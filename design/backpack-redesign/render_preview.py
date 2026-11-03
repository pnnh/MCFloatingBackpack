#!/usr/bin/env python3
"""Rough isometric-ish preview renderer for the prototype cuboid model.

Not a Minecraft-accurate renderer -- just enough 3D (rotate + orthographic project + painter's
algorithm + backface culling) to sanity-check the box layout and confirm the strap gap is
actually closed, without needing Blender or the game itself.
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw

from generate_leather_prototype import ELEMENTS, face_color

OUT_DIR = Path(__file__).parent


def rotate(point, yaw_deg, pitch_deg):
    x, y, z = point
    yaw = math.radians(yaw_deg)
    x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
    pitch = math.radians(pitch_deg)
    y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
    return (x, y, z)


FACE_DEFS = {
    # name -> (corner selector function given from/to, outward normal in model space)
    "north": (lambda f, t: [(f[0], f[1], f[2]), (t[0], f[1], f[2]), (t[0], t[1], f[2]), (f[0], t[1], f[2])], (0, 0, -1)),
    "south": (lambda f, t: [(f[0], f[1], t[2]), (f[0], t[1], t[2]), (t[0], t[1], t[2]), (t[0], f[1], t[2])], (0, 0, 1)),
    "west": (lambda f, t: [(f[0], f[1], f[2]), (f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], t[2])], (-1, 0, 0)),
    "east": (lambda f, t: [(t[0], f[1], f[2]), (t[0], f[1], t[2]), (t[0], t[1], t[2]), (t[0], t[1], f[2])], (1, 0, 0)),
    "down": (lambda f, t: [(f[0], f[1], f[2]), (f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2])], (0, -1, 0)),
    "up": (lambda f, t: [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])], (0, 1, 0)),
}


def build_polys(yaw_deg, pitch_deg):
    polys = []
    view = rotate((0, 0, -1), yaw_deg, pitch_deg)  # camera looks down -Z after rotation
    for el in ELEMENTS:
        f, t = el["from"], el["to"]
        # Recenter on the model's nominal center (8,8,8) so the rotation pivots sensibly.
        fc = [c - 8 for c in f]
        tc = [c - 8 for c in t]
        for face, (corners_fn, normal) in FACE_DEFS.items():
            corners = corners_fn(fc, tc)
            rn = rotate(normal, yaw_deg, pitch_deg)
            facing = rn[0] * view[0] + rn[1] * view[1] + rn[2] * view[2]
            if facing >= 0:
                continue  # backface-cull: normal pointing away from the camera
            rotated = [rotate(c, yaw_deg, pitch_deg) for c in corners]
            depth = sum(c[2] for c in rotated) / 4
            color = face_color(el, face)
            polys.append((depth, rotated, color, f"{el['name']}.{face}"))
    polys.sort(key=lambda p: p[0])  # painter's algorithm: farthest (most negative z) first
    return polys


def render(yaw_deg, pitch_deg, out_name, size=480, px_scale=18):
    polys = build_polys(yaw_deg, pitch_deg)
    img = Image.new("RGBA", (size, size), (46, 46, 52, 255))
    draw = ImageDraw.Draw(img)
    cx, cy = size / 2, size / 2
    for depth, rotated, color, label in polys:
        pts = [(cx + p[0] * px_scale, cy - p[1] * px_scale) for p in rotated]
        shade = max(0.0, min(1.0, 1.0 + depth * 0.03))
        shaded = tuple(int(c * shade) if i < 3 else c for i, c in enumerate(color))
        draw.polygon(pts, fill=shaded, outline=(20, 14, 8, 255))
    img.save(OUT_DIR / out_name)
    print(f"Wrote {out_name} ({len(polys)} visible faces)")


if __name__ == "__main__":
    # Front-ish 3/4 view (how it mostly reads when worn/held) and a rear 3/4 view aimed right at
    # the old gap, to make the fix easy to eyeball.
    render(yaw_deg=35, pitch_deg=-20, out_name="leather_prototype_preview_front.png")
    render(yaw_deg=215, pitch_deg=-15, out_name="leather_prototype_preview_back.png")
