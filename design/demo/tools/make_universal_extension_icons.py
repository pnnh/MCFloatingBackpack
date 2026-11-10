#!/usr/bin/env python3
"""Draws the five 16x16 universal extension item icons (038) pixel by pixel.

Usage: make_universal_extension_icons.py <out_dir> [--preview <preview_dir>]

Hand-drawn, no source image: ludo.ai / pixellab were not used (same as the category extension icons, see
make_category_extension_icons.py, whose iron plate and helpers this uses); this script is the source. Development-time tool: not
shipped in the mod.

Every icon is the same iron plate with a 10x10 picture on it: a frame around a three by three grid of cells (every cell takes
anything: "any item fits here"), in the colours of the backpack tier it is for. The ten category extensions show things (bread, a
pickaxe...); these show a grid, so they cannot be taken for one of those. Files are named <tier>_general_extension.png.
With --preview it also writes each icon magnified 12 times, an overview of the five and one of the five beside the ten category
extensions (to judge that they can be told apart).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import make_category_extension_icons as base  # noqa: E402

rgb = base.rgb
INSIDE = rgb(0x1A1A22)

# tier: (frame light, frame dark, cell light, cell dark)
TIERS = {
    "leather": (rgb(0xB9824B), rgb(0x6B4423), rgb(0xEBCB9C), rgb(0xA67C52)),
    "iron": (rgb(0xE6E6EC), rgb(0x7E7E88), rgb(0xFFFFFF), rgb(0xB4B4BE)),
    "gold": (rgb(0xF7D84C), rgb(0xA27A12), rgb(0xFFF3A6), rgb(0xD4A920)),
    "diamond": (rgb(0x66EDE5), rgb(0x1F8F94), rgb(0xD2FBF8), rgb(0x5BC8CC)),
    "netherite": (rgb(0x6B5878), rgb(0x2B2333), rgb(0xA08CB4), rgb(0x5A4A6A)),
}


def grid_picture(frame_light, frame_dark, cell_light, cell_dark):
    """A 10x10 picture as {(x, y): colour} on the plate (origin at (3, 3) of the icon)."""
    pixels = {}
    for y in range(10):
        for x in range(10):
            if x in (0, 9) or y in (0, 9):
                color = frame_dark if (x == 9 or y == 9) else frame_light
            else:
                color = INSIDE
            pixels[(x + 3, y + 3)] = color
    for cx in (1, 4, 7):
        for cy in (1, 4, 7):
            for dx in (0, 1):
                for dy in (0, 1):
                    # light cell, its bottom row and right column a little darker, like the slots of the panel
                    shade = cell_dark if (dx == 1 or dy == 1) and not (dx == 0 and dy == 0) else cell_light
                    pixels[(cx + dx + 3, cy + dy + 3)] = shade
    return pixels


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    out_dir = args[0]
    preview_dir = args[args.index("--preview") + 1] if "--preview" in args else None
    os.makedirs(out_dir, exist_ok=True)
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)

    rendered = {}
    for tier, colors in TIERS.items():
        img = base.render(grid_picture(*colors))
        rendered[tier] = img
        img.save(os.path.join(out_dir, tier + "_general_extension.png"))
        if preview_dir:
            img.resize((base.SIZE * 12, base.SIZE * 12), Image.NEAREST).save(os.path.join(preview_dir, tier + "_general_x12.png"))

    if preview_dir:
        scale = 8
        cell = base.SIZE * scale
        sheet = Image.new("RGBA", (cell * 5, cell), (60, 60, 66, 255))
        for i, tier in enumerate(TIERS):
            sheet.alpha_composite(rendered[tier].resize((cell, cell), Image.NEAREST), (i * cell, 0))
        sheet.save(os.path.join(preview_dir, "universal_overview.png"))

        # the five beside the ten category extensions, at the real size of a slot (18 pixels) magnified 4 times
        categories = base.icons()
        order = ["food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc"]
        small = Image.new("RGBA", (18 * 15, 18), (139, 139, 139, 255))
        for i, category in enumerate(order):
            small.alpha_composite(base.render(categories[category]), (i * 18 + 1, 1))
        for i, tier in enumerate(TIERS):
            small.alpha_composite(rendered[tier], ((10 + i) * 18 + 1, 1))
        small.resize((18 * 15 * 4, 18 * 4), Image.NEAREST).save(os.path.join(preview_dir, "universal_beside_categories.png"))
    print("wrote", len(TIERS), "icons to", out_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
