#!/usr/bin/env python3
"""048/049: 占位贴图——为 19 种液体罐各生成一张 16x16 的物品贴图（四档材质 x 五种液体，没有皮岩浆罐），再为 5 根管子（水、油、岩浆、牛奶、蜂蜜）各生成一张。

这只是占位图：画一个带盖的罐子，罐壁与盖用"档次色"，罐里的液体用"液体色"。用户之后可以直接用同名 PNG 替换，不用改代码。
颜色表与 specs/048-fluid-tanks/contracts/recipes-and-assets.md 一致。

用法（仓库根目录）：
    python3 design/tank-textures/generate.py
输出：src/main/resources/assets/mcfloatingbackpack/textures/item/<档次>_<液体>_tank.png
依赖：Pillow。本目录属于 design/（开发期资源），不进 jar；生成出的 PNG 在 src/main/resources 里，随 jar 发布，不用 Git LFS。
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources",
                   "assets", "mcfloatingbackpack", "textures", "item")

TIERS = {"leather": "#8B5A2B", "iron": "#C8C8C8", "gold": "#F2C94C", "netherite": "#4A3F47"}
LIQUIDS = {"water": "#3F76E4", "oil": "#2B2B2B", "lava": "#E8651A", "milk": "#F2F2F2", "honey": "#E3A82B"}


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color)


def draw(tier_hex, liquid_hex):
    frame, liquid = rgb(tier_hex), rgb(liquid_hex)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # 盖：第 1～2 行，x 5..10
    for y in (1, 2):
        for x in range(5, 11):
            px[x, y] = frame + (255,) if y == 1 else shade(frame, 0.75) + (255,)
    # 罐身：第 3～14 行，x 3..12；外框用档次色，上沿高光、下沿阴影
    for y in range(3, 15):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 14)
            if edge:
                factor = 1.15 if y == 3 else (0.7 if y == 14 or x == 12 else 0.95)
                px[x, y] = shade(frame, factor) + (255,)
            elif y <= 5:
                px[x, y] = (200, 215, 225, 90)     # 液面上方的空气，半透明
            else:
                px[x, y] = liquid + (255,)
    # 液体高光（左侧一列）与暗部（底行）
    for y in range(6, 14):
        px[4, y] = shade(liquid, 1.25) + (255,)
    for x in range(4, 12):
        px[x, 13] = shade(liquid, 0.8) + (255,)
    return img


def draw_pipe(liquid_hex):
    """049: 斜放的铁灰色管身，右上角的管口一端是液体色（占位图）。"""
    metal, liquid = rgb("#B8B8B8"), rgb(liquid_hex)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # 管身：从左下到右上的两像素宽斜线，上沿亮、下沿暗
    for i in range(3, 12):
        x, y = i, 15 - i
        px[x, y] = shade(metal, 1.15) + (255,)
        px[x + 1, y] = metal + (255,)
        px[x + 1, y + 1] = shade(metal, 0.8) + (255,)
        px[x, y + 1] = shade(metal, 0.9) + (255,)
    # 管口：右上角 3x3 的液体色，外面一圈深色
    for x in range(11, 16):
        for y in range(0, 5):
            if x in (11, 15) or y in (0, 4):
                px[x, y] = shade(metal, 0.55) + (255,)
            else:
                px[x, y] = liquid + (255,)
    # 左下的接头
    for x in range(1, 4):
        for y in range(12, 15):
            px[x, y] = shade(metal, 0.7) + (255,)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    count = 0
    for tier, tier_hex in TIERS.items():
        for liquid, liquid_hex in LIQUIDS.items():
            if liquid == "lava" and tier == "leather":
                continue
            draw(tier_hex, liquid_hex).save(os.path.join(OUT, f"{tier}_{liquid}_tank.png"))
            count += 1
    for liquid in ("water", "oil", "lava", "milk", "honey"):
        draw_pipe(LIQUIDS[liquid]).save(os.path.join(OUT, f"{liquid}_pipe.png"))
        count += 1
    print(f"wrote {count} textures to {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
