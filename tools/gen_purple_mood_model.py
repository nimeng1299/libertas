#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 紫色心情 (purpleMood) model artifacts.

Single source of truth for the wand's cubes; regenerates three files so the
runtime model, the texture atlas and the Blockbench project can never drift:

  1. src/main/resources/assets/libertas/models/item/purpleMood.json
     1.8-style item model JSON consumed by neuvillette.libertas.client.render.JsonItemRenderer.
     UV space is 0..16 (the renderer normalizes by 16); the texture itself is 64x64.
  2. src/main/resources/assets/libertas/textures/items/purple_mood.png
     64x64 purple-tech material atlas painted to exactly match the packed UV rects.
  3. blockbench/purpleMood.bbmodel
     Blockbench project (free format, per-face UV, embedded texture) for visual editing.

Faces sharing material + dimensions share one UV rect. Rects are shelf-packed
into the 16x16 uv space, painted at 4 px per uv unit.

Regenerate:  python tools/gen_purple_mood_model.py
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
JSON_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/models/item/purpleMood.json")
TEX_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/items/purple_mood.png")
BB_OUT = os.path.join(ROOT, "blockbench/purpleMood.bbmodel")
TEX_REF = "libertas:items/purple_mood"

TEX_PX = 64  # texture resolution (square)
UV_SPACE = 16.0  # uv units available in each axis (JsonItemRenderer divides by 16)
PX_PER_UV = TEX_PX / UV_SPACE
SNAP = 0.25  # uv coordinate granularity (1 px)
GUTTER = 0.25  # gap between packed rects

# Palette (RGB) - purple tech.
DARK = (24, 19, 42)
DARK_2 = (38, 30, 64)
PANEL = (42, 32, 68)
PANEL_HI = (70, 54, 112)
SEAM = (27, 20, 46)
RIM = (88, 28, 135)
GLOW_DARK = (109, 40, 217)
GLOW = (168, 85, 247)
GLOW_BRIGHT = (216, 180, 254)
GLOW_WHITE = (245, 243, 255)
BG = (7, 5, 16)


def rot_y45(origin_y):
    return {"angle": 45, "axis": "y", "origin": [8.0, origin_y, 8.0]}


# (name, from[x,y,z], to[x,y,z], rotation|None, material)
# Built along +Y, base at y=0, x/z centred on 8. Total height ~21.2.
CUBES = [
    ("pommel_core", (7.0, 0.0, 7.0), (9.0, 1.0, 9.0), None, "metal"),
    ("pommel_glow", (6.5, 0.6, 6.5), (9.5, 1.4, 9.5), None, "glow"),
    ("grip", (7.0, 1.0, 7.0), (9.0, 6.0, 9.0), None, "grip"),
    ("collar", (6.5, 6.0, 6.5), (9.5, 7.0, 9.5), None, "metal_ring"),
    ("shaft", (7.25, 7.0, 7.25), (8.75, 15.0, 8.75), None, "vent"),
    ("coil_1", (6.75, 8.0, 6.75), (9.25, 9.0, 9.25), None, "glow"),
    ("coil_2", (6.75, 10.5, 6.75), (9.25, 11.5, 9.25), None, "glow"),
    ("coil_3", (6.75, 13.0, 6.75), (9.25, 14.0, 9.25), None, "glow"),
    ("emitter_ring", (6.25, 15.0, 6.25), (9.75, 16.5, 9.75), None, "metal_ring"),
    ("fin_a", (5.9, 15.4, 7.7), (10.1, 16.1, 8.3), None, "fin"),
    ("fin_b", (7.7, 15.4, 5.9), (8.3, 16.1, 10.1), None, "fin"),
    ("neck", (7.5, 16.5, 7.5), (8.5, 17.5, 8.5), None, "metal"),
    ("crystal", (7.25, 17.5, 7.25), (8.75, 20.0, 8.75), rot_y45(18.75), "crystal"),
    ("crystal_tip", (7.6, 20.0, 7.6), (8.4, 21.2, 8.4), rot_y45(20.6), "crystal_bright"),
]

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
    """Shelf-pack one rect per unique (material, kind, w, h); returns {key: (u, v, w, h)}."""
    keys = set()
    for name, frm, to, _rot, mat in CUBES:
        for face in ("north", "east", "south", "west", "up", "down"):
            w, h = face_dims(frm, to, face)
            keys.add((material_for(face, mat), round(snap(w), 4), round(snap(h), 4)))

    rects = sorted(((k[0], k[1], k[2], k) for k in keys), key=lambda r: -r[2])
    placed, x, y, shelf_h = {}, 0.0, 0.0, 0.0
    for _mat, w, h, key in rects:
        if x + w > UV_SPACE:
            x, y, shelf_h = 0.0, y + shelf_h, 0.0
        placed[key] = (x, y, w, h)
        x += w + GUTTER
        shelf_h = max(shelf_h, h + GUTTER)
    if y + shelf_h > UV_SPACE + 1e-6:
        raise RuntimeError("UV atlas overflow: %.2f > %.2f" % (y + shelf_h, UV_SPACE))
    return placed


def clamp(c):
    return max(0, min(255, int(round(c))))


def jitter(rng, base, amount):
    return tuple(clamp(ch + rng.randint(-amount, amount)) for ch in base)


def paint_metal(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            d.point((px, py), fill=jitter(rng, PANEL, 6))
    d.rectangle([x0, y0, x0 + w - 1, y0], fill=PANEL_HI)  # top highlight
    for py in range(y0 + 3, y0 + h, 4):  # horizontal panel seams
        d.rectangle([x0, py, x0 + w - 1, py], fill=SEAM)
        d.point((x0 + 1, py + 1), fill=PANEL_HI)  # rivets
        d.point((x0 + w - 2, py + 1), fill=PANEL_HI)


def paint_metal_ring(d, x0, y0, w, h, rng):
    paint_metal(d, x0, y0, w, h, rng)
    mid = y0 + h // 2
    for px in range(x0, x0 + w):  # glowing inlay line
        c = GLOW_BRIGHT if rng.random() < 0.2 else GLOW
        d.point((px, mid), fill=c)
        if h >= 4:
            d.point((px, mid - 1), fill=jitter(rng, GLOW_DARK, 8))


def paint_grip(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            base = DARK_2 if (px + py) % 4 < 2 else DARK
            d.point((px, py), fill=jitter(rng, base, 4))
    for px in range(x0, x0 + w):  # tech dots on hatch crests
        for py in range(y0, y0 + h):
            if (px + py) % 4 == 0 and px % 3 == 1:
                d.point((px, py), fill=GLOW)
    d.rectangle([x0, y0, x0 + w - 1, y0], fill=DARK)
    d.rectangle([x0, y0 + h - 1, x0 + w - 1, y0 + h - 1], fill=DARK)


def paint_vent(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            d.point((px, py), fill=jitter(rng, PANEL, 5))
    for i, px in enumerate(range(x0 + 1, x0 + w - 1, 3)):  # vertical glow slits
        hot = 0.65 + 0.35 * math.sin(i * 1.7)
        for py in range(y0 + 1, y0 + h - 1):
            c = (GLOW_BRIGHT if rng.random() < 0.15 else GLOW) if rng.random() < hot else GLOW_DARK
            d.point((px, py), fill=c)
    d.rectangle([x0, y0, x0 + w - 1, y0], fill=PANEL_HI)
    d.rectangle([x0, y0 + h - 1, x0 + w - 1, y0 + h - 1], fill=SEAM)


def paint_glow(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = abs(py - (y0 + (h - 1) / 2.0)) / max(1.0, (h - 1) / 2.0)  # 0 centre .. 1 edge
            base = GLOW_BRIGHT if t < 0.25 else (GLOW if t < 0.7 else GLOW_DARK)
            if x0 + w / 3.0 <= px < x0 + 2 * w / 3.0 and t < 0.35:
                base = GLOW_BRIGHT
            d.point((px, py), fill=jitter(rng, base, 10))
    for _ in range(max(1, w // 3)):  # sparks
        d.point((rng.randrange(x0, x0 + w), rng.randrange(y0, y0 + h)), fill=GLOW_WHITE)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GLOW_DARK)


def paint_fin(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            d.point((px, py), fill=jitter(rng, GLOW, 12))
    mid = y0 + h // 2
    for px in range(x0, x0 + w):
        d.point((px, mid), fill=GLOW_BRIGHT if rng.random() < 0.5 else GLOW)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GLOW_DARK)


def paint_crystal(d, x0, y0, w, h, rng):
    stops = [(0.0, GLOW_WHITE), (0.25, GLOW_BRIGHT), (0.6, GLOW), (1.0, GLOW_DARK)]
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = (py - y0) / max(1.0, h - 1)  # 0 top .. 1 bottom
            base = GLOW_DARK
            for stop, col in stops:
                if t <= stop + 1e-6:
                    base = col
                    break
            if px % 2 == 0:
                base = jitter(rng, base, 14)  # vertical streaks
            d.point((px, py), fill=base)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=RIM)


def paint_crystal_bright(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = (py - y0) / max(1.0, h - 1)
            base = GLOW_BRIGHT if t > 0.5 else GLOW_WHITE
            d.point((px, py), fill=jitter(rng, base, 8))
    for _ in range(2):
        d.point((rng.randrange(x0, x0 + w), rng.randrange(y0, y0 + h)), fill=GLOW_WHITE)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GLOW)


PAINTERS = {
    "metal": paint_metal,
    "metal_ring": paint_metal_ring,
    "grip": paint_grip,
    "vent": paint_vent,
    "glow": paint_glow,
    "fin": paint_fin,
    "crystal": paint_crystal,
    "crystal_bright": paint_crystal_bright,
}


def paint_cap(d, x0, y0, w, h, rng, material):
    if w < 3 or h < 3:
        flat = {
            "metal": PANEL_HI, "metal_ring": PANEL_HI, "grip": DARK_2,
            "vent": PANEL_HI, "glow": GLOW_BRIGHT, "fin": GLOW_BRIGHT,
            "crystal": GLOW_BRIGHT, "crystal_bright": GLOW_WHITE,
        }[material]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=flat)
        return
    if material in ("metal", "metal_ring", "vent"):
        paint_metal(d, x0, y0, w, h, rng)
        d.rectangle([x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2], outline=PANEL_HI)
    elif material in ("glow", "fin"):
        paint_fin(d, x0, y0, w, h, rng)
        d.rectangle([x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2], fill=GLOW_BRIGHT)
    elif material == "grip":
        paint_grip(d, x0, y0, w, h, rng)
    else:  # crystals: flat top tone
        fill = GLOW_WHITE if material == "crystal_bright" else GLOW_BRIGHT
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=fill)
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GLOW)


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


def build_faces(placed, frm, to, mat):
    return {face: {"uv": uv_rect(placed, face, frm, to, mat), "texture": "#0"}
            for face in ("north", "east", "south", "west", "up", "down")}


NS = uuid.UUID("9a1b6f3e-2c47-4d5e-8a19-b1c2d3e4f5a6")


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
        faces = build_faces(placed, frm, to, mat)
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
            "origin": [8.0, 8.0, 8.0],
            "faces": {f: {"uv": spec["uv"], "texture": 0} for f, spec in faces.items()},
            "type": "cube",
            "uuid": det_uuid(name),
        }
        if rot:
            bb_el["rotation"] = {"angle": rot["angle"], "axis": rot["axis"], "origin": rot["origin"]}
        elements_bb.append(bb_el)
        children.append(det_uuid(name))

    model = {
        "credit": "Generated by tools/gen_purple_mood_model.py - edit blockbench/purpleMood.bbmodel instead",
        "textures": {"0": TEX_REF, "particle": TEX_REF},
        "elements": elements_json,
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(model, f, indent="\t", ensure_ascii=False)

    bbmodel = {
        "meta": {"format_version": "4.5", "box_uv": False},
        "name": "purpleMood",
        "model_identifier": "",
        "visible_box": [1.0, 1.0, 0.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": 16, "height": 16},
        "elements": elements_bb,
        "outliner": [{
            "name": "purpleMood",
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
            "path": os.path.relpath(TEX_OUT, os.path.dirname(BB_OUT)),
            "name": "purple_mood.png",
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
