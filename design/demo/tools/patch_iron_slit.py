#!/usr/bin/env python3
"""补上铁背包网格里几处能透光的缝（specs/010-backpack-texture-budget，用户反馈两轮）。

现象：第一人称手持铁背包时，背包上有缝能看到背后的场景（第一轮是一条竖线，第二轮是下方一点点草地）。
原因：AI 生成的铁背包网格有若干处两块曲面之间没有闭合。Minecraft 会剔除背面，从某些方向看过去缝里什么都没有。
手持时（firstperson 的 rotation Y=90）相机看到的是模型 −x 一侧，所以侧面小口袋上的缝最显眼。

为什么不按开放边界做三角化：这个网格有 544 条被 3 个以上面共用的边（非流形），开放边界在缝上
断成好几段，拓扑上不成一个闭环，所以直接按几何补——在缝里（两块曲面之间、比前面那层更靠里）
放一片薄板，正反两面都放。薄板所有顶点共用同一个 UV 点（取离薄板中心最近的网格顶点的 UV），
所以是纯色，不会因为跨 UV 岛而出现彩色拉伸。

    python3 design/demo/tools/patch_iron_slit.py <iron_backpack.obj>     # 原地修改；已打过补丁则跳过

只处理 PLATES 里列出的几处缝；其余开放边和零星的 1~2 像素小孔（见 design/demo/README.md 第十轮）不动。
每次加板后用栅格化复查：整球面 120 个方向的洞数、外轮廓像素变化、板是否凸出蒙皮。
"""

import sys

MARK = "# patch_iron_slit"

# 每块板：(轴, 位置, 范围1, 范围2)。轴 x → 范围是 (y, z)；y → (x, z)；z → (x, y)。
# 板放在缝里、比外层蒙皮更靠里，所以从外面看被蒙皮挡住，只在缝里露出。
PLATES = [
    # 第一轮：模型 −z 侧、x≈0.24–0.28 的竖缝。X 范围故意比缝宽，斜着看（实测到偏转 45°）射线从缝侧面穿过 z=0.345 时 x 会偏到 0.30 左右
    ("z", 0.345, (0.226, 0.340), (0.462, 0.692)),
    # 第二轮：手持时相机看到的 ±x 两侧小口袋上的缝（x≈0.035/0.965，y 0.37–0.43，z 0.488–0.572）。
    # 板深 0.06：再深（0.10 以上）盖不住斜视角，再浅（0.05 以下）会在斜视角下凸出蒙皮
    ("x", 0.06, (0.36, 0.44), (0.48, 0.58)),
    ("x", 0.94, (0.36, 0.44), (0.48, 0.58)),
    # 第二轮：z 方向看过去 y≈0.54–0.555 的两条水平细缝（x 0.06–0.19 和 0.74–0.94）
    ("z", 0.45, (0.05, 0.20), (0.536, 0.558)),
    ("z", 0.45, (0.73, 0.945), (0.536, 0.558)),
]


# 第三轮（手持视角实测）：五块"贴着视线方向"的斜板，补的是第一块 z=0.345 板所在的同一条竖缝（x 0.24–0.35, y 0.44–0.62, z 0.30–0.43）
# 在手持视角下的漏光：手持有 25° 的滚转 + 透视，斜着穿过缝的光线会从 z=0.345 那块板的下缘漏过去。
# 由 design/demo/tools/firstperson_holes.py --plates 生成：以手持第一人称相机（含 ±6°/±10° 的视角摆动）为准，
# 在每个洞后面（比洞边缘的蒙皮更靠里 0.004）放一块恰好盖住它的斜板，四个角是模型坐标。
HAND_VIEW_PLATES = [[[0.3298, 0.6052, 0.3054], [0.3298, 0.6052, 0.4117], [0.2534, 0.4412, 0.4117], [0.2534, 0.4412, 0.3054]], [[0.3318, 0.608, 0.3041], [0.3223, 0.6125, 0.4044], [0.2453, 0.4474, 0.4044], [0.2549, 0.443, 0.3041]], [[0.3403, 0.5903, 0.3059], [0.3403, 0.5903, 0.4264], [0.2521, 0.4437, 0.4264], [0.2521, 0.4437, 0.3059]], [[0.318, 0.6155, 0.307], [0.318, 0.6155, 0.397], [0.2566, 0.4374, 0.397], [0.2566, 0.4374, 0.307]], [[0.3463, 0.5914, 0.3026], [0.3298, 0.6021, 0.4142], [0.235, 0.4561, 0.4142], [0.2515, 0.4454, 0.3026]]]

PLATES += [("q", c) for c in HAND_VIEW_PLATES]


def quad(axis, pos, r1, r2):
    if axis == "q":
        return [tuple(c) for c in pos]
    (a0, a1), (b0, b1) = r1, r2
    if axis == "x":
        return [(pos, a0, b0), (pos, a1, b0), (pos, a1, b1), (pos, a0, b1)]
    if axis == "y":
        return [(a0, pos, b0), (a1, pos, b0), (a1, pos, b1), (a0, pos, b1)]
    return [(a0, b0, pos), (a1, b0, pos), (a1, b1, pos), (a0, b1, pos)]


def unit_normal(q):
    """(q0,q1,q2) 绕序对应的单位法线。"""
    u = [q[1][k] - q[0][k] for k in range(3)]
    v = [q[2][k] - q[0][k] for k in range(3)]
    n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
    length = sum(c * c for c in n) ** 0.5
    return tuple(c / length for c in n)


def main(path):
    lines = open(path).read().split("\n")
    if any(l.startswith(MARK) for l in lines):
        print("已打过补丁，跳过")
        return
    V, VT_of = [], {}
    nvn = 0
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

    new_v, new_vn, faces = [], [], [MARK + f" {len(PLATES)} plates x2 windings"]
    for entry in PLATES:
        axis, pos, r1, r2 = (entry[0], entry[1], None, None) if entry[0] == "q" else entry
        q = quad(axis, pos, r1, r2)
        cx, cy, cz = (sum(p[k] for p in q) / 4 for k in range(3))
        near = min(VT_of, key=lambda i: (V[i][0] - cx) ** 2 + (V[i][1] - cy) ** 2 + (V[i][2] - cz) ** 2)
        uv = VT_of[near]
        base = len(V) + len(new_v)
        new_v += [f"v {x:.6f} {y:.6f} {z:.6f}" for x, y, z in q]
        a, b, c, d = base + 1, base + 2, base + 3, base + 4
        tris = [(a, b, c), (a, c, d)]
        n = unit_normal(q)                       # (a,b,c) 绕序的法线
        for group, sign in ((tris, 1), ([t[::-1] for t in tris], -1)):
            new_vn.append("vn {:.6f} {:.6f} {:.6f}".format(*(sign * c for c in n)))
            idx = nvn + len(new_vn)
            for tri in group:
                faces.append("f " + " ".join(f"{i}/{uv}/{idx}" for i in tri))

    out = lines[:]
    last_f = max(i for i, l in enumerate(out) if l.startswith("f "))
    out[last_f + 1:last_f + 1] = faces
    out[last["vn"] + 1:last["vn"] + 1] = new_vn      # vn 在 v 之后：先插后面的段，行号不会错位
    out[last["v"] + 1:last["v"] + 1] = new_v
    open(path, "w").write("\n".join(out))
    print(f"补了 {len(PLATES)} 块板（正反各一层），已写入 {path}")


if __name__ == "__main__":
    main(sys.argv[1])
