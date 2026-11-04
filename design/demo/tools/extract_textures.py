#!/usr/bin/env python3
"""从五个 GLB 里取出 2048×2048 的基础色与法线原图，供 build_textures.py 缩放。

Blender 无头运行（不要在带 MCP 的实时会话里跑）：

    /Applications/Blender.app/Contents/MacOS/Blender --background --factory-startup \
        --python design/demo/tools/extract_textures.py

输出到 design/demo/work/orig2048/<tier>_base.png 与 <tier>_normal.png（该目录已 gitignore）。

按材质节点的连线找到图像，而不是按图像名取：同一个 Blender 会话里连续导入多个 GLB 时，
图像会被重命名成 Image_0.001 之类，按名字取会取到上一个文件的图。这里每个文件之间
都清空对象和图像，并且仍然只信连线。
"""

from pathlib import Path

import bpy

DEMO = Path(__file__).resolve().parents[1]
OUT = DEMO / "work" / "orig2048"

GLBS = {
    "leather": "皮革背包.glb",
    "iron": "铁背包.glb",
    "gold": "金背包.glb",
    "diamond": "钻石背包.glb",
    "netherite": "下界合金背包.glb",
}


def clear_scene():
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for img in list(bpy.data.images):
        bpy.data.images.remove(img)


def save_image(img, path):
    img.filepath_raw = str(path)
    img.file_format = "PNG"
    img.save()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for tier, glb in GLBS.items():
        clear_scene()
        bpy.ops.import_scene.gltf(filepath=str(DEMO / glb))
        obj = bpy.data.objects["geometry_0"]
        bsdf = next(n for n in obj.data.materials[0].node_tree.nodes if n.type == "BSDF_PRINCIPLED")
        base = bsdf.inputs["Base Color"].links[0].from_node.image
        normal = bsdf.inputs["Normal"].links[0].from_node.inputs["Color"].links[0].from_node.image
        save_image(base, OUT / f"{tier}_base.png")
        save_image(normal, OUT / f"{tier}_normal.png")
        print(f"extracted {tier}: base {tuple(base.size)}, normal {tuple(normal.size)}")


if __name__ == "__main__":
    main()
