#!/usr/bin/env python3
"""比较新旧背包基础色贴图的亮度（specs/010-backpack-texture-budget，SC-004）。

不是比整张图的平均亮度（贴图里有大片没被模型用到的空白区域），而是按网格 .obj 里每个三角面
实际使用的 UV 位置取样，按三角面的 3D 面积加权，得到"玩家真正看到的平均亮度"。

    python3 design/demo/tools/check_brightness.py

对比对象：
  旧：design/demo/obj_export/<tier>_backpack_diffuse.png          （010 之前的 1024 版，留档）
  新：src/main/resources/.../textures/item/<tier>_floating_backpack.png （当前随包的版本）

任一档偏差超过 ±10%（可用 LIMIT 环境变量改）时非零退出。
"""

import os
import sys
from pathlib import Path

from PIL import Image

DEMO = Path(__file__).resolve().parents[1]
REPO = DEMO.parents[1]
MODELS = REPO / "src/main/resources/assets/mcfloatingbackpack/models/item"
TEXTURES = REPO / "src/main/resources/assets/mcfloatingbackpack/textures/item"
OLD = DEMO / "obj_export"
TIERS = ["leather", "iron", "gold", "diamond", "netherite"]
LIMIT = float(os.environ.get("LIMIT", "10"))


def load_obj(path):
    verts, uvs, faces = [], [], []
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            verts.append(tuple(map(float, p[1:4])))
        elif p[0] == "vt":
            uvs.append((float(p[1]), float(p[2])))
        elif p[0] == "f":
            faces.append([tuple(int(x) if x else 0 for x in t.split("/")) for t in p[1:4]])
    return verts, uvs, faces


def triangle_area(a, b, c):
    u = [b[i] - a[i] for i in range(3)]
    v = [c[i] - a[i] for i in range(3)]
    n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
    return (sum(x * x for x in n) ** 0.5) / 2


def samples(obj_path):
    verts, uvs, faces = load_obj(obj_path)
    out = []
    for f in faces:
        a, b, c = [verts[t[0] - 1] for t in f]
        u = sum(uvs[t[1] - 1][0] for t in f) / 3
        v = 1 - sum(uvs[t[1] - 1][1] for t in f) / 3  # 与模型 JSON 的 flip_v 一致
        out.append((u, v, triangle_area(a, b, c)))
    return out


def weighted_luminance(img, pts):
    px = img.load()
    w, h = img.size
    acc = tot = 0.0
    for u, v, area in pts:
        r, g, b = px[min(w - 1, max(0, int(u * w))), min(h - 1, max(0, int(v * h)))]
        acc += (0.299 * r + 0.587 * g + 0.114 * b) * area
        tot += area
    return acc / tot


def main():
    worst = 0.0
    print(f"{'档位':10s} {'旧(1024)':>9s} {'新':>8s} {'偏差':>8s}")
    for tier in TIERS:
        pts = samples(MODELS / f"{tier}_backpack.obj")
        old = weighted_luminance(Image.open(OLD / f"{tier}_backpack_diffuse.png").convert("RGB"), pts)
        new = weighted_luminance(Image.open(TEXTURES / f"{tier}_floating_backpack.png").convert("RGB"), pts)
        dev = 100 * (new - old) / old
        worst = max(worst, abs(dev))
        print(f"{tier:10s} {old:9.1f} {new:8.1f} {dev:+7.1f}%")
    print(f"最大偏差 {worst:.1f}%，限制 ±{LIMIT:.0f}%")
    if worst > LIMIT:
        print("结果：超限")
        sys.exit(1)
    print("结果：通过")


if __name__ == "__main__":
    main()
