#!/usr/bin/env python3
"""压平下界合金背面的浅浮雕（specs/015-netherite-back-distinct，方案 B）。

背面（z 最小、贴着躯干的一面）的翻盖、口袋、扣具是高出背板约 0.04 个方块（不到 1 个像素）的浮雕。方案 A 已把
贴图和法线改成平整背板，这里再把几何也压平，让背面从任何角度、任何光照下都是一块平板。

规则（只向外，不削最外平面，包围盒不变），对 OBJ 里每一个 `v`：
  1. 0.2 < x < 0.8 且 zmin < z < 0.29：z 抬到 zmin（背面最外平面 0.238，`BackpackBackLayer` 的贴身位置按它算）；
     其中原来在 z ≥ 0.245 的那一层（原背板平面、口袋）抬到 zmin + 0.003 而不是 zmin：原网格是多层外壳叠起来的，
     如果两层压到同一个 z 会 z-fighting，隔开 0.003（远小于 1 个像素）就不会闪。
  2. 0.28 < x < 0.72、0.06 < y < 0.88、0.29 ≤ z < 0.335：抬到 zmin + 0.003。这一条压掉背板里的凹槽（例如翻盖下沿
     y≈0.42 处深约 0.06 的一条横槽）；背板上下边缘的圆角（y > 0.88、y < 0.06）和左右边缘的斜面不动。

试过但放弃的做法：规则 2 的 x 范围用 0.235–0.765（贴着背板左右边缘）时，几个细长的壁三角形被"折"过去、朝向反转，
手持视角（`firstperson_holes.py`）的洞从 [0,0,1,0,0,0,0] 变成 [2,1,3,2,2,1,0]；把这些三角形交换绕序反而更糟
（约 100 个像素）。把规则 2 的 x 范围收窄到 0.28–0.72（离开边缘斜面）后，洞回到压平前的 [0,0,1,0,0,0,0]。

按位置而不是按"属于哪个三角形"来选顶点：这个 OBJ 有 20756 个 v，只有 9698 个不同位置（UV/法线接缝处的顶点是重复的），
重复顶点必须一起动，否则会拉出裂缝。

    python3 design/demo/tools/flatten_netherite_back.py            # 原地修改随包的 netherite_backpack.obj；已处理过则跳过
    python3 design/demo/tools/flatten_netherite_back.py --check    # 只报告会动哪些顶点

不改面数、不改 UV、不改法线。设计目录里的 obj_export/netherite_backpack.obj（Git LFS）没有同步修改。
"""

import collections
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
OBJ = REPO / "src/main/resources/assets/mcfloatingbackpack/models/item/netherite_backpack.obj"
MARK = "# flatten_netherite_back"

X_RANGE = (0.2, 0.8)
Z_LIMIT = 0.29          # 规则 1 的上限
INNER = ((0.28, 0.72), (0.06, 0.88), 0.335)    # 规则 2：(x 范围, y 范围, z 上限)
LAYER_SPLIT = 0.245     # 低于它的是最外层浮雕，高于它的是原背板平面与口袋
SEPARATION = 0.003


def main(check):
    lines = open(OBJ).read().split("\n")
    if lines and lines[0].startswith(MARK):
        print("已处理过，跳过")
        return 0
    zmin = min(float(l.split()[3]) for l in lines if l.startswith("v "))
    moved = collections.Counter()
    positions = set()
    out = []
    for l in lines:
        if l.startswith("v "):
            x, y, z = map(float, l.split()[1:4])
            nz, why = z, None
            if X_RANGE[0] < x < X_RANGE[1] and zmin < z < Z_LIMIT:
                nz = zmin if z < LAYER_SPLIT else zmin + SEPARATION
                why = "规则1 到最外平面" if z < LAYER_SPLIT else "规则1 到最外平面 + 0.003"
            elif INNER[0][0] < x < INNER[0][1] and INNER[1][0] < y < INNER[1][1] and Z_LIMIT <= z < INNER[2]:
                nz, why = zmin + SEPARATION, "规则2 凹槽压平"
            if why and nz != z:
                moved[why] += 1
                positions.add((round(x, 5), round(y, 5), round(z, 5)))
                l = f"v {x:.6f} {y:.6f} {nz:.6f}"
        out.append(l)
    print(f"zmin = {zmin:.5f}；要动的顶点 {sum(moved.values())} 个（不同位置 {len(positions)} 个）：{dict(moved)}")
    if check:
        return 0
    out.insert(0, f"{MARK} rule1 z<{Z_LIMIT} x {X_RANGE}; rule2 {INNER}; -> zmin / zmin+{SEPARATION}")
    open(OBJ, "w").write("\n".join(out))
    print(f"已写入 {OBJ.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main("--check" in sys.argv))
