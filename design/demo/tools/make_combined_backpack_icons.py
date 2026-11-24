#!/usr/bin/env python3
"""Draws the five 32x32 combined backpack item icons (065) from the five floating backpack icons.

Usage: make_combined_backpack_icons.py <textures_item_dir>

Reads <tier>_floating_backpack_icon.png from the directory and writes <tier>_combined_backpack.png next to it: the very same backpack
(so the tier reads at a glance, and the icon has the same size as the backpacks) with a violet "one to many" link badge in the lower
right corner, so it reads as "a backpack that only works together with other backpacks". The badge is the same for every tier.
Development-time tool: not shipped in the mod. The art is a first version for the author to refine; the files are named
<tier>_combined_backpack.png and are referenced by models/item/<tier>_combined_backpack.json (gui scale 1.2, as the backpack icons).
"""
import os
import sys

from PIL import Image

TIERS = ["leather", "iron", "gold", "diamond", "netherite"]

OUTLINE = (0x1E, 0x14, 0x2A, 255)
PANEL = (0x3A, 0x2A, 0x52, 255)
LINK = (0xB0, 0x6C, 0xFF, 255)
LINK_LIGHT = (0xE8, 0xC8, 0xFF, 255)
LINE = (0x8A, 0x52, 0xD0, 255)

# badge: 12x12 at (19, 19); local coordinates below
BADGE_X, BADGE_Y, BADGE = 19, 19, 12


def draw_badge(img):
    px = img.load()
    for y in range(BADGE):
        for x in range(BADGE):
            corner = x in (0, BADGE - 1) and y in (0, BADGE - 1)
            if corner:
                continue
            edge = x in (0, BADGE - 1) or y in (0, BADGE - 1)
            px[BADGE_X + x, BADGE_Y + y] = OUTLINE if edge else PANEL

    def put(x, y, c):
        px[BADGE_X + x, BADGE_Y + y] = c

    def node(x, y):  # 2x2 node, light on top-left
        put(x, y, LINK_LIGHT)
        put(x + 1, y, LINK)
        put(x, y + 1, LINK)
        put(x + 1, y + 1, LINK)

    # left node (the combined backpack) fans out to two nodes on the right (the connected backpacks), plus a third in the middle
    for x in range(4, 8):
        put(x, 5, LINE)
        put(x, 6, LINE)
    for y in range(2, 10):
        put(6, y, LINE)
    for x in range(6, 8):
        put(x, 2, LINE)
        put(x, 9, LINE)
    node(2, 5)
    node(7, 1)
    node(8, 5)
    node(7, 9)


def main():
    out = sys.argv[1]
    for tier in TIERS:
        img = Image.open(os.path.join(out, tier + "_floating_backpack_icon.png")).convert("RGBA")
        draw_badge(img)
        img.save(os.path.join(out, tier + "_combined_backpack.png"))


if __name__ == "__main__":
    main()
