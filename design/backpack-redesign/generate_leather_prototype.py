#!/usr/bin/env python3
"""Generates redesigned floating-backpack item models + matching textures for all 5 tiers.

Fixes the reported "see-through" hole: the original body box only went from z=7 to z=12,
while the shoulder straps extend out to z=5.6, leaving an uncovered gap behind/between the
straps for y in [4, 8.5]. This version deepens the body box to z=5.8 so it reaches the straps.

The body tapers (wider at the bottom, narrower at the top) across 3 stacked segments instead of
one constant-width box, and carries 3 accessory pouches (left/right/back) that are meant to
become separately-installable upgrade modules later, like Traveler's Backpack's tank/crafting
upgrades -- base tier ships with none of them. Writes both a "full" (all pouches) and a "_base"
(no pouches) variant per tier so both ends of that range can be checked.

Every tier shares the exact same geometry (ELEMENTS); only the 5-color palette changes, driven
by TIER_PALETTES. Only writes files under design/backpack-redesign/ for review -- applying a
tier's output to the actual mod resources (models/item, textures/item) is a separate, deliberate
step done by hand after eyeballing the render.
"""

import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

OUT_DIR = Path(__file__).parent
SCALE = 4  # 1 model unit = 4 texture pixels (texture_size 64 vs the 16-unit model grid)
CANVAS_WIDTH = 128


def _clamp(v):
    return max(0, min(255, v))


def shade(color, delta):
    """Lighten (delta>0) or darken (delta<0) an RGBA color, alpha untouched."""
    return tuple(_clamp(c + delta) if i < 3 else c for i, c in enumerate(color))


# --- Per-tier palette -------------------------------------------------------------
# A flat brown-on-brown reskin was the original complaint: real backpacks (and the
# Sophisticated/Traveler's references) mix a body material with a visibly different hardware
# material plus a small accent color, not one hue throughout. Each tier below follows Minecraft's
# own established material-color associations -- iron's pale steel, gold's warm yellow, diamond's
# cyan, netherite's dark purple-red glint -- both for the hardware (the tier's "armor plating") and
# the accent studs, while the body/flap/strap tones shift from warm leather-brown up through cooler,
# richer canvas tones as the tier gets more advanced. Geometry (ELEMENTS, below) is identical across
# every tier; only these 5 colors change.
TIER_PALETTES = {
    "leather": {
        "body": (120, 78, 42, 255),  # main panel base color
        "flap": (100, 60, 34, 255),  # flap/lid: a shade darker and redder than the body, like a
        # different cut of leather, not a copy-pasted color
        "strap": (74, 44, 24, 255),  # straps: dark leather, but not the old near-black "stitch"
        "hardware": (104, 106, 112, 255),  # buckle / trim bands / rivets: brushed steel gray
        # (darker/more "pewter" than a first pass -- too pale read as near-white under light)
        "accent": (196, 150, 54, 255),  # tier accent stud: brass, fitting the humble base tier
    },
    "iron": {
        "body": (120, 128, 138, 255),  # sturdy pale slate-blue canvas, reinforced look
        "flap": (100, 108, 118, 255),
        "strap": (54, 58, 66, 255),  # dark charcoal straps
        "hardware": (176, 180, 186, 255),  # bright iron-silver plating -- lighter than leather's
        # hardware since on this tier the hardware IS the tier's whole point, it should pop
        "accent": (214, 226, 232, 255),  # pale icy steel accent (iron tool/armor color)
    },
    "gold": {
        "body": (132, 108, 46, 255),  # warm old-gold canvas, richer than leather's plain brown
        "flap": (112, 90, 36, 255),
        "strap": (70, 54, 20, 255),
        "hardware": (198, 160, 60, 255),  # gilded brass-gold hardware
        "accent": (255, 214, 64, 255),  # bright saturated gold-yellow accent
    },
    "diamond": {
        "body": (58, 92, 102, 255),  # cool teal-blue-gray canvas
        "flap": (46, 78, 88, 255),
        "strap": (28, 46, 54, 255),
        "hardware": (170, 190, 196, 255),  # pale platinum-blue hardware
        "accent": (84, 222, 224, 255),  # bright cyan accent (diamond's signature color)
    },
    "netherite": {
        "body": (54, 50, 56, 255),  # near-black charcoal-purple canvas
        "flap": (42, 38, 44, 255),
        "strap": (26, 24, 28, 255),
        "hardware": (90, 86, 94, 255),  # dark gunmetal hardware
        "accent": (152, 58, 86, 255),  # deep purple-red accent (netherite glint)
    },
}

TIER_ORDER = ["leather", "iron", "gold", "diamond", "netherite"]

# The following globals (PALETTE, BODY_BASE, ..., ELEMENTS, DETACHABLE) are (re)populated by
# configure_palette() for whichever tier is currently being built. build() and the paint_*/apply_*
# helpers below read them as module globals rather than taking them as parameters -- this file
# generates one tier at a time, sequentially, so that's safe and keeps every helper's signature
# the same as when this script only handled the leather tier.
PALETTE = None
BODY_BASE = BODY_LIGHT = BODY_DARK = BODY_SIDE = None
FLAP_BASE = FLAP_LIGHT = FLAP_DARK = FLAP_SIDE = None
STRAP_BASE = STRAP_LIGHT = STRAP_DARK = STRAP_SIDE = None
POUCH_BASE = POUCH_LIGHT = POUCH_DARK = POUCH_SIDE = None
HARDWARE = HARDWARE_DARK = HARDWARE_LIGHT = None
ACCENT = ACCENT_DARK = None
ELEMENTS = None
DETACHABLE = None

FACE_ORDER = ["north", "south", "east", "west", "up", "down"]


def configure_palette(tier):
    """(Re)derive every shade + the ELEMENTS list from TIER_PALETTES[tier]. Geometry (the from/to
    coordinates) never changes between tiers -- only which derived color each element's faces use.
    """
    global PALETTE, BODY_BASE, BODY_LIGHT, BODY_DARK, BODY_SIDE
    global FLAP_BASE, FLAP_LIGHT, FLAP_DARK, FLAP_SIDE
    global STRAP_BASE, STRAP_LIGHT, STRAP_DARK, STRAP_SIDE
    global POUCH_BASE, POUCH_LIGHT, POUCH_DARK, POUCH_SIDE
    global HARDWARE, HARDWARE_DARK, HARDWARE_LIGHT, ACCENT, ACCENT_DARK
    global ELEMENTS, DETACHABLE

    PALETTE = TIER_PALETTES[tier]

    BODY_BASE = PALETTE["body"]
    BODY_LIGHT = shade(BODY_BASE, 36)
    BODY_DARK = shade(BODY_BASE, -36)
    BODY_SIDE = shade(BODY_BASE, -18)

    FLAP_BASE = PALETTE["flap"]
    FLAP_LIGHT = shade(FLAP_BASE, 36)
    FLAP_DARK = shade(FLAP_BASE, -36)
    FLAP_SIDE = shade(FLAP_BASE, -18)

    STRAP_BASE = PALETTE["strap"]
    STRAP_LIGHT = shade(STRAP_BASE, 30)
    STRAP_DARK = shade(STRAP_BASE, -30)
    STRAP_SIDE = shade(STRAP_BASE, -15)

    # Pouches read as a visibly different accessory piece (not a clone of the body) by sitting a
    # notch lighter than the body base, same hue family.
    POUCH_BASE = shade(BODY_BASE, 18)
    POUCH_LIGHT = shade(POUCH_BASE, 36)
    POUCH_DARK = shade(POUCH_BASE, -36)
    POUCH_SIDE = shade(POUCH_BASE, -18)

    HARDWARE = PALETTE["hardware"]
    HARDWARE_DARK = shade(HARDWARE, -46)
    HARDWARE_LIGHT = shade(HARDWARE, 46)

    ACCENT = PALETTE["accent"]
    ACCENT_DARK = shade(ACCENT, -44)

    # Elements: (name, from[x,y,z], to[x,y,z], face_color_overrides)
    # Coordinates are in the usual 0-16 Minecraft item-model unit space.
    #
    # The body is 3 stacked segments, each a bit narrower than the one below it (a "stepped
    # taper"), instead of one constant-width box. A single uniform-width box reads as a
    # cabinet/drawer, not a soft-sided bag -- real backpacks are wider at the bottom where the
    # main storage bulges and narrower at the top near the straps/flap. Minecraft cuboids can't
    # curve, so a 2-step taper (3 segments) is the standard blocky-pixel-art way to fake that
    # profile.
    ELEMENTS = [
        {
            "name": "body_bottom",
            "from": [2.2, 1, 5.8],
            "to": [13.8, 5, 12.4],
            "colors": {"up": BODY_LIGHT, "down": BODY_DARK, "ns": BODY_BASE, "ew": BODY_SIDE},
        },
        {
            "name": "body_mid",
            "from": [3.2, 5, 5.8],
            "to": [12.8, 9, 12],
            "colors": {"up": BODY_LIGHT, "down": BODY_DARK, "ns": BODY_BASE, "ew": BODY_SIDE},
        },
        {
            "name": "body_top",
            "from": [4, 9, 5.8],
            "to": [12, 12, 11.6],
            "colors": {"up": BODY_LIGHT, "down": BODY_DARK, "ns": BODY_BASE, "ew": BODY_SIDE},
        },
        {
            # Slight overhang past body_top's narrower top, like a lid lip. Uses the dedicated
            # FLAP_* tone (not BODY_*) -- a visibly different panel color.
            "name": "flap",
            "from": [3.7, 12, 5.8],
            "to": [12.3, 13.2, 12],
            "colors": {"up": FLAP_LIGHT, "down": FLAP_DARK, "ns": FLAP_BASE, "ew": FLAP_SIDE},
        },
        {
            "name": "strap_left",
            "from": [3.8, 4, 4.8],
            "to": [5.4, 13, 7.2],
            "colors": {"up": STRAP_LIGHT, "down": STRAP_DARK, "ns": STRAP_BASE, "ew": STRAP_SIDE},
            "share_uv_with": None,
        },
        {
            "name": "strap_right",
            "from": [10.6, 4, 4.8],
            "to": [12.2, 13, 7.2],
            "colors": {"up": STRAP_LIGHT, "down": STRAP_DARK, "ns": STRAP_BASE, "ew": STRAP_SIDE},
            "share_uv_with": "strap_left",  # mirrors strap_left; same size, reuse its UV rects
        },
        {
            "name": "buckle",
            "from": [7, 5.5, 6.4],
            "to": [9, 7.5, 7.2],
            "colors": {"up": HARDWARE_LIGHT, "down": HARDWARE_DARK, "ns": HARDWARE, "ew": HARDWARE_DARK},
        },
        {
            # One of the 3 detachable accessory pouches (see DETACHABLE below). "Front" here is
            # the face that ends up visible to other players when the backpack is worn -- what
            # the user calls 后边 (back).
            "name": "pouch_back",
            "from": [4, 1.5, 12.4],
            "to": [12, 4.8, 13.4],
            "colors": {"up": POUCH_LIGHT, "down": POUCH_DARK, "ns": POUCH_BASE, "ew": POUCH_SIDE},
            "detachable": True,
        },
        {
            "name": "pouch_right",
            "from": [13.6, 2, 7.5],
            "to": [15.1, 5, 11.2],
            "colors": {"up": POUCH_LIGHT, "down": POUCH_DARK, "ns": POUCH_BASE, "ew": POUCH_SIDE},
            "share_uv_with": None,
            "detachable": True,
        },
        {
            "name": "pouch_left",
            "from": [0.9, 2, 7.5],
            "to": [2.4, 5, 11.2],
            "colors": {"up": POUCH_LIGHT, "down": POUCH_DARK, "ns": POUCH_BASE, "ew": POUCH_SIDE},
            "share_uv_with": "pouch_right",  # mirrors pouch_right; same size, reuse its UV rects
            "detachable": True,
        },
    ]

    # The 3 accessory pouches (left/right/back) are meant to become separately installable
    # upgrade modules later (like Traveler's Backpack tanks/crafting upgrades) -- the base tier
    # of each metal would ship with none of them, and crafted upgrades would add them one at a
    # time, both visually and functionally. At this (art/model) stage that only means: keep each
    # pouch as its own self-contained element that can be dropped from ELEMENTS without leaving a
    # gap. Actually wiring up "is this upgrade installed" as a real game mechanic (items, data
    # components, conditional rendering in Java) is a separate feature, not part of this art
    # prototype.
    DETACHABLE = {el["name"] for el in ELEMENTS if el.get("detachable")}


def size_of(el):
    fx, fy, fz = el["from"]
    tx, ty, tz = el["to"]
    return (tx - fx, ty - fy, tz - fz)


def face_dims(el):
    w, h, d = size_of(el)
    return {
        "north": (w, h),
        "south": (w, h),
        "east": (d, h),
        "west": (d, h),
        "up": (w, d),
        "down": (w, d),
    }


def face_color(el, face):
    c = el["colors"]
    if face in ("up",):
        return c["up"]
    if face in ("down",):
        return c["down"]
    if face in ("east", "west"):
        return c["ew"]
    return c["ns"]


class ShelfPacker:
    """Shelf (row) bin packer with first-fit across all open shelves, not just the latest one.

    Items should be fed tallest-first: each new shelf's height is fixed by the first (tallest)
    item placed on it, and later, shorter items get first-fit into whichever existing shelf has
    enough leftover width -- falling back to a new shelf only when none do.
    """

    def __init__(self, width):
        self.width = width
        self.shelves = []  # each: {"y": int, "height": int, "x": int}
        self.max_y = 0

    def place(self, w, h):
        w = max(1, round(w))
        h = max(1, round(h))
        for shelf in self.shelves:
            if shelf["height"] >= h and shelf["x"] + w <= self.width:
                rect = (shelf["x"], shelf["y"], shelf["x"] + w, shelf["y"] + h)
                shelf["x"] += w
                return rect
        y = self.max_y
        shelf = {"y": y, "height": h, "x": w}
        self.shelves.append(shelf)
        self.max_y = y + h
        return (0, y, w, y + h)


def paint_leather_face(img, rect, base_color, rng):
    """Mottled material look: a few soft blotches (uneven tanning/weave/plating grain) plus fine
    per-pixel grain plus a 1px ambient-occlusion darkening right at the face's own edges.
    Flat-filling a rect reads as plastic; this is what makes it read as a material.
    """
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    blotches = []
    for _ in range(rng.randint(2, 4)):
        cx = rng.uniform(x0, x1)
        cy = rng.uniform(y0, y1)
        radius = rng.uniform(max(2.0, min(w, h) * 0.25), max(3.0, min(w, h) * 0.55))
        intensity = rng.randint(-16, 8)  # blotches read mostly as darker marks
        blotches.append((cx, cy, radius, intensity))
    for px in range(x0, x1):
        for py in range(y0, y1):
            value = 0.0
            for cx, cy, radius, intensity in blotches:
                dist = ((px + 0.5 - cx) ** 2 + (py + 0.5 - cy) ** 2) ** 0.5
                falloff = max(0.0, 1.0 - dist / radius)
                value += intensity * falloff
            value += rng.randint(-7, 7)
            edge_dist = min(px - x0, x1 - 1 - px, py - y0, y1 - 1 - py)
            if edge_dist == 0:
                value -= 16
            elif edge_dist == 1:
                value -= 7
            pixel = tuple(_clamp(c + round(value)) if i < 3 else c for i, c in enumerate(base_color))
            img.putpixel((px, py), pixel)


def paint_buckle_face(img, rect, base_color):
    """Brushed-metal look: corner highlight, opposite corner shadow, flat in between."""
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    highlight = tuple(_clamp(c + 46) if i < 3 else c for i, c in enumerate(base_color))
    shadow = tuple(_clamp(c - 46) if i < 3 else c for i, c in enumerate(base_color))
    for px in range(x0, x1):
        for py in range(y0, y1):
            t = ((px - x0) / max(1, w - 1) + (py - y0) / max(1, h - 1)) / 2
            pixel = tuple(
                _clamp(round(shadow[i] * t + highlight[i] * (1 - t))) if i < 3 else base_color[i]
                for i in range(4)
            )
            img.putpixel((px, py), pixel)
    # A central vertical "pin" (the tongue that actually grips the strap) reads clearly even at
    # this size and is what makes it recognizable as a buckle rather than a plain metal square.
    if w >= 4 and h >= 4:
        pin_x = x0 + w // 2
        for py in range(y0 + 1, y1 - 1):
            img.putpixel((pin_x, py), shadow)


def draw_dashed_rect(draw, rect, color, dash=2, gap=1):
    x0, y0, x1, y1 = rect
    for edge_y in (y0, y1 - 1):
        x = x0
        while x < x1:
            draw.line([(x, edge_y), (min(x + dash - 1, x1 - 1), edge_y)], fill=color)
            x += dash + gap
    for edge_x in (x0, x1 - 1):
        y = y0
        while y < y1:
            draw.line([(edge_x, y), (edge_x, min(y + dash - 1, y1 - 1))], fill=color)
            y += dash + gap


def apply_scuffs(img, rect, rng, lighter):
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    if w < 6 or h < 6:
        return
    for _ in range(rng.randint(1, 3)):
        sx = rng.randint(x0, x1 - 2)
        sy = rng.randint(y0, y1 - 2)
        size = rng.choice([1, 1, 2])
        for dx in range(size):
            for dy in range(size):
                if sx + dx < x1 and sy + dy < y1:
                    img.putpixel((sx + dx, sy + dy), lighter)


def apply_trim_band(draw, rect, edge, height):
    """A solid hardware-colored strip along one edge of a rect -- used in pairs on two adjacent
    elements' touching edges (e.g. body_bottom's top + body_mid's bottom) so they read as one
    continuous belt wrapping the model where those two pieces meet in 3D, even though they're
    unrelated rectangles in the texture atlas.
    """
    x0, y0, x1, y1 = rect
    if edge == "top":
        band = (x0, y0, x1, min(y1, y0 + height))
    elif edge == "bottom":
        band = (x0, max(y0, y1 - height), x1, y1)
    else:
        raise ValueError(edge)
    draw.rectangle([band[0], band[1], band[2] - 1, band[3] - 1], fill=HARDWARE)
    draw.line([(band[0], band[1]), (band[2] - 1, band[1])], fill=HARDWARE_LIGHT)


def apply_accent_studs(img, rect):
    """Two small tier-accent studs flanking roughly where the buckle sits, the one deliberately
    saturated color note against all the muted body/hardware tones -- this is what the
    Sophisticated/Traveler's references use their colored gems/rivets for.
    """
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    if w < 12 or h < 8:
        return
    cy = y0 + round(h * 0.35)
    for cx in (x0 + round(w * 0.25), x0 + round(w * 0.75)):
        img.putpixel((cx, cy), ACCENT_DARK)
        img.putpixel((cx + 1, cy), ACCENT)
        img.putpixel((cx, cy + 1), ACCENT)
        img.putpixel((cx + 1, cy + 1), ACCENT_DARK)


def build(tier, include_pouches=True, suffix=""):
    elements = [
        el for el in ELEMENTS if include_pouches or el["name"] not in DETACHABLE
    ]
    packer = ShelfPacker(CANVAS_WIDTH)
    uv_by_element_face = {}
    placed_rects = []  # for the debug preview + texture painting: (rect, color, label)

    # Collect every face that actually needs its own UV rect (mirrored elements are skipped),
    # then pack tallest-first (first-fit decreasing) so shelves pack tightly instead of each
    # element's faces fighting over leftover space in arrival order.
    pending = []
    for el in elements:
        if el.get("share_uv_with"):
            continue
        dims = face_dims(el)
        for face in FACE_ORDER:
            w, h = dims[face]
            pending.append((el["name"], face, w * SCALE, h * SCALE))
    pending.sort(key=lambda item: item[3], reverse=True)

    faces_uv_by_element = {}
    for name, face, w, h in pending:
        rect = packer.place(w, h)
        faces_uv_by_element.setdefault(name, {})[face] = rect
        placed_rects.append((rect, face_color(next(e for e in elements if e["name"] == name), face), f"{name}.{face}"))

    for el in elements:
        share = el.get("share_uv_with")
        uv_by_element_face[el["name"]] = faces_uv_by_element[share] if share else faces_uv_by_element[el["name"]]

    canvas_height = packer.max_y
    # Round up to a tidy multiple of 8 for a cleaner-looking PNG.
    canvas_height = ((canvas_height + 7) // 8) * 8
    canvas_height = max(canvas_height, 8)

    # --- Paint the texture ---
    img = Image.new("RGBA", (CANVAS_WIDTH, canvas_height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    rng = random.Random(1337)  # fixed seed: deterministic, re-running the script reproduces it
    big_panel_labels = {
        "body_bottom.north", "body_bottom.south",
        "body_mid.north", "body_mid.south",
        "body_top.north", "body_top.south",
        "flap.north", "flap.south",
        "pouch_back.north", "pouch_back.south",
        "pouch_right.north", "pouch_right.south",
    }
    # The trim band sits at the body_bottom / body_mid seam -- the widest, most prominent
    # transition -- on both the front/back and the side faces, so it reads as a belt wrapping
    # all the way around rather than just two stripes on the front.
    trim_pairs = [
        ("body_bottom.north", "top"), ("body_bottom.south", "top"), ("body_bottom.east", "top"),
        ("body_bottom.west", "top"),
        ("body_mid.north", "bottom"), ("body_mid.south", "bottom"), ("body_mid.east", "bottom"),
        ("body_mid.west", "bottom"),
    ]
    trim_pairs = dict(trim_pairs)

    for (x0, y0, x1, y1), color, label in placed_rects:
        darker = tuple(max(0, c - 40) if i < 3 else c for i, c in enumerate(color))
        lighter = tuple(min(255, c + 36) if i < 3 else c for i, c in enumerate(color))
        if label.startswith("buckle"):
            paint_buckle_face(img, (x0, y0, x1, y1), color)
            # A crisp 1px dark outline separates the buckle from the surrounding body material --
            # without it, the metal gradient alone tends to blend into the body's own mottling at
            # a glance instead of reading as a distinct piece of hardware.
            draw.rectangle([x0, y0, x1 - 1, y1 - 1], outline=HARDWARE_DARK, width=1)
            continue
        paint_leather_face(img, (x0, y0, x1, y1), color, rng)
        # Stitched dashed inset border on the big visible panels -- a seam a couple of texels in
        # from the edge, dashed rather than a solid outline so it actually reads as stitching
        # instead of just a second rectangle.
        if label in big_panel_labels and (x1 - x0) > 6 and (y1 - y0) > 6:
            draw_dashed_rect(draw, (x0 + 2, y0 + 2, x1 - 2, y1 - 2), darker)
        # Small dark corner rivets on the front faces of body/pouch panels, each with a
        # one-texel metallic highlight so they read as a dome, not just a dark square.
        if label.endswith(".north") and (
            label.startswith("body") or label.startswith("pouch")
        ) and (x1 - x0) > 8 and (y1 - y0) > 8:
            for cx, cy in ((x0 + 2, y0 + 2), (x1 - 3, y0 + 2), (x0 + 2, y1 - 3), (x1 - 3, y1 - 3)):
                draw.rectangle([cx, cy, cx + 1, cy + 1], fill=HARDWARE_DARK)
                img.putpixel((cx, cy), HARDWARE)
        # A few scuffed/worn highlight flecks on the big front panels, mostly near the edges
        # where real material actually wears first.
        if label in big_panel_labels:
            apply_scuffs(img, (x0, y0, x1, y1), rng, lighter)
        # Hardware trim band at the body_bottom/body_mid seam (see trim_pairs above).
        if label in trim_pairs:
            apply_trim_band(draw, (x0, y0, x1, y1), trim_pairs[label], height=2)
        # Two accent studs flanking the buckle, only on the panel the buckle actually sits on.
        if label in ("body_mid.north", "body_mid.south"):
            apply_accent_studs(img, (x0, y0, x1, y1))
    img.save(OUT_DIR / f"{tier}_prototype{suffix}_texture.png")

    # --- Debug UV-layout preview (upscaled, labeled) so it's easy to sanity-check by eye ---
    preview_scale = 6
    preview = Image.new(
        "RGBA", (CANVAS_WIDTH * preview_scale, canvas_height * preview_scale), (30, 30, 30, 255)
    )
    pdraw = ImageDraw.Draw(preview)
    for (x0, y0, x1, y1), color, label in placed_rects:
        box = [x0 * preview_scale, y0 * preview_scale, x1 * preview_scale - 1, y1 * preview_scale - 1]
        pdraw.rectangle(box, fill=color, outline=(255, 255, 255, 255))
        pdraw.text((box[0] + 2, box[1] + 2), label, fill=(255, 255, 255, 255))
    preview.save(OUT_DIR / f"{tier}_prototype{suffix}_uv_debug.png")

    # --- Emit the item model JSON ---
    # IMPORTANT: Minecraft's actual model loader (verified by reading FaceBakery.fillVertex in
    # the NeoForge sources: `sprite.getU(blockFaceUV.getU(vertexIndex) / 16.0F)`) ALWAYS divides
    # "uv" by a hardcoded 16, and never reads "texture_size" anywhere in the block/model or
    # texture packages -- "texture_size" is accepted as inert metadata (useful to Blockbench for
    # letting you author/view UV in pixel units) but has zero effect on how the game samples the
    # texture. "uv" values must therefore always be in the nominal 0-16 grid, scaled by the
    # texture's actual pixel dimensions, never raw pixel coordinates -- emitting raw pixel
    # coordinates (as an earlier version of this script did) makes UV go far outside 0-16, which
    # wraps/repeats across the whole shared item atlas and renders as multicolored static noise.
    model = {
        "texture_size": [CANVAS_WIDTH, canvas_height],
        "textures": {
            "backpack": f"mcfloatingbackpack:item/{tier}_floating_backpack_prototype{suffix}",
            "particle": f"mcfloatingbackpack:item/{tier}_floating_backpack_prototype{suffix}",
        },
        "elements": [],
    }
    for el in elements:
        faces_uv = uv_by_element_face[el["name"]]
        faces = {}
        for face in FACE_ORDER:
            x0, y0, x1, y1 = faces_uv[face]
            uv = [
                round(x0 * 16.0 / CANVAS_WIDTH, 4),
                round(y0 * 16.0 / canvas_height, 4),
                round(x1 * 16.0 / CANVAS_WIDTH, 4),
                round(y1 * 16.0 / canvas_height, 4),
            ]
            faces[face] = {"uv": uv, "texture": "#backpack"}
        model["elements"].append(
            {
                "name": el["name"],
                "from": el["from"],
                "to": el["to"],
                "faces": faces,
            }
        )

    # Reuse the shipped model's display transforms -- same rough bounding box, no need to retune.
    model["display"] = {
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 2.5, 1], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 2.5, 1], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, 90, 25], "translation": [1.5, 3.5, 0], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.5, 3.5, 0], "scale": [0.7, 0.7, 0.7]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.95, 0.95, 0.95]},
        "fixed": {"rotation": [0, 180, 0], "scale": [1.1, 1.1, 1.1]},
    }

    (OUT_DIR / f"{tier}_prototype{suffix}.json").write_text(json.dumps(model, indent=2) + "\n")
    print(f"[{tier}/{suffix or 'full'}] texture canvas: {CANVAS_WIDTH}x{canvas_height}, {len(elements)} elements")


if __name__ == "__main__":
    for tier in TIER_ORDER:
        configure_palette(tier)
        # Full variant: all 3 detachable pouches present (what a fully-upgraded backpack would
        # show).
        build(tier, include_pouches=True, suffix="")
        # Base variant: no pouches at all -- confirms the body/flap/straps/buckle alone still
        # look like a finished item, since that's what an un-upgraded tier would render. The
        # actual "which pouches are installed" game logic is a separate feature; this is just the
        # art-side sanity check that both ends of that range look correct.
        build(tier, include_pouches=False, suffix="_base")
