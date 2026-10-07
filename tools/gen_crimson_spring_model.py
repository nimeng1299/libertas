#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 赤泉 (crimsonSpring) model artifacts.

Twin of the 灵泉 (spiritSpring): same generation scheme, blood-magic palette.
Single source of truth for the spring's cubes; regenerates three files so the
runtime model, the texture atlas and the Blockbench project can never drift:

  1. src/main/resources/assets/libertas/models/block/crimsonSpring.json
     1.8-style model JSON consumed by neuvillette.libertas.client.render
     .JsonItemRenderer (item form) and .JsonBlockRenderer (in-world ISBRH).
     UV space is 0..16 (the renderers normalize by 16); the texture itself is 64x64.
  2. src/main/resources/assets/libertas/textures/blocks/crimson_spring.png
     64x64 blood-material atlas painted to exactly match the packed UV rects.
  3. blockbench/crimsonSpring.bbmodel
     Blockbench project (free format, per-face UV, embedded texture) for visual editing.

Faces sharing material + dimensions share one UV rect. Rects are shelf-packed
into the 16x16 uv space, painted at 4 px per uv unit.

The spring is meant to sit directly below a Blood Magic altar (see
BlockCrimsonSpring): a dark basalt basin filled with glowing crimson liquid,
a blood-drop crystal streaming upward from the centre and two drifting embers.

Regenerate:  python tools/gen_crimson_spring_model.py
"""

import base64
import io
import json
import math
import os
import random
import uuid

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JSON_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/models/block/crimsonSpring.json")
TEX_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/blocks/crimson_spring.png")
BB_OUT = os.path.join(ROOT, "blockbench/crimsonSpring.bbmodel")
TEX_REF = "libertas:blocks/crimson_spring"

TEX_PX = 64  # texture resolution (square)
UV_SPACE = 16.0  # uv units available in each axis (renderers divide by 16)
PX_PER_UV = TEX_PX / UV_SPACE
SNAP = 0.25  # uv coordinate granularity (1 px)
GUTTER = 0.25  # gap between packed rects

# Palette (RGB) - blood-magic tones: dark basalt ceramic, luminous crimson
# blood, a rising blood-drop crystal and drifting embers.
BASALT = (54, 48, 58)
BASALT_HI = (82, 74, 88)
BASALT_LO = (38, 33, 42)
BASALT_RIM = (26, 22, 30)
BLOOD_DEEP = (96, 8, 18)
BLOOD = (158, 20, 32)
BLOOD_HI = (208, 44, 56)
BLOOD_SPARK = (255, 120, 120)
BLOOD_BRIGHT = (236, 70, 84)
BLOOD_GLOW = (255, 160, 150)
GEM_HI = (255, 200, 200)
GEM = (232, 90, 100)
GEM_LO = (186, 40, 56)
GEM_RIM = (140, 18, 34)
EMBER = (255, 178, 128)
EMBER_EDGE = (214, 90, 60)
BG = (14, 8, 12)


def rot_y45(origin_y, origin_x=8.0, origin_z=8.0):
    return {"angle": 45, "axis": "y", "origin": [origin_x, origin_y, origin_z]}


# (name, from[x,y,z], to[x,y,z], rotation|None, material)
# A spring to feed a Blood Magic altar from below: square basalt basin with
# recessed glowing blood, an upwelling in the middle, two rune plinths, one
# rising three-tier blood-drop crystal and two drifting embers. Kept inside
# one block cell (x/z 2..14, y 0..9.5) so it never pokes into neighbouring
# blocks. The blood is split into 4 quadrant cubes so all of them share one
# small 4x4 ripple rect in the UV atlas instead of a full 8x8 one.
CUBES = [
    ("rim_n", (2.0, 0.0, 2.0), (14.0, 3.0, 4.0), None, "basin"),
    ("rim_s", (2.0, 0.0, 12.0), (14.0, 3.0, 14.0), None, "basin"),
    ("rim_w", (2.0, 0.0, 4.0), (4.0, 3.0, 12.0), None, "basin"),
    ("rim_e", (12.0, 0.0, 4.0), (14.0, 3.0, 12.0), None, "basin"),
    ("floor", (4.0, 0.0, 4.0), (12.0, 1.0, 12.0), None, "basin"),
    ("water_a", (4.0, 1.0, 4.0), (8.0, 2.0, 8.0), None, "blood"),
    ("water_b", (8.0, 1.0, 4.0), (12.0, 2.0, 8.0), None, "blood"),
    ("water_c", (4.0, 1.0, 8.0), (8.0, 2.0, 12.0), None, "blood"),
    ("water_d", (8.0, 1.0, 8.0), (12.0, 2.0, 12.0), None, "blood"),
    ("upwell", (6.75, 1.25, 6.75), (9.25, 2.75, 9.25), None, "blood_bright"),
    ("pad_e", (9.25, 2.0, 4.75), (11.5, 2.25, 7.0), None, "stone"),
    ("pad_w", (4.5, 2.0, 9.5), (6.75, 2.25, 11.75), None, "stone"),
    ("drop", (6.75, 4.5, 6.75), (9.25, 6.75, 9.25), rot_y45(5.625), "gem"),
    ("drop_mid", (7.1, 6.75, 7.1), (8.9, 8.25, 8.9), rot_y45(7.5), "gem_bright"),
    ("drop_tip", (7.55, 8.25, 7.55), (8.45, 9.5, 8.45), rot_y45(8.875), "gem_bright"),
    ("wisp_e", (10.9, 6.75, 7.8), (11.65, 7.5, 8.55), None, "ember"),
    ("wisp_w", (4.35, 8.0, 7.45), (5.1, 8.75, 8.2), None, "ember"),
]

# Faces that can never be seen and are skipped to keep the UV atlas small:
# the pool floor is fully covered by blood, the upwelling's bottom is buried in
# the blood volume, and the blood quadrants touch each other / the basin floor.
SKIP_FACES = {
    ("floor", "up"),
    ("floor", "down"),
    ("upwell", "down"),
    ("water_a", "east"), ("water_b", "west"),  # seam between quadrants (x=8)
    ("water_c", "east"), ("water_d", "west"),
    ("water_a", "south"), ("water_c", "north"),  # seam between quadrants (z=8)
    ("water_b", "south"), ("water_d", "north"),
    ("water_a", "down"), ("water_b", "down"), ("water_c", "down"), ("water_d", "down"),
}

SIDES = ("north", "east", "south", "west")


def snap(v):
    """Round a uv dimension UP to the coordinate granularity, minimum 1 px."""
    return max(SNAP, math.ceil(v / SNAP - 1e-6) * SNAP)


def face_dims(frm, to, face):
    if face in ("north", "south"):
        return to[0] - frm[0], to[1] - frm[1]
    if face in ("east", "west"):
        return to[2] - frm[2], to[1] - frm[1]
    return to[0] - frm[0], to[2] - frm[2]  # up / down


def material_for(face, material):
    if face in ("up", "down"):
        return material + "_cap"
    return material


def pack_rects():
    """Guillotine-pack (best-short-side-fit) one rect per unique (material, kind, w, h);
    returns {key: (u, v, w, h)}."""
    keys = set()
    for name, frm, to, _rot, mat in CUBES:
        for face in ("north", "east", "south", "west", "up", "down"):
            if (name, face) in SKIP_FACES:
                continue
            w, h = face_dims(frm, to, face)
            keys.add((material_for(face, mat), round(snap(w), 4), round(snap(h), 4)))

    placed = {}
    free = [(0.0, 0.0, UV_SPACE, UV_SPACE)]
    for mat, w, h in sorted(keys, key=lambda k: -(k[1] * k[2])):
        key = (mat, w, h)
        # pack the rect plus a gutter so neighbouring rects never share a texel row/col
        we, he = w + GUTTER, h + GUTTER
        best = None
        for fx, fy, fw, fh in free:
            if we <= fw + 1e-6 and he <= fh + 1e-6:
                # best-short-side-fit: prefer the free rect that hugs the placed one
                waste = (min(fw - we, fh - he), max(fw - we, fh - he))
                if best is None or waste < best[0]:
                    best = (waste, fx, fy)
        if best is None:
            raise RuntimeError("UV atlas overflow: no room for %s (%.2f x %.2f)" % (key, w, h))
        _, x, y = best
        placed[key] = (x, y, w, h)

        # Subtract the placed rect (incl. gutter) from every overlapping free rect. A free
        # rect that stays untouched remains valid; redundant (contained) leftovers are
        # harmless because every future placement subtracts itself everywhere.
        nxt = []
        for fx, fy, fw, fh in free:
            if fx >= x + we or fx + fw <= x or fy >= y + he or fy + fh <= y:
                nxt.append((fx, fy, fw, fh))
                continue
            if fx < x:
                nxt.append((fx, fy, x - fx, fh))
            if x + we < fx + fw:
                nxt.append((x + we, fy, fx + fw - x - we, fh))
            if fy < y:
                nxt.append((fx, fy, fw, y - fy))
            if y + he < fy + fh:
                nxt.append((fx, y + he, fw, fy + fh - y - he))
        free = [r for r in nxt if r[2] > 1e-6 and r[3] > 1e-6]
    return placed


def clamp(c):
    return max(0, min(255, int(round(c))))


def jitter(rng, base, amount):
    return tuple(clamp(ch + rng.randint(-amount, amount)) for ch in base)


def paint_basin(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            band = (py - y0) % 4  # faint horizontal basalt bands
            base = BASALT if band else BASALT_LO
            d.point((px, py), fill=jitter(rng, base, 5))
    d.rectangle([x0, y0, x0 + w - 1, y0], fill=BASALT_HI)  # top highlight
    d.rectangle([x0, y0 + h - 1, x0 + w - 1, y0 + h - 1], fill=BASALT_LO)
    for _ in range(max(1, w * h // 8)):  # speckles
        d.point((rng.randrange(x0, x0 + w), rng.randrange(y0, y0 + h)), fill=jitter(rng, BASALT_HI, 8))
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=BASALT_RIM)


def paint_basin_cap(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            d.point((px, py), fill=jitter(rng, BASALT_HI, 4))
    if w >= 4 and h >= 4:  # inner rim shading for large tops
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=BASALT_RIM)
        d.rectangle([x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2], outline=BASALT_LO)


def paint_blood(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = (py - y0) / max(1.0, h - 1)  # 0 top .. 1 bottom
            base = BLOOD_HI if t < 0.3 else (BLOOD if t < 0.75 else BLOOD_DEEP)
            if (px + 2 * py) % 5 == 0:
                base = jitter(rng, base, 10)  # gentle ripple streaks
            d.point((px, py), fill=base)
    for _ in range(max(1, w // 3)):  # sparkles
        d.point((rng.randrange(x0, x0 + w), rng.randrange(y0, y0 + h)), fill=BLOOD_SPARK)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=BLOOD_DEEP)


def paint_blood_cap(d, x0, y0, w, h, rng):
    cx, cy = x0 + (w - 1) / 2.0, y0 + (h - 1) / 2.0
    max_r = math.hypot(w / 2.0, h / 2.0)
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            r = math.hypot(px - cx, py - cy) / max(1.0, max_r)
            base = BLOOD_HI if r < 0.35 else (BLOOD if r < 0.8 else BLOOD_DEEP)
            if abs((r * 3.0) % 1.0) < 0.12:  # concentric ripples
                base = BLOOD_SPARK
            d.point((px, py), fill=jitter(rng, base, 6))
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=BLOOD_DEEP)


def paint_blood_bright(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = (py - y0) / max(1.0, h - 1)
            base = BLOOD_GLOW if t < 0.5 else BLOOD_BRIGHT
            d.point((px, py), fill=jitter(rng, base, 6))
    d.point((x0 + w // 2, y0), fill=BLOOD_GLOW)  # small crest highlight
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=BLOOD_HI)


def paint_stone(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            base = BASALT_LO
            d.point((px, py), fill=jitter(rng, base, 6))
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=BASALT_RIM)
    if w >= 3 and h >= 3:  # a faint rune notch across the plinth top
        d.line([x0 + 1, y0 + h // 2, x0 + w - 2, y0 + h // 2], fill=BLOOD)


def paint_ember(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            d.point((px, py), fill=jitter(rng, EMBER, 6))
    if w >= 3 and h >= 3:
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=EMBER_EDGE)


def paint_gem(d, x0, y0, w, h, rng):
    stops = [(0.0, GEM_HI), (0.3, GEM), (0.7, GEM_LO), (1.0, GEM_RIM)]
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = (py - y0) / max(1.0, h - 1)  # 0 top .. 1 bottom
            base = GEM_LO
            for stop, col in stops:
                if t <= stop + 1e-6:
                    base = col
                    break
            if px % 2 == 0:
                base = jitter(rng, base, 10)  # vertical streaks
            d.point((px, py), fill=base)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GEM_RIM)


def paint_gem_bright(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = (py - y0) / max(1.0, h - 1)
            base = BLOOD_GLOW if t > 0.5 else GEM_HI
            d.point((px, py), fill=jitter(rng, base, 6))
    for _ in range(2):
        d.point((rng.randrange(x0, x0 + w), rng.randrange(y0, y0 + h)), fill=BLOOD_GLOW)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GEM)


PAINTERS = {
    "basin": paint_basin,
    "blood": paint_blood,
    "blood_bright": paint_blood_bright,
    "stone": paint_stone,
    "gem": paint_gem,
    "gem_bright": paint_gem_bright,
    "ember": paint_ember,
}

CAP_FLATS = {
    "basin": BASALT_HI,
    "blood": BLOOD_HI,
    "blood_bright": BLOOD_GLOW,
    "stone": BASALT_LO,
    "gem": GEM_HI,
    "gem_bright": BLOOD_GLOW,
    "ember": EMBER,
}


def paint_cap(d, x0, y0, w, h, rng, material):
    if w < 3 or h < 3:  # tiny faces read better as flat tone
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=CAP_FLATS[material])
        return
    if material == "basin":
        paint_basin_cap(d, x0, y0, w, h, rng)
    elif material == "blood":
        paint_blood_cap(d, x0, y0, w, h, rng)
    elif material == "blood_bright":
        paint_blood_bright(d, x0, y0, w, h, rng)
    elif material == "stone":
        paint_stone(d, x0, y0, w, h, rng)
    elif material == "gem":
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=GEM_HI)
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GEM)
    elif material == "gem_bright":
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=BLOOD_GLOW)
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GEM)
    else:  # ember
        paint_ember(d, x0, y0, w, h, rng)


def build_texture(placed):
    img = Image.new("RGBA", (TEX_PX, TEX_PX), BG + (255,))
    d = ImageDraw.Draw(img)
    for (material, w, h), (u, v, rw, rh) in placed.items():
        rng = random.Random("%s|%.2f|%.2f" % (material, w, h))  # deterministic per-rect noise
        x0, y0 = int(round(u * PX_PER_UV)), int(round(v * PX_PER_UV))
        pw, ph = int(round(rw * PX_PER_UV)), int(round(rh * PX_PER_UV))
        if material.endswith("_cap"):
            paint_cap(d, x0, y0, pw, ph, rng, material[:-4])
        else:
            PAINTERS[material](d, x0, y0, pw, ph, rng)
    return img


def uv_rect(placed, face, frm, to, mat):
    w, h = face_dims(frm, to, face)
    key = (material_for(face, mat), round(snap(w), 4), round(snap(h), 4))
    if key not in placed:
        raise RuntimeError("unpacked rect %s" % (key,))
    u, v, rw, rh = placed[key]
    return [round(u, 3), round(v, 3), round(u + rw, 3), round(v + rh, 3)]


def build_faces(placed, name, frm, to, mat):
    return {face: {"uv": uv_rect(placed, face, frm, to, mat), "texture": "#0"}
            for face in ("north", "east", "south", "west", "up", "down")
            if (name, face) not in SKIP_FACES}


NS = uuid.UUID("7c2e5a91-4b3d-4f68-9a2c-1d5e8f0a3b6d")


def det_uuid(name):
    return str(uuid.uuid5(NS, name))


def main():
    placed = pack_rects()
    img = build_texture(placed)

    buf = io.BytesIO()
    img.save(buf, "PNG")
    png_bytes = buf.getvalue()

    os.makedirs(os.path.dirname(JSON_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(TEX_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(BB_OUT), exist_ok=True)
    img.save(TEX_OUT)

    elements_json, elements_bb, children = [], [], []
    for name, frm, to, rot, mat in CUBES:
        faces = build_faces(placed, name, frm, to, mat)
        el = {"name": name, "from": list(frm), "to": list(to)}
        if rot:
            el["rotation"] = rot
        el["faces"] = faces
        elements_json.append(el)

        bb_el = {
            "name": name,
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "render_order": "default",
            "allow_mirror_modeling": True,
            "from": list(frm),
            "to": list(to),
            "autouv": 0,
            "color": 7,
            "origin": rot["origin"] if rot else [8.0, 8.0, 8.0],
            "faces": {f: {"uv": spec["uv"], "texture": 0} for f, spec in faces.items()},
            "type": "cube",
            "uuid": det_uuid(name),
        }
        # Blockbench's own parser requires all six face keys; hidden faces get a
        # null texture placeholder so they stay present but untextured.
        for f in ("north", "east", "south", "west", "up", "down"):
            if f not in bb_el["faces"]:
                bb_el["faces"][f] = {"uv": [0.0, 0.0, 0.0, 0.0], "texture": None}
        if rot:
            # the free format stores rotations as an euler vector + origin (the runtime
            # model JSON keeps the java-block {angle, axis, origin} form instead)
            axis_index = {"x": 0, "y": 1, "z": 2}[rot["axis"]]
            bb_el["rotation"] = [0.0, 0.0, 0.0]
            bb_el["rotation"][axis_index] = rot["angle"]
        elements_bb.append(bb_el)
        children.append(det_uuid(name))

    model = {
        "credit": "Generated by tools/gen_crimson_spring_model.py - edit blockbench/crimsonSpring.bbmodel instead",
        "textures": {"0": TEX_REF, "particle": TEX_REF},
        "elements": elements_json,
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(model, f, indent="\t", ensure_ascii=False)

    bbmodel = {
        "meta": {"format_version": "4.5", "box_uv": False},
        "name": "crimsonSpring",
        "model_identifier": "",
        "visible_box": [1.0, 1.0, 0.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": 16, "height": 16},
        "elements": elements_bb,
        "outliner": [{
            "name": "crimsonSpring",
            "origin": [8.0, 8.0, 8.0],
            "color": 7,
            "uuid": det_uuid("group"),
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": children,
        }],
        "textures": [{
            "path": os.path.relpath(TEX_OUT, os.path.dirname(BB_OUT)).replace("\\", "/"),
            "name": "crimson_spring.png",
            "folder": "",
            "namespace": "",
            "id": "0",
            "group": "",
            "width": TEX_PX,
            "height": TEX_PX,
            "uv_width": 16,
            "uv_height": 16,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "default",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "internal": True,
            "saved": False,
            "uuid": det_uuid("texture"),
            "relative_path": "",
            "source": "data:image/png;base64," + base64.b64encode(png_bytes).decode("ascii"),
        }],
    }
    with open(BB_OUT, "w", encoding="utf-8") as f:
        json.dump(bbmodel, f, indent="\t", ensure_ascii=False)

    json.loads(open(JSON_OUT, encoding="utf-8").read())  # round-trip validation
    json.loads(open(BB_OUT, encoding="utf-8").read())
    print("wrote %s (%d rects), %s, %s" % (JSON_OUT, len(placed), TEX_OUT, BB_OUT))


if __name__ == "__main__":
    main()
