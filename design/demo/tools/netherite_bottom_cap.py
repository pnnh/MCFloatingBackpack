import bpy, math, mathutils, bmesh
for o in list(bpy.data.objects): bpy.data.objects.remove(o, do_unlink=True)
bpy.ops.import_scene.gltf(filepath="/Users/Larry/Projects/pnnh/MCFloatingBackpack/design/demo/下界合金背包.glb")
ob=bpy.data.objects["geometry_0"]; bpy.context.view_layer.objects.active=ob; ob.select_set(True)
mat=ob.data.materials[0]
bsdf=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
base=bsdf.inputs["Base Color"].links[0].from_node.image
nm=bsdf.inputs["Normal"].links[0].from_node.inputs["Color"].links[0].from_node.image
for img,suffix in ((base,"base"),(nm,"normal")):
    img.filepath_raw=f"/tmp/tier/netherite_{suffix}.png"; img.file_format="PNG"; img.save()
ob.data.transform(mathutils.Matrix.Rotation(math.radians(-90),4,'Z')); ob.data.update()
# ---- bottom cap ----
me=ob.data; zmin=min(v.co.z for v in me.vertices)
bm=bmesh.new(); bm.from_mesh(me); uvl=bm.loops.layers.uv.active
CAPZ=zmin+0.03; H=0.26          # plate half-size: opening is ~0.23, rim ~0.058 wide, so it overlaps under the rim
corners=[(-H,-H),(-H,H),(H,H),(H,-H)]
vs=[bm.verts.new((x,y,CAPZ)) for x,y in corners]
f=bm.faces.new(vs)
if f.normal.z>0: f.normal_flip()      # must face DOWN so it is visible from below
print("cap normal z:",f.normal.z)
# texture patch (Blender UV, v up): speckled gold-on-dark panel used by the pocket
U0,U1,V0,V1=0.60,0.77,0.36,0.51
for l,(x,y) in zip(f.loops,[(v.co.x,v.co.y) for v in [l.vert for l in f.loops]]):
    l[uvl].uv=(U0+(U1-U0)*(x+H)/(2*H), V0+(V1-V0)*(y+H)/(2*H))
f.material_index=0
bm.to_mesh(me); bm.free(); me.update()
m=ob.modifiers.new("T","TRIANGULATE"); bpy.ops.object.modifier_apply(modifier=m.name)
bpy.ops.wm.obj_export(filepath="/tmp/tier/netherite_backpack.obj", export_selected_objects=True, export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y")
print("exported, zmin",zmin)
