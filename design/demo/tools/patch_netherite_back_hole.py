#!/usr/bin/env python3
"""堵上下界合金背面中央那个透光的方孔（specs/015-netherite-back-distinct）。

背景：下界合金网格正反两面各有一个金色扣具，扣具环中间是一个贯穿网格的方孔（从 −z 看过去能看到背景，
渲染测得孔口约 x 0.492–0.528、y 0.712–0.746）。015 把背面的扣具重画成平整背板之后，这个孔仍在，
变成背板正中一个"透光的小方窗"（手持/掉落物旋转到背面时会看到孔后面的世界）。所以在孔后面放一片薄板。

做法同 patch_iron_slit.py：板放在孔后（比背面浅浮雕层更靠里，z=0.30）、比孔口大一圈，从外面看被背板挡住，
只在孔里露出；正反两个绕序各一份，所以不受背面剔除影响。板的 UV 取自贴图里一块 8×8 的背板纹素
（背面独占、已被 netherite_back_panel.py 画成带颗粒的深灰背板），所以和周围背板看不出区别。

    python3 design/demo/tools/patch_netherite_back_hole.py            # 原地修改随包的 netherite_backpack.obj；已打过补丁则跳过
    python3 design/demo/tools/patch_netherite_back_hole.py --check    # 只报告

只补这一处；正面扣具的孔是原设计的一部分，不动。会增加 4 个三角面（一块板 × 两个绕序 × 2）。
设计目录里的 obj_export/netherite_backpack.obj（Git LFS）没有同步修改。
"""

import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
ASSETS = REPO / "src/main/resources/assets/mcfloatingbackpack"
OBJ = ASSETS / "models/item/netherite_backpack.obj"
BASE = ASSETS / "textures/item/netherite_floating_backpack.png"
MARK = "# patch_netherite_back_hole"

# (z, (x0, x1), (y0, y1))：孔口约 x 0.492–0.528、y 0.712–0.746，板每边大出约 0.02
PLATE = (0.30, (0.470, 0.550), (0.690, 0.770))


def main(check):
    lines = open(OBJ).read().split("\n")
    if any(l.startswith(MARK) for l in lines):
        print("已打过补丁，跳过")
        return 0
    V, VT_of, nvn = [], {}, 0
    last = {"v": -1, "vn": -1, "vt": -1}
    for i, l in enumerate(lines):
        t = l.split()
        if not t:
            continue
        if t[0] == "v":
            V.append(tuple(map(float, t[1:4])))
            last["v"] = i
        elif t[0] == "vn":
            nvn += 1
            last["vn"] = i
        elif t[0] == "vt":
            last["vt"] = i
        elif t[0] == "f":
            for c in t[1:4]:
                p = c.split("/")
                VT_of.setdefault(int(p[0]) - 1, int(p[1]))
    z, (x0, x1), (y0, y1) = PLATE
    quad = [(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)]
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    # UV：板不能是单色（会像一块色斑），所以给它一小块"普通深灰背板"的 UV 区域（8×8 纹素，已被
    # netherite_back_panel.py 画成带颗粒的背板）。在背面独占的纹素里找一块全是深灰、无金色的。
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import netherite_back_panel as panel
    from PIL import Image

    verts, uvs, tris = panel.load_obj()
    back, other = panel.masks(uvs, tris, panel.select_back(verts, tris))
    only, _ = panel.only_back(back, other)
    tex = Image.open(BASE).convert("RGB")
    op = only.load()

    def block_ok(bx, by):
        for yy in range(by, by + 8):
            for xx in range(bx, bx + 8):
                if not op[xx, yy]:
                    return False
                r, g, b = tex.getpixel((xx, yy))
                lum = 0.299 * r + 0.587 * g + 0.114 * b
                if not (20 <= lum <= 60) or max(r, g, b) - min(r, g, b) > 30:
                    return False
        return True

    block = next(((bx, by) for by in range(0, 504, 4) for bx in range(0, 504, 4) if block_ok(bx, by)), None)
    if block is None:
        print("找不到合适的背板纹素块")
        return 1
    bx, by = block
    u0, u1 = bx / 512, (bx + 8) / 512
    v0, v1 = 1 - (by + 8) / 512, 1 - by / 512          # OBJ 的 v 向上
    print(f"板 z={z} x {x0}–{x1} y {y0}–{y1}；UV 取自纹素块 ({bx},{by}) 8×8")
    if check:
        return 0
    nvt = sum(1 for l in lines if l.startswith("vt "))
    new_vt = [f"vt {u0:.6f} {v0:.6f}", f"vt {u1:.6f} {v0:.6f}", f"vt {u1:.6f} {v1:.6f}", f"vt {u0:.6f} {v1:.6f}"]
    uvi = [nvt + 1, nvt + 2, nvt + 3, nvt + 4]
    base = len(V)
    new_v = [f"v {x:.6f} {y:.6f} {zz:.6f}" for x, y, zz in quad]
    a, b, c, d = base + 1, base + 2, base + 3, base + 4
    tris = [(a, b, c), (a, c, d)]
    # 朝 −z 的法线对应 (a,b,c) 绕序：(b−a)×(c−a) 的 z 分量为正，所以 (a,b,c) 的法线朝 +z；反向绕序朝 −z
    faces = [MARK + " 1 plate x2 windings"]
    new_vn = []
    for group, nz in ((tris, 1.0), ([t[::-1] for t in tris], -1.0)):
        new_vn.append(f"vn 0.000000 0.000000 {nz:.6f}")
        idx = nvn + len(new_vn)
        for tri in group:
            faces.append("f " + " ".join(f"{i}/{uvi[i - base - 1]}/{idx}" for i in tri))
    out = lines[:]
    last_f = max(i for i, l in enumerate(out) if l.startswith("f "))
    # 各段在文件里的先后顺序不固定（这个文件是 v、vn、vt、f），所以按位置从后往前插，行号不会错位
    inserts = [(last_f + 1, faces), (last["vn"] + 1, new_vn), (last["vt"] + 1, new_vt), (last["v"] + 1, new_v)]
    for pos, block in sorted(inserts, key=lambda e: -e[0]):
        out[pos:pos] = block
    open(OBJ, "w").write("\n".join(out))
    print(f"已写入 {OBJ.name}：+4 顶点、+4 uv、+2 法线、+4 三角面")
    return 0


if __name__ == "__main__":
    sys.exit(main("--check" in sys.argv))
