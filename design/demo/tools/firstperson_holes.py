#!/usr/bin/env python3
"""按游戏里第一人称手持的真实变换，找背包网格上"能透光"的洞（specs/010-backpack-texture-budget）。

为什么要有它：之前用"从各个方向正对着看"的栅格化找缝，补完以后用户在游戏里还是漏光——因为手持
有 25° 滚转和透视，斜着穿过缝的光线和正对着看不是一回事。这里按 ItemInHandRenderer 的链条重建画面：

    相机在原点朝 −z，70° 垂直 FOV；右手：translate(0.56, −0.52, −0.72)
    → 显示变换 firstperson_righthand：translate(平移×0.0625) · rotationXYZ(rot) · scale
    → 模型坐标 −0.5

只画正面朝向的三角形（和游戏的背面剔除一致），统计"图形内部没被盖住"的像素。为了覆盖手握时的轻微晃动，
同时检查 ±6°/±10° 的几组视角摆动。已用修补前的铁背包对过：重建画面里的斜缝与截图里右下角那条一致。

    python3 design/demo/tools/firstperson_holes.py <model.obj>             # 每个视角的洞像素数；全 0 即通过
    python3 design/demo/tools/firstperson_holes.py <model.obj> --plates    # 同时按洞生成补板的四个角（JSON）

显示变换默认取铁背包 JSON 里的值（rotation [0,90,25]、translation [1.5,3.5,0]、scale 0.7）；
如果改了 models/item/*.json 的 firstperson_righthand，要同步改下面的 ROT/TRANS/SCALE。
纯 Python，无第三方依赖；大约一分钟。
"""

import collections
import json
import math
import sys

ROT, TRANS, SCALE = (0, 90, 25), (1.5, 3.5, 0), 0.7
OFF = (0.56, -0.52, -0.72)
W, H, SC = 864, 860, 257.0                 # 画布向下加高，让整个背包落在画面内（真实屏幕在 y=514 处截断）
F = 1 / math.tan(math.radians(70) / 2)


def rot(axis, a):
    c, s = math.cos(a), math.sin(a)
    return {"x": [[1, 0, 0], [0, c, -s], [0, s, c]], "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]], "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[axis]


def mm(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(A, v):
    return tuple(sum(A[i][k] * v[k] for k in range(3)) for i in range(3))


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


R = mm(mm(rot("x", math.radians(ROT[0])), rot("y", math.radians(ROT[1]))), rot("z", math.radians(ROT[2])))
RT = [[R[j][i] for j in range(3)] for i in range(3)]
T = tuple(c * 0.0625 for c in TRANS)
PERT = [None] + [mm(rot("y", math.radians(a)), rot("x", math.radians(b))) for a, b in ((6, 0), (-6, 0), (0, 6), (0, -6), (10, 8), (-10, -8))]


def to_view(v):
    w = mv(R, tuple((v[i] - .5) * SCALE for i in range(3)))
    return tuple(w[i] + T[i] + OFF[i] for i in range(3))


def from_view(p):
    w = mv(RT, tuple(p[i] - OFF[i] - T[i] for i in range(3)))
    return tuple(w[i] / SCALE + .5 for i in range(3))


def load(path):
    v, tris = [], []
    for line in open(path):
        t = line.split()
        if t and t[0] == "v":
            v.append(tuple(map(float, t[1:4])))
        elif t and t[0] == "f":
            i = [int(x.split("/")[0]) - 1 for x in t[1:4]]
            tris.append((v[i[0]], v[i[1]], v[i[2]]))
    return tris


def render(tris, e):
    depth = [1e9] * (W * H)
    cov = bytearray(W * H)
    for tri in tris:
        p = [to_view(q) for q in tri]
        if e:
            p = [mv(e, q) for q in p]
        if dot(cross(sub(p[1], p[0]), sub(p[2], p[0])), p[0]) >= 0 or any(q[2] >= -0.01 for q in p):
            continue
        (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = [(W / 2 + q[0] * F * SC / -q[2], SC - q[1] * F * SC / -q[2], -q[2]) for q in p]
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-12:
            continue
        for y in range(max(0, int(min(y0, y1, y2))), min(H - 1, int(max(y0, y1, y2)) + 1) + 1):
            for x in range(max(0, int(min(x0, x1, x2))), min(W - 1, int(max(x0, x1, x2)) + 1) + 1):
                a = ((y1 - y2) * (x + .5 - x2) + (x2 - x1) * (y + .5 - y2)) / den
                b = ((y2 - y0) * (x + .5 - x2) + (x0 - x2) * (y + .5 - y2)) / den
                c = 1 - a - b
                if a >= -0.02 and b >= -0.02 and c >= -0.02:
                    cov[y * W + x] = 1
                    depth[y * W + x] = min(depth[y * W + x], a * z0 + b * z1 + c * z2)
    outside = bytearray(W * H)
    dq = collections.deque()
    for x in range(W):
        for y in (0, H - 1):
            if not cov[y * W + x] and not outside[y * W + x]:
                outside[y * W + x] = 1; dq.append((x, y))
    for y in range(H):
        for x in (0, W - 1):
            if not cov[y * W + x] and not outside[y * W + x]:
                outside[y * W + x] = 1; dq.append((x, y))
    while dq:
        x, y = dq.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < W and 0 <= ny < H and not cov[ny * W + nx] and not outside[ny * W + nx]:
                outside[ny * W + nx] = 1; dq.append((nx, ny))
    return depth, {(i % W, i // W) for i in range(W * H) if not cov[i] and not outside[i]}


def components(pixels):
    rest, out = set(pixels), []
    while rest:
        stack = [rest.pop()]
        comp = list(stack)
        while stack:
            x, y = stack.pop()
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    q = (x + dx, y + dy)
                    if q in rest:
                        rest.remove(q); stack.append(q); comp.append(q)
        out.append(comp)
    return out


def plates(depth, holes, e, margin=3):
    """每个洞一块板：放在洞边缘蒙皮最深处再往里 0.004，所以从这个视角只会在洞里露出。"""
    out = []
    einv = [[e[j][i] for j in range(3)] for i in range(3)] if e else None
    for comp in components(holes):
        cs = set(comp)
        bd = [depth[q[1] * W + q[0]] for (x, y) in comp for q in ((x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
              if q not in cs and 0 <= q[0] < W and 0 <= q[1] < H and depth[q[1] * W + q[0]] < 1e8]
        if not bd:
            continue
        tp = max(bd) + 0.004
        xs, ys = [p[0] for p in comp], [p[1] for p in comp]
        corners = []
        for px, py in ((min(xs) - margin, min(ys) - margin), (max(xs) + 1 + margin, min(ys) - margin), (max(xs) + 1 + margin, max(ys) + 1 + margin), (min(xs) - margin, max(ys) + 1 + margin)):
            p = ((px - W / 2) * tp / (F * SC), (SC - py) * tp / (F * SC), -tp)
            corners.append([round(c, 4) for c in from_view(mv(einv, p) if einv else p)])
        out.append(corners)
    return out


def main():
    path = sys.argv[1]
    want_plates = "--plates" in sys.argv
    tris = load(path)
    counts, found = [], []
    for e in PERT:
        depth, holes = render(tris, e)
        counts.append(len(holes))
        if want_plates:
            found += plates(depth, holes, e)
    print("每个视角的洞像素数（第一个是标准视角，后面是 6 组晃动）：", counts)
    print("结果：" + ("通过" if not any(counts) else "有洞"))
    if want_plates:
        print(json.dumps(found))
    sys.exit(1 if any(counts) else 0)


if __name__ == "__main__":
    main()
