"""3D preview of the player skins in Blender (headless).

  blender -b --factory-startup --python tools/assets/preview_skins.py -- <repo root>
Renders docs/previews/skins_preview.png: each skin (alpha, bravo, neutral) from the front and the back,
classic 64x64 layout with the overlay layers slightly inflated, in dim moody light.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

root = Path(sys.argv[sys.argv.index("--") + 1]).resolve()
skin_dir = root / "src/main/resources/assets/bodycamhorror/textures/entity/skin"
out = root / "docs/previews/skins_preview.png"

# (name, base uv, overlay uv, size w,h,d in px, min corner x,z,y in px) ; x right(+X = player's left), z up, y depth (front = -Y)
PARTS = [
    ("head", (0, 0), (32, 0), (8, 8, 8), (-4, 24, -4), 0.5),
    ("body", (16, 16), (16, 32), (8, 12, 4), (-4, 12, -2), 0.25),
    ("rarm", (40, 16), (40, 32), (4, 12, 4), (-8, 12, -2), 0.25),
    ("larm", (32, 48), (48, 48), (4, 12, 4), (4, 12, -2), 0.25),
    ("rleg", (0, 16), (0, 32), (4, 12, 4), (-4, 0, -2), 0.25),
    ("lleg", (16, 48), (0, 48), (4, 12, 4), (0, 0, -2), 0.25),
]


def face_rects(u, v, w, h, d):
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def box_faces(x0, x1, y0, y1, z0, z1):
    # Corners TL, TR, BR, BL as seen from outside, matching Minecraft's texture orientation.
    return {
        "front": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],   # -Y, viewer's right = +X
        "back": [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],    # +Y, viewer's right = -X
        "right": [(x0, y1, z1), (x0, y0, z1), (x0, y0, z0), (x0, y1, z0)],   # -X, x grows toward front
        "left": [(x1, y0, z1), (x1, y1, z1), (x1, y1, z0), (x1, y0, z0)],    # +X, x grows toward back
        "top": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],     # back edge at the top
        "bottom": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
    }


def material(path):
    m = bpy.data.materials.new(path.stem)
    m.use_nodes = True
    for attr, val in (("blend_method", "CLIP"), ("surface_render_method", "DITHERED")):
        try:
            setattr(m, attr, val)
        except (AttributeError, TypeError):
            pass
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    out = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL")
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(str(path))
    tex.interpolation = "Closest"
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.9
    # Overlay pixels with alpha 0 must vanish: mix a transparent shader in by the texture alpha.
    transp = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(tex.outputs["Alpha"], mix.inputs["Fac"])
    nt.links.new(transp.outputs["BSDF"], mix.inputs[1])
    nt.links.new(bsdf.outputs["BSDF"], mix.inputs[2])
    nt.links.new(mix.outputs["Shader"], out.inputs["Surface"])
    return m


def build(skin_path, offset, yaw):
    mat = material(skin_path)
    verts, faces, uvs = [], [], []
    for _, buv, ouv, (w, h, d), (mx, mz, my), infl in PARTS:
        for layer, (u, v), grow in ((0, buv, 0.0), (1, ouv, infl)):
            x0, x1 = mx - grow, mx + w + grow
            z0, z1 = mz - grow, mz + h + grow
            y0, y1 = my - grow, my + d + grow
            rects = face_rects(u, v, w, h, d)
            for name, corners in box_faces(x0, x1, y0, y1, z0, z1).items():
                rx0, ry0, rx1, ry1 = rects[name]
                uvc = [(rx0, ry0), (rx1, ry0), (rx1, ry1), (rx0, ry1)]
                base = len(verts)
                for i in (0, 3, 2, 1):  # counter-clockwise from outside
                    cx, cy, cz = corners[i]
                    verts.append((cx / 16, cy / 16, cz / 16))
                    uvs.append((uvc[i][0] / 64, 1 - uvc[i][1] / 64))
                faces.append([base, base + 1, base + 2, base + 3])
    mesh = bpy.data.meshes.new(skin_path.stem)
    mesh.from_pydata(verts, [], faces)
    layer = mesh.uv_layers.new(name="UV")
    for poly in mesh.polygons:
        for li in poly.loop_indices:
            layer.data[li].uv = uvs[mesh.loops[li].vertex_index]
    mesh.materials.append(mat)
    obj = bpy.data.objects.new(skin_path.stem, mesh)
    obj.location = offset
    obj.rotation_euler = (0, 0, math.radians(yaw))
    bpy.context.scene.collection.objects.link(obj)


for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
sc = bpy.context.scene
for eng in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
    try:
        sc.render.engine = eng
        break
    except TypeError:
        continue
sc.render.resolution_x, sc.render.resolution_y = 1600, 900
sc.view_settings.view_transform = "Standard"
world = bpy.data.worlds.new("w")
world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs["Color"].default_value = (0.035, 0.034, 0.032, 1)
bg.inputs["Strength"].default_value = 1.0
sc.world = world

x = -3.6
for team in ("alpha", "bravo", "neutral"):
    p = skin_dir / f"{team}.png"
    build(p, Vector((x, 0, 0)), 0)            # front
    build(p, Vector((x + 1.25, 0, 0)), 180)   # back
    x += 2.9

key = bpy.data.lights.new("key", "AREA")
key.energy = 900
key.size = 3
ko = bpy.data.objects.new("key", key)
ko.location = (2.0, -5.0, 4.0)
ko.rotation_euler = (Vector((0, 0, 1)) - ko.location).to_track_quat("-Z", "Y").to_euler()
sc.collection.objects.link(ko)
fill = bpy.data.lights.new("fill", "AREA")
fill.energy = 150
fill.size = 4
fo = bpy.data.objects.new("fill", fill)
fo.location = (-4.0, -4.0, 1.5)
fo.rotation_euler = (Vector((0, 0, 1)) - fo.location).to_track_quat("-Z", "Y").to_euler()
sc.collection.objects.link(fo)

cam = bpy.data.cameras.new("cam")
cam.type = "ORTHO"
cam.ortho_scale = 8.4
co = bpy.data.objects.new("cam", cam)
co.location = (0.0, -10.0, 1.6)
co.rotation_euler = (math.radians(87), 0, 0)
sc.collection.objects.link(co)
sc.camera = co
sc.render.filepath = str(out)
bpy.ops.render.render(write_still=True)
print("rendered", out)
