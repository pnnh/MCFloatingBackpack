#!/usr/bin/env python3
"""Run headless (not in the user's live Blender session) to build the leather-prototype
backpack(s) into a brand-new, otherwise-empty .blend file and render stills from it.

Purpose: isolate whether the faint pale/gray vertical stripe seen in earlier renders (done in
the user's live Blender, which has ~80 unrelated pre-existing objects) comes from our own
model/UV data, or from something else already in that scene. This script starts from a
completely empty scene with nothing but the two backpacks, a camera, and a light, so if the
stripe is still there, it's provably ours.

Usage (from a normal shell, NOT inside the live Blender MCP session):
    /Applications/Blender.app/Contents/MacOS/Blender --background --factory-startup \
        --python build_isolated_blend.py
"""

import json
import math
from pathlib import Path

import bpy

HERE = Path(__file__).parent
UNIT = 1.0 / 16.0


def mc_face_for_blender_normal(n):
    x, y, z = round(n.x), round(n.y), round(n.z)
    if x == 1:
        return "east"
    if x == -1:
        return "west"
    if y == 1:
        return "south"
    if y == -1:
        return "north"
    if z == 1:
        return "up"
    if z == -1:
        return "down"
    return None


import os

SKIP_ELEMENTS = set(filter(None, os.environ.get("SKIP_ELEMENTS", "").split(",")))


def build_variant(model_path, texture_path, name_prefix, x_offset, mat_name):
    model = json.loads(model_path.read_text())
    tex_w, tex_h = model["texture_size"]
    model["elements"] = [el for el in model["elements"] if el["name"] not in SKIP_ELEMENTS]

    img = bpy.data.images.load(str(texture_path))
    img.name = f"{name_prefix}_tex"

    mat = bpy.data.materials.new(mat_name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Roughness"].default_value = 0.75
    tex_node = mat.node_tree.nodes.new("ShaderNodeTexImage")
    tex_node.image = img
    tex_node.interpolation = "Closest"
    mat.node_tree.links.new(bsdf.inputs["Base Color"], tex_node.outputs["Color"])

    created = []
    for el in model["elements"]:
        fx, fy, fz = el["from"]
        tx, ty, tz = el["to"]
        bx0, bx1 = (fx - 8) * UNIT + x_offset, (tx - 8) * UNIT + x_offset
        by0, by1 = (fz - 8) * UNIT, (tz - 8) * UNIT
        bz0, bz1 = (fy - 8) * UNIT, (ty - 8) * UNIT
        bpy.ops.mesh.primitive_cube_add(
            size=1, location=((bx0 + bx1) / 2, (by0 + by1) / 2, (bz0 + bz1) / 2)
        )
        cube = bpy.context.active_object
        cube.name = f"{name_prefix}_{el['name']}"
        cube.scale = (bx1 - bx0, by1 - by0, bz1 - bz0)
        cube.data.materials.append(mat)
        created.append(cube)

        mesh = cube.data
        mesh.uv_layers.new(name="UVMap")
        uv_layer = mesh.uv_layers.active.data
        for poly in mesh.polygons:
            face_name = mc_face_for_blender_normal(poly.normal)
            uv = el["faces"].get(face_name, {}).get("uv") if face_name else None
            if not uv:
                continue
            u0, v0, u1, v1 = uv
            corners_uv = [
                (u0 / tex_w, 1 - v0 / tex_h), (u1 / tex_w, 1 - v0 / tex_h),
                (u1 / tex_w, 1 - v1 / tex_h), (u0 / tex_w, 1 - v1 / tex_h),
            ]
            for i, li in enumerate(poly.loop_indices):
                uv_layer[li].uv = corners_uv[i % 4]
    return created


def main():
    # Start from a totally clean scene -- not the factory-startup default cube/camera/light.
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for coll in list(bpy.data.collections):
        bpy.data.collections.remove(coll)

    full = build_variant(
        HERE / "leather_prototype.json", HERE / "leather_prototype_texture.png",
        "full", 0.0, "isolated_full_mat",
    )
    base = build_variant(
        HERE / "leather_prototype_base.json", HERE / "leather_prototype_base_texture.png",
        "base", 0.9, "isolated_base_mat",
    )

    # Target empty at the midpoint between the two backpacks, used to aim the camera and light.
    bpy.ops.object.empty_add(location=(0.45, 0.07, -0.06))
    target = bpy.context.active_object
    target.name = "look_target"

    bpy.ops.object.light_add(type="SUN", location=(2, -3, 3))
    sun = bpy.context.active_object
    sun.data.energy = 3.0
    if os.environ.get("NO_SHADOWS") == "1":
        sun.data.use_shadow = False
    con = sun.constraints.new("TRACK_TO")
    con.target = target
    con.track_axis = "TRACK_NEGATIVE_Z"
    con.up_axis = "UP_Y"

    bpy.ops.object.light_add(type="AREA", location=(-1.5, -2, 1.5))
    fill = bpy.context.active_object
    fill.data.energy = 40.0
    fill.data.size = 1.5
    if os.environ.get("NO_SHADOWS") == "1":
        fill.data.use_shadow = False
    con = fill.constraints.new("TRACK_TO")
    con.target = target
    con.track_axis = "TRACK_NEGATIVE_Z"
    con.up_axis = "UP_Y"

    def add_camera(name, location):
        bpy.ops.object.camera_add(location=location)
        cam = bpy.context.active_object
        cam.name = name
        con = cam.constraints.new("TRACK_TO")
        con.target = target
        con.track_axis = "TRACK_NEGATIVE_Z"
        con.up_axis = "UP_Y"
        return cam

    cam_front = add_camera("cam_front", (0.45, -2.2, 0.35))
    cam_back = add_camera("cam_back", (0.45, 2.2, 0.35))

    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in [
        e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items
    ] else "BLENDER_EEVEE"
    scene.render.resolution_x = 1000
    scene.render.resolution_y = 700
    scene.render.film_transparent = False
    scene.world = bpy.data.worlds.new("World")
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[0].default_value = (0.5, 0.52, 0.55, 1.0)

    out_prefix = os.environ.get("OUT_PREFIX", "isolated")
    for cam, out_name in ((cam_front, f"{out_prefix}_front.png"), (cam_back, f"{out_prefix}_back.png")):
        scene.camera = cam
        scene.render.filepath = str(HERE / out_name)
        bpy.ops.render.render(write_still=True)
        print(f"Rendered {out_name}")

    if os.environ.get("SAVE_BLEND") == "1":
        blend_path = HERE / "backpack_prototype.blend"
        bpy.ops.wm.save_as_mainfile(filepath=str(blend_path))
        print(f"Saved {blend_path}")


if __name__ == "__main__":
    main()
