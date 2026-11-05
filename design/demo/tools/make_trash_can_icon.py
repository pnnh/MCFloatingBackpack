#!/usr/bin/env python3
"""Turns a large generated pixel-art image into the 16x16 trash can item icon (021).

Usage: make_trash_can_icon.py <source.png> <out.png> [--size 16] [--colors 12]

Steps: the near-white background (flood-filled from the border, plus any enclosed near-white hole) becomes
transparent, the content is cropped and scaled so it fills the icon, alpha is made 0/255, and the colors are reduced to a small palette. Development-time tool: not shipped in the mod.
Source images (here: design/demo/tools/trash_can_icon/, generated with ludo.ai, see 021 DELIVERY.md) are inputs only.
"""
import argparse
from collections import deque

from PIL import Image


def near_white(px, threshold=235):
    return px[0] >= threshold and px[1] >= threshold and px[2] >= threshold


def clear_background(img):
    w, h = img.size
    px = img.load()
    seen = [[False] * h for _ in range(w)]
    queue = deque()
    for x in range(w):
        queue.append((x, 0)); queue.append((x, h - 1))
    for y in range(h):
        queue.append((0, y)); queue.append((w - 1, y))
    while queue:
        x, y = queue.popleft()
        if not (0 <= x < w and 0 <= y < h) or seen[x][y]:
            continue
        seen[x][y] = True
        if px[x, y][3] == 0 or near_white(px[x, y]):
            px[x, y] = (0, 0, 0, 0)
            queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    # enclosed near-white holes (any gap inside the shape): also transparent
    for y in range(h):
        for x in range(w):
            if px[x, y][3] != 0 and near_white(px[x, y]):
                px[x, y] = (0, 0, 0, 0)
    return img


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("out")
    parser.add_argument("--size", type=int, default=16)
    parser.add_argument("--colors", type=int, default=12)
    args = parser.parse_args()

    img = clear_background(Image.open(args.source).convert("RGBA"))
    img = img.crop(img.getbbox())
    w, h = img.size
    scale = args.size / max(w, h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    small = img.resize((nw, nh), Image.BOX)

    # alpha 0/255, then a small palette for the opaque pixels
    rgb = Image.new("RGB", small.size, (0, 0, 0))
    alpha = Image.new("L", small.size, 0)
    sp, rp, ap = small.load(), rgb.load(), alpha.load()
    for y in range(nh):
        for x in range(nw):
            r, g, b, a = sp[x, y]
            if a >= 128:
                rp[x, y] = (r, g, b)
                ap[x, y] = 255
    quant = rgb.quantize(colors=args.colors, method=Image.Quantize.MEDIANCUT).convert("RGB")
    result = Image.new("RGBA", (args.size, args.size), (0, 0, 0, 0))
    ox, oy = (args.size - nw) // 2, args.size - nh  # bottom-aligned like most item icons
    qp = quant.load()
    out = result.load()
    for y in range(nh):
        for x in range(nw):
            if ap[x, y]:
                out[ox + x, oy + y] = qp[x, y] + (255,)
    result.save(args.out)
    print(f"{args.out}: {args.size}x{args.size}, content {nw}x{nh}")


if __name__ == "__main__":
    main()
