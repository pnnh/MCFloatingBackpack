#!/usr/bin/env python3
"""Draws the ten 16x16 category extension item icons (036) pixel by pixel.

Usage: make_category_extension_icons.py <out_dir> [--preview <preview_dir>]

Hand-drawn, no source image: ludo.ai / pixellab were not used (same as the auto pickup icon, see
specs/024-auto-pickup/DELIVERY.md); this script is the source. Development-time tool: not shipped in the mod.

Every icon is the same iron plate (a dark 1-pixel outline, light at the top left and dark at the bottom right, like the
vanilla item icons, which echoes the four iron ingots of the recipes) with a 10x10 picture of the category on it, each in
its own colors so that the ten can be told apart at the size of a slot. Files are named <category>_extension.png.
"""
import math
import os
import sys

from PIL import Image

SIZE = 16
CLEAR = (0, 0, 0, 0)
OUTLINE = (0x24, 0x24, 0x28, 255)
PLATE_LIGHT = (0xCF, 0xCF, 0xD3, 255)
PLATE = (0x9A, 0x9A, 0xA0, 255)
PLATE_DARK = (0x62, 0x62, 0x68, 255)


def rgb(hex_value):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, 255)


def plate():
    """The shared base: {(x, y): color} for the 14x14 rounded iron plate."""
    cells = {(x, y) for x in range(1, 15) for y in range(1, 15)}
    cells -= {(1, 1), (14, 1), (1, 14), (14, 14)}
    out = {}
    for (x, y) in cells:
        edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            out[(x, y)] = OUTLINE
        elif x == 2 or y == 2:
            out[(x, y)] = PLATE_LIGHT
        elif x == 13 or y == 13:
            out[(x, y)] = PLATE_DARK
        else:
            out[(x, y)] = PLATE
    return out


def picture(rows, palette):
    """A 10x10 picture from ten strings of ten characters; '.' leaves the plate showing."""
    assert len(rows) == 10, rows
    pixels = {}
    for y, row in enumerate(rows):
        assert len(row) == 10, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                pixels[(x + 3, y + 3)] = palette[ch]
    return pixels


def disc_picture(rim, body, shine, label, hole_hint):
    """A record seen from above: black disc with a lighter ring, a coloured label and a hole."""
    pixels = {}
    cx = cy = 4.5
    for y in range(10):
        for x in range(10):
            d = math.hypot(x - cx, y - cy)
            if d > 4.6:
                continue
            if d > 3.9:
                color = rim
            elif d < 0.9:
                color = hole_hint
            elif d < 2.0:
                color = label
            else:
                color = shine if (x - cx) + (y - cy) < -2.5 and d > 2.6 else body
            pixels[(x + 3, y + 3)] = color
    return pixels


def log_picture():
    """The end of a log: bark, then growth rings."""
    bark = rgb(0x5A3A1C)
    ring_dark = rgb(0x8A5E2E)
    ring_light = rgb(0xC4975A)
    pixels = {}
    cx = cy = 4.5
    for y in range(10):
        for x in range(10):
            d = math.hypot(x - cx, y - cy)
            if d > 4.7:
                continue
            if d > 3.9:
                color = bark
            elif d > 2.9:
                color = ring_light
            elif d > 1.9:
                color = ring_dark
            elif d > 0.9:
                color = ring_light
            else:
                color = ring_dark
            pixels[(x + 3, y + 3)] = color
    return pixels


def icons():
    """{category: {(x, y): color}} of the ten pictures."""
    bread = picture(
        [
            "..........",
            "...bbbb...",
            "..bLLLLb..",
            ".bLLcLcLb.",
            ".bLcLcLLb.",
            ".bLLLLLLb.",
            ".bLLLLLLb.",
            "..bbbbbb..",
            "..........",
            "..........",
        ],
        {"b": rgb(0x7A4412), "L": rgb(0xD9A04A), "c": rgb(0xF0C878)},
    )
    pickaxe = picture(
        [
            "..GGGGGG..",
            ".GgggggG..",
            "GG..hh.GG.",
            "G...hh..G.",
            "....hh....",
            "....hh....",
            "....hh....",
            "....hh....",
            "....hh....",
            "....hh....",
        ],
        {"G": rgb(0x5C5C66), "g": rgb(0xDADAE4), "h": rgb(0x8A5A2B)},
    )
    sword = picture(
        [
            "........sS",
            ".......sSS",
            "......sSS.",
            ".....sSS..",
            "....sSS...",
            ".g.sSS....",
            "..ggS.....",
            "..hgg.....",
            ".hh.g.....",
            "hh........",
        ],
        {"s": rgb(0xF2F4FA), "S": rgb(0x8E96AA), "g": rgb(0xE2B33A), "h": rgb(0x7A4A22)},
    )
    helmet = picture(
        [
            "...rrrr...",
            "..SSSSSS..",
            ".SsssssSS.",
            ".SsssssSS.",
            ".SsVVVVSS.",
            ".SsVVVVSS.",
            ".SsV..VSS.",
            ".SS....SS.",
            "..........",
            "..........",
        ],
        {"S": rgb(0x5C6070), "s": rgb(0xC2C7D4), "V": rgb(0x2A2C36), "r": rgb(0xC83A3A)},
    )
    ore = picture(
        [
            "...GGGG...",
            "..GgggGG..",
            ".GggCgggG.",
            ".GgggggCG.",
            "GGgCgggggG",
            "GgggggCggG",
            "GgCggggggG",
            ".GgggCggG.",
            "..GGGGGG..",
            "..........",
        ],
        {"G": rgb(0x4A4A52), "g": rgb(0x8E8E98), "C": rgb(0x38C8D8)},
    )
    wheat = picture(
        [
            "....yY....",
            "...yYyY...",
            "....yY....",
            "...yYyY...",
            "....yY....",
            "...yYyY...",
            "....g.....",
            "....g.....",
            "....g.....",
            "....g.....",
        ],
        {"y": rgb(0xF2CC3A), "Y": rgb(0xB88A14), "g": rgb(0x5E9A2A)},
    )
    potion = picture(
        [
            "...kkkk...",
            "...kccc...",
            "....cc....",
            "...cppc...",
            "..cpppPc..",
            "..cpPpPc..",
            "..cppPPc..",
            "...cccc...",
            "..........",
            "..........",
        ],
        {"k": rgb(0x7A5A32), "c": rgb(0xDDEAF2), "p": rgb(0xC83AA8), "P": rgb(0x8A1E78)},
    )
    redstone = picture(
        [
            "..........",
            "..........",
            "....rR....",
            "...rRrR...",
            "..rRrRrR..",
            ".rRrRrRrR.",
            ".RrRrRrRr.",
            "..........",
            "..........",
            "..........",
        ],
        {"r": rgb(0xE8241C), "R": rgb(0x8A0C0C)},
    )
    return {
        "food": bread,
        "tool": pickaxe,
        "weapon": sword,
        "armor": helmet,
        "ore": ore,
        "crop": wheat,
        "wood": log_picture(),
        "potion": potion,
        "redstone": redstone,
        "disc": disc_picture(
            rim=rgb(0x3A3A44), body=rgb(0x16161C), shine=rgb(0x3A3C4C), label=rgb(0xD83A3A), hole_hint=CLEAR
        ),
    }


def render(pixels):
    img = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    px = img.load()
    for (x, y), color in plate().items():
        px[x, y] = color
    for (x, y), color in pixels.items():
        px[x, y] = color
    return img


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

    order = ["food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc"]
    pictures = icons()
    rendered = {}
    for category in order:
        img = render(pictures[category])
        rendered[category] = img
        img.save(os.path.join(out_dir, category + "_extension.png"))
        if preview_dir:
            img.resize((SIZE * 12, SIZE * 12), Image.NEAREST).save(os.path.join(preview_dir, category + "_x12.png"))

    if preview_dir:
        # one overview: five by two on a dark and a light checker so that transparency and the plate can be judged
        scale = 8
        cell = SIZE * scale
        sheet = Image.new("RGBA", (cell * 5, cell * 2), (60, 60, 66, 255))
        for i, category in enumerate(order):
            big = rendered[category].resize((cell, cell), Image.NEAREST)
            sheet.alpha_composite(big, ((i % 5) * cell, (i // 5) * cell))
        sheet.save(os.path.join(preview_dir, "overview.png"))
        # and at the real size of a slot (18 pixels) magnified 4 times, as the player sees them
        small = Image.new("RGBA", (18 * 10, 18), (139, 139, 139, 255))
        for i, category in enumerate(order):
            small.alpha_composite(rendered[category], (i * 18 + 1, 1))
        small.resize((18 * 10 * 4, 18 * 4), Image.NEAREST).save(os.path.join(preview_dir, "slot_size.png"))
    print("wrote", len(order), "icons to", out_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
