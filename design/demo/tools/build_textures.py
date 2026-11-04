#!/usr/bin/env python3
"""把 2048 原图处理成游戏用的背包贴图（基础色 + LabPBR 法线）。

先跑 extract_textures.py（Blender 无头）得到 design/demo/work/orig2048/，再：

    python3 design/demo/tools/build_textures.py            # 默认 512
    python3 design/demo/tools/build_textures.py 640        # 512 不够清晰时的退路
    python3 design/demo/tools/build_textures.py 512 --install   # 同时覆盖 src/main/resources

输出：
  design/demo/work/out/<tier>_floating_backpack.png / _n.png     游戏用的最终文件
  design/demo/obj_export/<tier>_backpack_diffuse_<size>.png       设计目录里的留档
  design/demo/obj_export/<tier>_backpack_normal_n_<size>.png

处理方式与依据见 specs/010-backpack-texture-budget/research.md：
  基础色：BOX 缩放 → UnsharpMask(1.0, 90, 2) → 按档位 gamma。BOX+轻锐化比 Lanczos 保留更多
          边缘对比度（实测），gamma 沿用 009 的值，所以亮度不会变（实测偏差 ≤ ±3%）。
  法线  ：BOX 缩放 → 重新归一化向量（平均会让向量变短）→ LabPBR 转换：
          R 保持，G 翻转（glTF 的 OpenGL Y 向上 → LabPBR 的 DirectX Y 向下），B=255（AO，
          源数据为空）、A=255（高度，无数据）。
"""

import math
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageFilter

DEMO = Path(__file__).resolve().parents[1]
REPO = DEMO.parents[1]
ORIG = DEMO / "work" / "orig2048"
OUT = DEMO / "work" / "out"
OBJ_EXPORT = DEMO / "obj_export"
RES_TEXTURES = REPO / "src/main/resources/assets/mcfloatingbackpack/textures/item"

# 009 的 gamma：皮革 0.62、铁 0.89、下界合金 0.91、金与钻石不调
GAMMA = {"leather": 0.62, "iron": 0.89, "gold": 1.0, "diamond": 1.0, "netherite": 0.91}


def build_base(tier, size):
    img = Image.open(ORIG / f"{tier}_base.png").convert("RGB")
    img = img.resize((size, size), Image.BOX)
    img = img.filter(ImageFilter.UnsharpMask(radius=1.0, percent=90, threshold=2))
    gamma = GAMMA[tier]
    if gamma != 1.0:
        lut = [round(255 * ((i / 255) ** gamma)) for i in range(256)]
        img = img.point(lut * 3)
    return img


def build_normal(tier, size):
    img = Image.open(ORIG / f"{tier}_normal.png").convert("RGB").resize((size, size), Image.BOX)
    src = img.load()
    out = Image.new("RGBA", (size, size))
    dst = out.load()
    for y in range(size):
        for x in range(size):
            r, g, b = src[x, y]
            nx, ny, nz = r / 127.5 - 1.0, g / 127.5 - 1.0, b / 127.5 - 1.0
            length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
            nx, ny = nx / length, ny / length
            r2 = round((nx + 1.0) * 127.5)
            g2 = round((ny + 1.0) * 127.5)
            dst[x, y] = (r2, 255 - g2, 255, 255)
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    size = int(args[0]) if args else 512
    install = "--install" in sys.argv
    if not ORIG.exists():
        sys.exit(f"找不到 {ORIG}，先用 Blender 跑 extract_textures.py")
    OUT.mkdir(parents=True, exist_ok=True)
    for tier in GAMMA:
        base = build_base(tier, size)
        normal = build_normal(tier, size)
        base.save(OUT / f"{tier}_floating_backpack.png")
        normal.save(OUT / f"{tier}_floating_backpack_n.png")
        base.save(OBJ_EXPORT / f"{tier}_backpack_diffuse_{size}.png")
        normal.save(OBJ_EXPORT / f"{tier}_backpack_normal_n_{size}.png")
        if install:
            shutil.copyfile(OUT / f"{tier}_floating_backpack.png", RES_TEXTURES / f"{tier}_floating_backpack.png")
            shutil.copyfile(OUT / f"{tier}_floating_backpack_n.png", RES_TEXTURES / f"{tier}_floating_backpack_n.png")
        print(f"{tier}: {size}x{size} gamma={GAMMA[tier]}{' (installed)' if install else ''}")


if __name__ == "__main__":
    main()
