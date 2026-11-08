#!/usr/bin/env python3
"""Draws the 16x16 auto pickup item icon (024) pixel by pixel: an iron hopper with an ender pearl dropping into it.

Usage: make_auto_pickup_icon.py <out.png>

Hand-drawn, no source image: ludo.ai / pixellab were not used (see specs/024-auto-pickup/DELIVERY.md). Development-time tool:
not shipped in the mod. The shapes are computed (a filled circle for the pearl, a stepped funnel for the hopper), outlined
with the dark color and shaded light at the top left and dark at the bottom right, like the vanilla item icons.
"""
import sys

from PIL import Image

SIZE = 16
CLEAR = (0, 0, 0, 0)
OUTLINE = (0x24, 0x24, 0x28, 255)
IRON_LIGHT = (0xCF, 0xCF, 0xD3, 255)
IRON = (0x9A, 0x9A, 0xA0, 255)
IRON_DARK = (0x62, 0x62, 0x68, 255)
PEARL_LIGHT = (0x9A, 0xF0, 0xDC, 255)
PEARL = (0x24, 0xA8, 0x8E, 255)
PEARL_DARK = (0x0E, 0x5C, 0x52, 255)


def outline_of(filled):
    """Pixels of the shape that touch the outside (4-neighbourhood)."""
    edge = set()
    for (x, y) in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in filled:
                edge.add((x, y))
    return edge


def main():
    img = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    px = img.load()

    # The pearl: a circle of radius ~3 around (7.5, 2.5), so it sits above the hopper and just touches its rim.
    pearl = {(x, y) for x in range(SIZE) for y in range(0, 6) if (x - 7.5) ** 2 + (y - 2.5) ** 2 <= 3.3 ** 2}
    pearl_edge = outline_of(pearl)
    for (x, y) in pearl:
        if (x, y) in pearl_edge:
            px[x, y] = OUTLINE
        elif (x - 7.5) + (y - 2.5) < -1.2:
            px[x, y] = PEARL_LIGHT
        elif (x - 7.5) + (y - 2.5) > 1.2:
            px[x, y] = PEARL_DARK
        else:
            px[x, y] = PEARL

    # The hopper: a rim (rows 6-7, x 1..14), a funnel that narrows (rows 8-12) and a spout (rows 13-15, x 6..9).
    hopper = set()
    for x in range(1, 15):
        hopper.update({(x, 6), (x, 7)})
    for y, (x0, x1) in zip(range(8, 13), ((2, 13), (3, 12), (4, 11), (5, 10), (6, 9))):
        hopper.update((x, y) for x in range(x0, x1 + 1))
    for y in range(13, 16):
        hopper.update((x, y) for x in range(6, 10))
    edge = outline_of(hopper)
    for (x, y) in hopper:
        if (x, y) in edge:
            px[x, y] = OUTLINE
        elif y == 7:
            px[x, y] = IRON_LIGHT
        elif x >= 9 or y >= 13 and x >= 8:
            px[x, y] = IRON_DARK
        elif x >= 7:
            px[x, y] = IRON
        else:
            px[x, y] = IRON_LIGHT
    # the dark opening of the hopper under the pearl
    for x in range(2, 14):
        px[x, 6] = OUTLINE

    img.save(sys.argv[1])


if __name__ == "__main__":
    main()
