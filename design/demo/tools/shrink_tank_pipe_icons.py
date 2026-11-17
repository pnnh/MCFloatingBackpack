#!/usr/bin/env python3
"""Shrinks the 16x16 liquid tank and pipe item icons a little (056): the picture is resampled to a smaller size and centred again.

Usage: shrink_tank_pipe_icons.py <textures_dir> [--preview <out.png>] [--dry-run]

The tank icons are 10x14 and the pipe icons 15x15 of the 16x16 (the tank picture touches the top and the bottom edges), so next to the
extension icons (12x12 and up to 14x14 with a margin) they looked too big. Tanks become 9x13 and pipes 13x13. The picture is cut
out of the icon, resampled with a box filter on a 4x enlargement (so the thin outline does not turn to mush), alpha is made 0/255, and
it is put back centred. Run it once on the original icons: it works from the bounding box, and running it again shrinks them again.
Development-time tool: not shipped in the mod.
"""
import glob
import os
import sys

from PIL import Image

TANK_SIZE = (9, 13)
PIPE_SIZE = (13, 13)


def shrink(path, size):
    img = Image.open(path).convert("RGBA")
    box = img.getchannel("A").point(lambda a: 255 if a else 0).getbbox()
    crop = img.crop(box)
    # premultiplied resampling: transparent pixels must not tint the edge
    big = crop.resize((crop.width * 4, crop.height * 4), Image.NEAREST)
    small = big.resize(size, Image.BOX)
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ox = (16 - size[0]) // 2
    oy = (16 - size[1]) // 2
    px = small.load()
    dst = out.load()
    for y in range(size[1]):
        for x in range(size[0]):
            r, g, b, a = px[x, y]
            if a >= 128:
                dst[x + ox, y + oy] = (r, g, b, 255)
    return out


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    directory = args[0]
    preview = args[args.index("--preview") + 1] if "--preview" in args else None
    dry = "--dry-run" in args
    jobs = [(p, TANK_SIZE) for p in sorted(glob.glob(os.path.join(directory, "*_tank.png")))]
    jobs += [(p, PIPE_SIZE) for p in sorted(glob.glob(os.path.join(directory, "*_pipe.png")))]
    done = []
    for path, size in jobs:
        out = shrink(path, size)
        done.append((os.path.basename(path), out))
        if not dry:
            out.save(path)
    if preview:
        sc = 8
        cols = 9
        rows = (len(done) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 18 * sc, rows * 18 * sc), (139, 139, 139, 255))
        for i, (_, img) in enumerate(done):
            sheet.alpha_composite(img.resize((16 * sc, 16 * sc), Image.NEAREST), ((i % cols) * 18 * sc + sc, (i // cols) * 18 * sc + sc))
        sheet.save(preview)
    print("shrank" if not dry else "would shrink", len(done), "icons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
