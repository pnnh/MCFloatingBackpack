#!/usr/bin/env python3
"""Draws the 16x16 icons of the mobile tools (053: the jukebox; 054: the crafting table; 055 adds the furnace) pixel by pixel.

Usage: make_mobile_tool_icons.py <out_dir> [--preview <preview_dir>]

Hand-drawn like the category extension icons (036): the same iron plate (a dark 1-pixel outline, light at the top left, dark at the
bottom right) with a 10x10 picture on it, so the tools look like the family of the extensions and the other tools. The plate and the
helpers are the ones of make_category_extension_icons.py. Files are named <tool>.png. Development-time tool: not shipped in the mod.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_category_extension_icons import SIZE, picture, render, rgb  # noqa: E402


def icons():
    """{tool: {(x, y): color}} of the pictures."""
    jukebox = picture(
        [
            "..wwwwww..",
            ".wWWWWWWw.",
            ".wWkkkkWw.",
            ".wkkrrkkw.",
            ".wkkrrkkw.",
            ".wWkkkkWw.",
            ".wWWWWWWw.",
            ".wwwwwwww.",
            "..n..n..n.",
            "...nn.nn..",
        ],
        {"w": rgb(0x5A3A1C), "W": rgb(0xA8773E), "k": rgb(0x14141A), "r": rgb(0xD83A3A), "n": rgb(0x3A8A3A)},
    )
    crafting_table = picture(
        [
            "..........",
            ".wwwwwwww.",
            ".wWWgWWgw.",
            ".wWWgWWgw.",
            ".wggggggw.",
            ".wWWgWWgw.",
            ".wWWgWWgw.",
            ".wwwwwwww.",
            "..l....l..",
            "..l....l..",
        ],
        {"w": rgb(0x5A3A1C), "W": rgb(0xC8A060), "g": rgb(0x8A5A2B), "l": rgb(0x3E2812)},
    )
    return {"mobile_jukebox": jukebox, "mobile_crafting_table": crafting_table}


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
    pictures = icons()
    for tool, pixels in pictures.items():
        img = render(pixels)
        img.save(os.path.join(out_dir, tool + ".png"))
        if preview_dir:
            img.resize((SIZE * 12, SIZE * 12), Image.NEAREST).save(os.path.join(preview_dir, tool + "_x12.png"))
    print("wrote", len(pictures), "icons to", out_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
