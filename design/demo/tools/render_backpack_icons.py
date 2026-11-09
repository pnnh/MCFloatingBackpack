#!/usr/bin/env python3
"""Renders the five backpacks' flat GUI icons (028) from their OBJ models and skin textures.

Usage: render_backpack_icons.py [--size 32] [--supersample 4] [--out DIR] [--preview FILE]

Why: the backpack models have 16,000-20,000 faces each and the game draws every item in the hotbar, every frame, face by face.
In the GUI and on the ground the backpack is a flat icon instead (models/item/<tier>_floating_backpack.json, a
neoforge:separate_transforms model); the 3D model stays for the hands, the back layer and item frames.

How: a small software rasterizer (pure Python + PIL, the project has no numpy). Each OBJ is read, moved to the centre of its
block, turned the way the old GUI display turned it (rotation [30, 225, 0]: first about Y, then about X), projected
orthographically, and drawn with a z-buffer, back faces culled, the skin sampled per pixel and a soft directional light. It
is drawn at SUPERSAMPLE times the size and averaged down (colours only from covered samples, so no dark halo), which gives
smooth edges. All five tiers share one scale (the largest silhouette fits with a margin) so their relative sizes are kept;
each is centred on its own silhouette. The outermost ring of pixels stays transparent so the texture atlas cannot bleed.
Development-time tool: not shipped in the mod. The output is deterministic.
"""
import argparse
import math
import os

from PIL import Image

TIERS = ["leather", "iron", "gold", "diamond", "netherite"]
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mcfloatingbackpack")

# the old GUI display of the models: rotation [30, 225, 0]
ROT_X = math.radians(30.0)
ROT_Y = math.radians(225.0)
# light, in view space (x right, y up, z towards the viewer): from the upper left, in front
LIGHT = (-0.45, 0.70, 0.55)
AMBIENT = 0.60
DIFFUSE = 0.40
# pixels left free on every side (1 for the atlas ring, plus a little air)
MARGIN = 2.0


def load_obj(path):
    verts, uvs, faces = [], [], []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith("v "):
                _, x, y, z = line.split()[:4]
                verts.append((float(x), float(y), float(z)))
            elif line.startswith("vt "):
                parts = line.split()
                uvs.append((float(parts[1]), float(parts[2])))
            elif line.startswith("f "):
                refs = []
                for tok in line.split()[1:]:
                    bits = tok.split("/")
                    refs.append((int(bits[0]) - 1, int(bits[1]) - 1 if len(bits) > 1 and bits[1] else -1))
                for i in range(1, len(refs) - 1):  # a fan, in case of quads or larger
                    faces.append((refs[0], refs[i], refs[i + 1]))
    return verts, uvs, faces


def turn(p):
    """Centre the block on the origin, then rotate about Y and then about X."""
    x, y, z = p[0] - 0.5, p[1] - 0.5, p[2] - 0.5
    cy, sy = math.cos(ROT_Y), math.sin(ROT_Y)
    x, z = cy * x + sy * z, -sy * x + cy * z
    cx, sx = math.cos(ROT_X), math.sin(ROT_X)
    y, z = cx * y - sx * z, sx * y + cx * z
    return (x, y, z)


def normalize(v):
    n = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) or 1.0
    return (v[0] / n, v[1] / n, v[2] / n)


def render(verts, uvs, faces, texture, scale, centre, size, supersample):
    big = size * supersample
    zbuf = [-1e9] * (big * big)
    color = [None] * (big * big)
    tw, th = texture.size
    tex = texture.convert("RGB").load()
    light = normalize(LIGHT)
    turned = [turn(v) for v in verts]
    half = big / 2.0
    for tri in faces:
        pts = [turned[r[0]] for r in tri]
        a, b, c = pts
        ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        n = normalize((uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx))
        if n[2] <= 0.0:  # facing away from the viewer
            continue
        shade = AMBIENT + DIFFUSE * max(0.0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
        sp = [(half + (p[0] - centre[0]) * scale * supersample, half - (p[1] - centre[1]) * scale * supersample, p[2]) for p in pts]
        area = (sp[1][0] - sp[0][0]) * (sp[2][1] - sp[0][1]) - (sp[2][0] - sp[0][0]) * (sp[1][1] - sp[0][1])
        if abs(area) < 1e-12:
            continue
        min_x = max(0, int(math.floor(min(p[0] for p in sp))))
        max_x = min(big - 1, int(math.ceil(max(p[0] for p in sp))))
        min_y = max(0, int(math.floor(min(p[1] for p in sp))))
        max_y = min(big - 1, int(math.ceil(max(p[1] for p in sp))))
        uv = [uvs[r[1]] if r[1] >= 0 else (0.0, 0.0) for r in tri]
        inv = 1.0 / area
        for py in range(min_y, max_y + 1):
            fy = py + 0.5
            for px in range(min_x, max_x + 1):
                fx = px + 0.5
                w0 = ((sp[1][0] - fx) * (sp[2][1] - fy) - (sp[2][0] - fx) * (sp[1][1] - fy)) * inv
                w1 = ((sp[2][0] - fx) * (sp[0][1] - fy) - (sp[0][0] - fx) * (sp[2][1] - fy)) * inv
                w2 = 1.0 - w0 - w1
                if w0 < 0.0 or w1 < 0.0 or w2 < 0.0:
                    continue
                z = w0 * sp[0][2] + w1 * sp[1][2] + w2 * sp[2][2]
                i = py * big + px
                if z <= zbuf[i]:
                    continue
                u = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
                v = 1.0 - (w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1])  # flip_v
                r, g, bl = tex[min(tw - 1, max(0, int(u * tw))), min(th - 1, max(0, int(v * th)))]
                zbuf[i] = z
                color[i] = (min(255, r * shade), min(255, g * shade), min(255, bl * shade))
    # average down: colours only from covered samples, alpha from coverage
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px_out = out.load()
    for y in range(size):
        for x in range(size):
            total = [0.0, 0.0, 0.0]
            covered = 0
            for sy in range(supersample):
                row = (y * supersample + sy) * big + x * supersample
                for sx in range(supersample):
                    c = color[row + sx]
                    if c is not None:
                        covered += 1
                        total[0] += c[0]
                        total[1] += c[1]
                        total[2] += c[2]
            if covered:
                alpha = int(round(255.0 * covered / (supersample * supersample)))
                px_out[x, y] = (int(round(total[0] / covered)), int(round(total[1] / covered)), int(round(total[2] / covered)), alpha)
    return out


def bounds(verts):
    xs, ys = [], []
    for v in verts:
        t = turn(v)
        xs.append(t[0])
        ys.append(t[1])
    return min(xs), max(xs), min(ys), max(ys)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--size", type=int, default=32)
    parser.add_argument("--supersample", type=int, default=4)
    parser.add_argument("--out", default=os.path.join(ASSETS, "textures", "item"))
    parser.add_argument("--preview", help="also write a contact sheet of the five icons, scaled up, to this file")
    args = parser.parse_args()

    models = {}
    for tier in TIERS:
        obj = os.path.join(ASSETS, "models", "item", "%s_backpack.obj" % tier)
        verts, uvs, faces = load_obj(obj)
        models[tier] = (verts, uvs, faces, Image.open(os.path.join(ASSETS, "textures", "item", "%s_floating_backpack.png" % tier)))
        print("%-10s %6d vertices %6d triangles" % (tier, len(verts), len(faces)))

    # one scale for all five: the largest silhouette fits inside the size minus the margins
    limit = args.size / 2.0 - MARGIN
    biggest = 0.0
    for tier in TIERS:
        x0, x1, y0, y1 = bounds(models[tier][0])
        biggest = max(biggest, (x1 - x0) / 2.0, (y1 - y0) / 2.0)
    scale = limit / biggest
    print("scale %.3f pixels per block unit" % scale)

    os.makedirs(args.out, exist_ok=True)
    icons = []
    for tier in TIERS:
        verts, uvs, faces, texture = models[tier]
        x0, x1, y0, y1 = bounds(verts)
        centre = ((x0 + x1) / 2.0, (y0 + y1) / 2.0)
        icon = render(verts, uvs, faces, texture, scale, centre, args.size, args.supersample)
        path = os.path.join(args.out, "%s_floating_backpack_icon.png" % tier)
        icon.save(path)
        icons.append(icon)
        print("wrote", os.path.relpath(path, ROOT))

    if args.preview:
        zoom = 8
        sheet = Image.new("RGBA", (args.size * zoom * len(icons), args.size * zoom * 2), (0, 0, 0, 0))
        for index, icon in enumerate(icons):
            big = icon.resize((args.size * zoom, args.size * zoom), Image.NEAREST)
            dark = Image.new("RGBA", big.size, (60, 60, 60, 255))
            light = Image.new("RGBA", big.size, (198, 198, 198, 255))
            sheet.paste(Image.alpha_composite(dark, big), (index * args.size * zoom, 0))
            sheet.paste(Image.alpha_composite(light, big), (index * args.size * zoom, args.size * zoom))
        sheet.save(args.preview)
        print("wrote", args.preview)


if __name__ == "__main__":
    main()
