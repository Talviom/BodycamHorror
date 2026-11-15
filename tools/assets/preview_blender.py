"""Builds the Minecraft JSON block models in Blender and renders preview PNGs.

Run headless (does not touch an open Blender scene):
  blender -b --factory-startup --python tools/assets/preview_blender.py -- <repo root>
Output: docs/previews/<model>.png and docs/previews/all_blocks.png
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

root = Path(sys.argv[sys.argv.index("--") + 1]).resolve()
assets = root / "src/main/resources/assets/bodycamhorror"
out_dir = root / "docs/previews"
out_dir.mkdir(parents=True, exist_ok=True)

EMISSIVE_HINTS = ("tube_on", "light_green", "light_red", "panel_on")


def mc(p):
    """Minecraft model units (x east, y up, z south) -> Blender metres (x east, y north, z up)."""
    return Vector((p[0] / 16 - 0.5, -(p[2] / 16 - 0.5), p[1] / 16))


def face_corners(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    # Order: top-left, top-right, bottom-right, bottom-left as seen from outside (Minecraft UV convention).
    return {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[face]


_materials = {}


def material(tex_path):
    key = str(tex_path)
    if key in _materials:
        return _materials[key]
    m = bpy.data.materials.new(tex_path.stem)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    img = nt.nodes.new("ShaderNodeTexImage")
    img.image = bpy.data.images.load(str(tex_path))
    img.interpolation = "Closest"
    nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.75
    if any(h in tex_path.stem for h in EMISSIVE_HINTS):
        for name in ("Emission Color", "Emission"):
            if name in bsdf.inputs:
                nt.links.new(img.outputs["Color"], bsdf.inputs[name])
                break
        if "Emission Strength" in bsdf.inputs:
            bsdf.inputs["Emission Strength"].default_value = 0.8
    _materials[key] = m
    return m


def build(name, offset=Vector()):
    data = json.loads((assets / "models/block" / f"{name}.json").read_text(encoding="utf-8"))
    tex = {k: assets / "textures/block" / (v.split("/")[-1] + ".png") for k, v in data["textures"].items()}
    verts, faces, uvs, mats = [], [], [], []
    mat_index = {}
    for e in data["elements"]:
        for face, fd in e["faces"].items():
            corners = face_corners(face, e["from"], e["to"])
            u1, v1, u2, v2 = fd["uv"]
            uv = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            # Counter-clockwise from outside for Blender: TL, BL, BR, TR.
            order = [0, 3, 2, 1]
            base = len(verts)
            for i in order:
                verts.append(mc(corners[i]) + offset)
                uvs.append((uv[i][0] / 16, 1 - uv[i][1] / 16))
            faces.append([base, base + 1, base + 2, base + 3])
            tkey = fd["texture"][1:]
            if tkey not in mat_index:
                mat_index[tkey] = len(mat_index)
            mats.append(mat_index[tkey])
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(v) for v in verts], [], faces)
    uv_layer = mesh.uv_layers.new(name="UV")
    for poly in mesh.polygons:
        for li in poly.loop_indices:
            uv_layer.data[li].uv = uvs[mesh.loops[li].vertex_index]
    for tkey, idx in sorted(mat_index.items(), key=lambda kv: kv[1]):
        mesh.materials.append(material(tex[tkey]))
    for poly, mi in zip(mesh.polygons, mats):
        poly.material_index = mi
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def clear():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)


def setup_scene():
    sc = bpy.context.scene
    for eng in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            sc.render.engine = eng
            break
        except TypeError:
            continue
    if sc.render.engine == "CYCLES":
        sc.cycles.samples = 64
    # Dark background, so lit tubes and status lights are visible.
    sc.render.film_transparent = False
    sc.render.resolution_x = 900
    sc.render.resolution_y = 900
    sc.view_settings.view_transform = "Standard"
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
    bg.inputs["Color"].default_value = (0.045, 0.05, 0.055, 1)
    bg.inputs["Strength"].default_value = 1.0
    sc.world = world


def add_lights(target):
    sun = bpy.data.lights.new("key", "SUN")
    sun.energy = 3.0
    so = bpy.data.objects.new("key", sun)
    so.rotation_euler = (math.radians(50), math.radians(10), math.radians(150))
    bpy.context.scene.collection.objects.link(so)
    fill = bpy.data.lights.new("fill", "AREA")
    fill.energy = 60
    fill.size = 2
    fo = bpy.data.objects.new("fill", fill)
    fo.location = target + Vector((-1.5, 1.5, 0.3))
    fo.rotation_euler = (target - fo.location).to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.collection.objects.link(fo)


def camera(target, direction, ortho):
    cam = bpy.data.cameras.new("cam")
    cam.type = "ORTHO"
    cam.ortho_scale = ortho
    co = bpy.data.objects.new("cam", cam)
    co.location = target + direction.normalized() * 6
    co.rotation_euler = (target - co.location).to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.collection.objects.link(co)
    bpy.context.scene.camera = co


def render(path):
    bpy.context.scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    print("rendered", path.name)


# Front faces point north = Blender +Y. View from the north-east, a little above
# (the lamp from below, because it hangs from the ceiling).
VIEWS = {
    "lamp": (Vector((0.9, 1.3, -0.9)), Vector((0, 0, 15 / 16))),
    "breaker": (Vector((0.8, 1.6, 0.7)), Vector((0, -0.4, 0.5))),
    "generator": (Vector((1.1, 1.5, 0.9)), Vector((0, 0, 0.45))),
}

setup_scene()
for name in ("lamp", "lamp_off", "breaker", "breaker_off", "generator", "generator_off"):
    clear()
    build(name)
    direction, target = VIEWS[name.replace("_off", "")]
    add_lights(target)
    camera(target, direction, 1.25)
    render(out_dir / f"{name}.png")

# All six side by side: top row powered, bottom row off.
clear()
row_names = [("lamp", "breaker", "generator"), ("lamp_off", "breaker_off", "generator_off")]
for r, names in enumerate(row_names):
    for c, name in enumerate(names):
        build(name, Vector((c * 1.6, 0, -r * 1.35)))
target = Vector((1.6, 0, -0.2))
add_lights(target)
camera(target, Vector((0.35, 1.6, 0.45)), 5.2)
sc = bpy.context.scene
sc.render.resolution_x = 1500
sc.render.resolution_y = 900
render(out_dir / "all_blocks.png")
