#!/usr/bin/env python3
"""把下界合金背包的背面重画成平整背板（specs/015-netherite-back-distinct）。

问题：下界合金的网格是 AI 生成的，前后两面几乎镜像对称（浮雕只有约 0.04 个方块），正反面都有翻盖、
金色扣具、口袋，分不出前后。背面（z 最小、贴着躯干的那一面，z≈0.238）的辨识度几乎全靠贴图，所以只重画
贴图：基础色 + LabPBR 法线（`_n`）。

做法（依据见 specs/015-netherite-back-distinct/research.md）：
  1. 背面三角形 = 三个顶点都在 z < zmin + 0.09、且中心 0.2 < x < 0.8 的三角形（两侧口袋、顶/底、正面不动）。
  2. 把背面与其余三角形分别栅格化到 UV 掩码，只改"背面独占"的纹素（共享的不动），再向外膨胀 1 格
     （只膨胀到不被其它面使用的纹素），避免 mip/线性过滤在岛边缘混入旧的金色。
  3. 对背面独占纹素逐个做重心插值，取得 3D (x, y)，按位置套程序化图案：深色背板、深色包边、两条肩带垫（带缝线）、
     一条横向接缝、四个小金铆钉。底色取自掩码之外的非金色深灰纹素，保持颗粒风格。
  4. `_n` 里同样的纹素写成平面法线（取现有平整区域的众数），光影下不再有翻盖/扣具的凸起。

    python3 design/demo/tools/netherite_back_panel.py --check   # 只报告掩码统计与自检，不写文件
    python3 design/demo/tools/netherite_back_panel.py           # 自检通过后原地改写随包的两张贴图

幂等：底色与随机数只来自掩码之外的纹素和固定种子，重复运行结果相同。自检失败时不写文件，退出码 1。

注意：`build_textures.py 512 --install` 会用 2048 原图覆盖五档贴图，下界合金之后需要重跑本脚本。
只依赖 Pillow 与标准库；大约几十秒。
"""

import collections
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parents[3]
ITEM = REPO / "src/main/resources/assets/mcfloatingbackpack"
OBJ = ITEM / "models/item/netherite_backpack.obj"
BASE = ITEM / "textures/item/netherite_floating_backpack.png"
NORMAL = ITEM / "textures/item/netherite_floating_backpack_n.png"
S = 512

BACK_LAYER = 0.09          # 背面浅浮雕层的厚度（zmin 起算）
BODY_X = (0.2, 0.8)        # 中央背板的 x 范围；之外是两侧口袋
GOLD_MAX_SHARE = 0.03      # 背板内金色像素占比上限
SEED = 20261105


def load_obj():
    verts, uvs, tris = [], [], []
    for line in open(OBJ):
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            verts.append(tuple(map(float, p[1:4])))
        elif p[0] == "vt":
            uvs.append((float(p[1]), float(p[2])))
        elif p[0] == "f":
            tris.append([(int(t.split("/")[0]) - 1, int(t.split("/")[1]) - 1) for t in p[1:4]])
    return verts, uvs, tris


def select_back(verts, tris):
    zmin = min(v[2] for v in verts)
    back = []
    for t in tris:
        vs = [verts[i] for i, _ in t]
        cx = sum(v[0] for v in vs) / 3
        if max(v[2] for v in vs) < zmin + BACK_LAYER and BODY_X[0] < cx < BODY_X[1]:
            back.append(True)
        else:
            back.append(False)
    return back


def uv_polygon(uvs, t):
    return [(uvs[j][0] * S, (1 - uvs[j][1]) * S) for _, j in t]


def masks(uvs, tris, is_back):
    back = Image.new("L", (S, S), 0)
    other = Image.new("L", (S, S), 0)
    db, do = ImageDraw.Draw(back), ImageDraw.Draw(other)
    for t, b in zip(tris, is_back):
        d = db if b else do
        poly = uv_polygon(uvs, t)
        d.polygon(poly, fill=255)
        # 退化的三角形（UV 缩成一个点或一条线，例如扣具孔里那块纯色小方块）填不出纹素，
        # 补上顶点所在的纹素，否则它们会保留旧颜色
        for px, py in poly:
            d.point((min(S - 1, int(px)), min(S - 1, int(py))), fill=255)
    return back, other


def only_back(back, other):
    bp, op = back.load(), other.load()
    only = Image.new("L", (S, S), 0)
    sp = only.load()
    shared = 0
    for y in range(S):
        for x in range(S):
            if bp[x, y]:
                if op[x, y]:
                    shared += 1
                else:
                    sp[x, y] = 255
    return only, shared


def dilate(only, other):
    sp, op = only.load(), other.load()
    out = only.copy()
    dp = out.load()
    for y in range(S):
        for x in range(S):
            if sp[x, y] or op[x, y]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and sp[nx, ny]:
                    dp[x, y] = 255
                    break
    return out


def texel_positions(verts, uvs, tris, is_back, paint):
    """每个要画的纹素 -> 背面平面上的 (x, y)；同一纹素被多个三角形覆盖时取第一个。"""
    pos = {}
    pp = paint.load()
    for t, b in zip(tris, is_back):
        if not b:
            continue
        P = uv_polygon(uvs, t)
        V = [verts[i] for i, _ in t]
        (ax, ay), (bx, by), (cx, cy) = P
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            for (px, py), v in zip(P, V):
                key = (min(S - 1, int(px)), min(S - 1, int(py)))
                if pp[key] and key not in pos:
                    pos[key] = (v[0], v[1])
            continue
        for y in range(max(0, int(min(ay, by, cy)) - 1), min(S, int(max(ay, by, cy)) + 2)):
            for x in range(max(0, int(min(ax, bx, cx)) - 1), min(S, int(max(ax, bx, cx)) + 2)):
                if not pp[x, y] or (x, y) in pos:
                    continue
                l1 = ((by - cy) * (x + .5 - cx) + (cx - bx) * (y + .5 - cy)) / den
                l2 = ((cy - ay) * (x + .5 - cx) + (ax - cx) * (y + .5 - cy)) / den
                l3 = 1 - l1 - l2
                # 略宽的容差：膨胀出来的纹素在三角形外 1 格，取最近三角形的外推位置
                if min(l1, l2, l3) < -0.35:
                    continue
                pos[(x, y)] = (
                    l1 * V[0][0] + l2 * V[1][0] + l3 * V[2][0],
                    l1 * V[0][1] + l2 * V[1][1] + l3 * V[2][1],
                )
    # 膨胀出来的一圈在三角形外：沿用最近的已有位置，让图案在岛边缘连续
    pending = [(x, y) for y in range(S) for x in range(S) if pp[x, y] and (x, y) not in pos]
    for _ in range(3):
        still = []
        for (x, y) in pending:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                q = pos.get((x + dx, y + dy))
                if q is not None:
                    pos[(x, y)] = q
                    break
            else:
                still.append((x, y))
        pending = still
        if not pending:
            break
    return pos


def donor_pool(base, other):
    """掩码之外的、非金色的深灰纹素：作为背板底色的颗粒来源。"""
    bp, op = base.load(), other.load()
    pool = []
    for y in range(S):
        for x in range(S):
            if not op[x, y]:
                continue
            r, g, b = bp[x, y][:3]
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            sat = max(r, g, b) - min(r, g, b)
            if 22 <= lum <= 70 and sat < 22:
                pool.append((r, g, b))
    return pool


# ---- 背面平面上的图案（x, y 为模型坐标，0..1）----

def pattern(x, y, rng, pool):
    """返回 (颜色, 是否金色)。"""
    base = rng.choice(pool)
    u = (x - BODY_X[0]) / (BODY_X[1] - BODY_X[0])   # 0..1 across the panel
    edge = min(u, 1 - u, y, 1 - y)
    r, g, b = base
    # 包边：靠近背板边缘更深
    if edge < 0.04:
        k = 0.62
        return (int(r * k), int(g * k), int(b * k)), False
    # 铆钉：肩带上下端各一个，小金块
    for cu in (0.30, 0.70):
        for cy in (0.28, 0.80):
            if abs(u - cu) < 0.020 and abs(y - cy) < 0.020:
                return (176, 132, 52), True
    # 肩带垫：两条竖带，略亮；中间有暗色缝线虚线
    for cu in (0.30, 0.70):
        d = abs(u - cu)
        if d < 0.065 and 0.25 < y < 0.85:
            if 0.050 < d < 0.065:
                return (int(r * 0.55), int(g * 0.55), int(b * 0.55)), False   # 带边
            if int(y * 90) % 3 == 0 and 0.040 < d < 0.050:
                return (int(r * 0.45), int(g * 0.45), int(b * 0.45)), False   # 缝线虚线
            k = 1.35
            return (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k))), False
    # 横向接缝（背板上部的衬垫缝）
    if abs(y - 0.62) < 0.008:
        return (int(r * 0.5), int(g * 0.5), int(b * 0.5)), False
    return base, False


def flat_normal(normal):
    """_n 里最常见的颜色（平整区域）。"""
    return collections.Counter(zip(*[iter(normal.tobytes())] * 4)).most_common(1)[0][0]


def main(check_only):
    verts, uvs, tris = load_obj()
    is_back = select_back(verts, tris)
    n_back = sum(is_back)
    back, other = masks(uvs, tris, is_back)
    only, shared = only_back(back, other)
    paint = dilate(only, other)
    n_only = sum(1 for p in only.tobytes() if p)
    n_back_tex = sum(1 for p in back.tobytes() if p)
    n_other = sum(1 for p in other.tobytes() if p)
    n_paint = sum(1 for p in paint.tobytes() if p)
    print(f"背面三角形 {n_back} / {len(tris)}；纹素：背面 {n_back_tex}，其余 {n_other}，共享 {shared}，"
          f"背面独占 {n_only}，膨胀后要画 {n_paint}")

    base = Image.open(BASE).convert("RGB")
    normal = Image.open(NORMAL).convert("RGBA")
    assert base.size == (S, S) and normal.size == (S, S), "贴图必须是 512x512"
    pool = donor_pool(base, other)
    flat = flat_normal(normal)
    print(f"底色颗粒来源 {len(pool)} 个纹素；平面法线值 {flat}")

    pos = texel_positions(verts, uvs, tris, is_back, paint)
    missing = n_paint - len(pos)
    print(f"得到 3D 位置的纹素 {len(pos)}，未覆盖 {missing}")

    rng = random.Random(SEED)
    new_base = base.copy()
    new_normal = normal.copy()
    nb, nn = new_base.load(), new_normal.load()
    gold = 0
    for (x, y), (px, py) in sorted(pos.items()):
        color, is_gold = pattern(px, py, rng, pool)
        nb[x, y] = color
        nn[x, y] = flat
        gold += is_gold
    gold_share = gold / max(1, len(pos))
    print(f"金色纹素占比 {gold_share * 100:.2f}%（上限 {GOLD_MAX_SHARE * 100:.0f}%）")

    # ---- 自检 ----
    errors = []
    painted = set(pos)
    for name, old, new in (("基础色", base, new_base), ("法线", normal, new_normal)):
        op, np_ = old.load(), new.load()
        for y in range(S):
            for x in range(S):
                if (x, y) not in painted and op[x, y] != np_[x, y]:
                    errors.append(f"{name} 在掩码之外被改动：({x},{y})")
                    break
            else:
                continue
            break
    op = other.load()
    for (x, y) in painted:
        if op[x, y]:
            errors.append(f"改动了被其它面使用的纹素 ({x},{y})")
            break
    if gold_share > GOLD_MAX_SHARE:
        errors.append("金色占比超限")
    if missing > n_paint * 0.02:
        errors.append(f"有 {missing} 个要画的纹素没有得到位置（超过 2%）")
    if (n_back, shared, n_only) not in ((4997, 714, 46566), (5001, 715, 46573), (5031, 768, 46690)):
        # 依次是：原始网格 / patch_netherite_back_hole.py 之后 / 再加 flatten_netherite_back.py 之后（压平把约 30 个三角面抬进了背面层）
        print("提示：统计数字与 research.md 记录的三组之一不同，说明模型已被改动过")
    if errors:
        print("自检失败：")
        for e in errors:
            print("  -", e)
        return 1
    print("自检通过")
    if check_only:
        print("--check：不写文件")
        return 0
    new_base.save(BASE)
    new_normal.save(NORMAL)
    print(f"已写入 {BASE.name} 与 {NORMAL.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main("--check" in sys.argv))
