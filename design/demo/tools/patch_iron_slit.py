#!/usr/bin/env python3
"""补上铁背包网格里一条能透光的竖缝（specs/010-backpack-texture-budget，用户反馈）。

现象：第一人称手持铁背包时，背包上有一条竖线能看到背后的水面。
原因：AI 生成的铁背包网格在 x≈0.24–0.28、y≈0.46–0.69、z≈0.31–0.42 处，两块曲面之间留了一条
0.02～0.04 宽、约 0.2 高的缝。Minecraft 会剔除背面，从模型 −z 侧（正面）看过去缝里什么都没有。
（用 /tmp 里的栅格化脚本从 24 个方向 × 5 个俯仰角检查过：只有正面一侧有这条竖缝。）

为什么不按开放边界做三角化：这个网格有 544 条被 3 个以上面共用的边（非流形），开放边界在缝上
断成好几段，拓扑上不成一个闭环，所以直接按几何补——在缝里（两块曲面之间、比前面那层更靠里）
放一片薄板，正反两面都放。薄板所有顶点共用同一个 UV 点（取离薄板中心最近的网格顶点的 UV），
所以是纯色，不会因为跨 UV 岛而出现彩色拉伸。

    python3 design/demo/tools/patch_iron_slit.py <iron_backpack.obj>     # 原地修改；已打过补丁则跳过

只处理这一条缝；其余开放边（见 design/demo/README.md 的"已知限制"）不动。
"""

import sys

MARK = "# patch_iron_slit"
X0, X1 = 0.226, 0.340   # 比缝宽不少：斜着看（实测到偏转 45°）时射线从缝侧面穿过 z=0.345，x 会偏到 0.30 左右
Y0, Y1 = 0.462, 0.692
Z = 0.345               # 前面那层在 z≈0.309，后面那层在 0.34–0.42，板放在两者之间，从正面看被前层挡住、只在缝里露出


def main(path):
    lines = open(path).read().split("\n")
    if any(l.startswith(MARK) for l in lines):
        print("已打过补丁，跳过")
        return
    V, VT_of = [], {}
    nv = nvt = nvn = 0
    last = {"v": -1, "vn": -1}
    for i, l in enumerate(lines):
        t = l.split()
        if not t:
            continue
        if t[0] == "v":
            V.append(tuple(map(float, t[1:4]))); last["v"] = i
        elif t[0] == "vn":
            nvn += 1; last["vn"] = i
        elif t[0] == "f":
            for c in t[1:4]:
                p = c.split("/")
                VT_of.setdefault(int(p[0]) - 1, int(p[1]))
    # 离薄板中心最近、且有 UV 的顶点
    cx, cy = (X0 + X1) / 2, (Y0 + Y1) / 2
    near = min(VT_of, key=lambda i: (V[i][0] - cx) ** 2 + (V[i][1] - cy) ** 2 + (V[i][2] - Z) ** 2)
    uv = VT_of[near]

    # 新增 4 个顶点（法线用 −z / +z 两个）
    base = len(V)
    corners = [(X0, Y0), (X1, Y0), (X1, Y1), (X0, Y1)]
    new_v = [f"v {x:.6f} {y:.6f} {Z:.6f}" for x, y in corners]
    nfront, nback = nvn + 1, nvn + 2
    new_vn = ["vn 0.000000 0.000000 -1.000000", "vn 0.000000 0.000000 1.000000"]
    a, b, c, d = base + 1, base + 2, base + 3, base + 4
    # 从 −z 看过去，(x0,y0)->(x0,y1)->(x1,y1) 是逆时针（x 向左、y 向上的镜像后），这里按法线 −z 校验：
    # 顶点 a(X0,Y0) b(X1,Y0) c(X1,Y1)：叉积 z>0 → 法线 +z，所以朝 −z 的面要取 a,c,b
    front = [(a, c, b), (a, d, c)]
    back = [(a, b, c), (a, c, d)]
    faces = [MARK + " 2 triangles x2 windings"]
    for tris, vn in ((front, nfront), (back, nback)):
        for tri in tris:
            faces.append("f " + " ".join(f"{i}/{uv}/{vn}" for i in tri))

    out = lines[:]
    last_f = max(i for i, l in enumerate(out) if l.startswith("f "))
    out[last_f + 1:last_f + 1] = faces
    out[last["vn"] + 1:last["vn"] + 1] = new_vn      # vn 在 v 之后：先插后面的段，行号不会错位
    out[last["v"] + 1:last["v"] + 1] = new_v
    open(path, "w").write("\n".join(out))
    print(f"薄板 x[{X0},{X1}] y[{Y0},{Y1}] z={Z}，UV 取自顶点 {near + 1}，已写入 {path}")


if __name__ == "__main__":
    main(sys.argv[1])
