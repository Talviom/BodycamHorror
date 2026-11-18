"""Превью монстра «Жилец» в Blender из tenant.geo.json и tenant.png (как его соберёт GeckoLib).

    D:\\Blender\\blender.exe -b --factory-startup --python tools/assets/preview_tenant.py -- <repo root>

Пишет docs/previews/tenant_front.png, tenant_three_quarter.png, tenant_side.png, tenant_dark.png.
Рядом стоит серый манекен ростом с игрока (2 блока) для масштаба.
Повороты костей как в GeckoLib: X и Y с обратным знаком, порядок Z, Y, X; перед модели = -z.
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

root = Path(sys.argv[sys.argv.index("--") + 1]).resolve()
assets = root / "src/main/resources/assets/bodycamhorror"
out = root / "docs/previews"
out.mkdir(parents=True, exist_ok=True)
geo = json.loads((assets / "geo/tenant.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
TW = geo["description"]["texture_width"]
TH = geo["description"]["texture_height"]


def to_blender(p):
    """Пиксели модели (x, y вверх, z на юг) -> метры Blender (x, y на север, z вверх)."""
    return Vector((p[0] / 16, -p[2] / 16, p[1] / 16))


def bone_matrix(b):
    px, py, pz = b["pivot"]
    rx, ry, rz = b.get("rotation", [0, 0, 0])
    r = (Matrix.Rotation(math.radians(rz), 4, "Z") @ Matrix.Rotation(math.radians(-ry), 4, "Y")
         @ Matrix.Rotation(math.radians(-rx), 4, "X"))
    return Matrix.Translation((px, py, pz)) @ r @ Matrix.Translation((-px, -py, -pz))


def face_rects(uv, size):
    u, v = uv
    w, h, d = (int(round(x)) for x in size)
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


def face_corners(f, x1, y1, z1, x2, y2, z2):
    # Верх-лево, верх-право, низ-право, низ-лево как на текстуре.
    return {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "up": [(x2, y2, z2), (x1, y2, z2), (x1, y2, z1), (x2, y2, z1)],
        "down": [(x2, y1, z1), (x1, y1, z1), (x1, y1, z2), (x2, y1, z2)],
    }[f]


OUTWARD = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def build_model(name, offset_x=0.0):
    bones = {b["name"]: b for b in geo["bones"]}
    world = {}

    def wm(n):
        if n not in world:
            b = bones[n]
            parent = wm(b["parent"]) if "parent" in b else Matrix.Identity(4)
            world[n] = parent @ bone_matrix(b)
        return world[n]

    verts, faces, uvs = [], [], []
    for b in geo["bones"]:
        m = wm(b["name"])
        for c in b.get("cubes", []):
            (ox, oy, oz), (sw, sh, sd) = c["origin"], c["size"]
            x1, y1, z1, x2, y2, z2 = ox, oy, oz, ox + sw, oy + sh, oz + sd
            for f, (rx, ry, rw, rh) in face_rects(c["uv"], c["size"]).items():
                if rw == 0 or rh == 0:
                    continue
                corners = [m @ Vector(p) for p in face_corners(f, x1, y1, z1, x2, y2, z2)]
                quad_uv = [(rx / TW, 1 - ry / TH), ((rx + rw) / TW, 1 - ry / TH),
                           ((rx + rw) / TW, 1 - (ry + rh) / TH), (rx / TW, 1 - (ry + rh) / TH)]
                normal = (corners[1] - corners[0]).cross(corners[2] - corners[0])
                outward = m.to_3x3() @ Vector(OUTWARD[f])
                if normal.dot(outward) < 0:
                    corners.reverse()
                    quad_uv.reverse()
                base = len(verts)
                verts += [to_blender(p) + Vector((offset_x, 0, 0)) for p in corners]
                faces.append([base, base + 1, base + 2, base + 3])
                uvs += quad_uv
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    uv_layer = mesh.uv_layers.new(name="UVMap")
    for i, loop in enumerate(mesh.loops):
        uv_layer.data[i].uv = uvs[loop.vertex_index]
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)

    mat = bpy.data.materials.new(name + "_mat")
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(str(assets / "textures/entity/tenant.png"))
    tex.interpolation = "Closest"
    mat.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    mat.node_tree.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    bsdf.inputs["Roughness"].default_value = 0.9
    obj.data.materials.append(mat)
    return obj


def box(name, lo, hi, color):
    bpy.ops.mesh.primitive_cube_add()
    o = bpy.context.active_object
    o.name = name
    lo, hi = Vector(lo), Vector(hi)
    o.location = (lo + hi) / 2
    o.scale = (hi - lo) / 2
    mat = bpy.data.materials.new(name + "_mat")
    mat.use_nodes = True
    bsdf = next(n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = color
    bsdf.inputs["Roughness"].default_value = 0.95
    o.data.materials.append(mat)
    return o


def mannequin(x):
    """Игрок 2 блока ростом: ноги 12 px, тело 12, голова 8 (как в Minecraft)."""
    g = (0.32, 0.31, 0.29, 1)
    box("m_legs", (x - 0.25, -0.125, 0), (x + 0.25, 0.125, 0.75), g)
    box("m_body", (x - 0.25, -0.125, 0.75), (x + 0.25, 0.125, 1.5), g)
    box("m_arm_r", (x - 0.5, -0.125, 0.75), (x - 0.25, 0.125, 1.5), g)
    box("m_arm_l", (x + 0.25, -0.125, 0.75), (x + 0.5, 0.125, 1.5), g)
    box("m_head", (x - 0.25, -0.25, 1.5), (x + 0.25, 0.25, 2.0), g)


bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
try:
    scene.render.engine = "CYCLES"
except TypeError as e:
    print("engine:", e)
scene.cycles.samples = 48
scene.cycles.use_denoising = True
scene.render.resolution_x = 720
scene.render.resolution_y = 900
scene.view_settings.view_transform = "Standard"

world = bpy.data.worlds.new("w")
scene.world = world
world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs["Color"].default_value = (0.012, 0.011, 0.010, 1)

tenant = build_model("tenant")
mannequin(1.4)
floor = box("floor", (-6, -6, -0.05), (6, 6, 0), (0.05, 0.048, 0.045, 1))
wall = box("wall", (-6, -3.1, 0), (6, -3.0, 9), (0.06, 0.057, 0.052, 1))

key = bpy.data.lights.new("key", "AREA")
key.energy = 260
key.size = 3
key_o = bpy.data.objects.new("key", key)
key_o.location = (-2.5, 4, 4)
key_o.rotation_euler = (Vector((0.3, 0, 1.3)) - key_o.location).to_track_quat("-Z", "Y").to_euler()
bpy.context.collection.objects.link(key_o)
fill = bpy.data.lights.new("fill", "AREA")
fill.energy = 60
fill.size = 4
fill_o = bpy.data.objects.new("fill", fill)
fill_o.location = (3, 2, 2)
fill_o.rotation_euler = (Vector((0.3, 0, 1.2)) - fill_o.location).to_track_quat("-Z", "Y").to_euler()
bpy.context.collection.objects.link(fill_o)

cam_data = bpy.data.cameras.new("cam")
cam_data.lens = 50
cam = bpy.data.objects.new("cam", cam_data)
bpy.context.collection.objects.link(cam)
scene.camera = cam


def look(at):
    d = Vector(at) - cam.location
    cam.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()


def shot(name, loc, at=(0.6, 0, 1.25)):
    cam.location = loc
    look(at)
    scene.render.filepath = str(out / f"{name}.png")
    bpy.ops.render.render(write_still=True)
    print("rendered", name)


shot("tenant_front", (0.6, 7.2, 1.5))
shot("tenant_three_quarter", (-4.6, 5.4, 1.9))
shot("tenant_side", (-7.2, 0.4, 1.4))

# Как его увидит игрок: темнота, только луч фонарика от плеча камеры.
key.energy = 0
fill.energy = 0
spot = bpy.data.lights.new("flashlight", "SPOT")
spot.energy = 900
spot.spot_size = math.radians(28)
spot.spot_blend = 0.6
spot.color = (1.0, 0.86, 0.66)
spot_o = bpy.data.objects.new("flashlight", spot)
spot_o.location = (0.35, 5.0, 1.3)
bpy.context.collection.objects.link(spot_o)
d = Vector((0.1, 0, 1.6)) - spot_o.location
spot_o.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
bpy.data.objects.remove(bpy.data.objects["m_legs"])
for n in ("m_body", "m_arm_r", "m_arm_l", "m_head"):
    bpy.data.objects.remove(bpy.data.objects[n])
shot("tenant_dark", (0.1, 5.4, 1.62), at=(0.0, 0, 1.75))
