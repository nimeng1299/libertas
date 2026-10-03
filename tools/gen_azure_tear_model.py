#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 碧空之泪 (azureTear) model artifacts.

Single source of truth for the crystal's cubes; regenerates three files so the
runtime model, the texture atlas and the Blockbench project can never drift:

  1. src/main/resources/assets/libertas/models/block/azureTear.json
     1.8-style model JSON consumed by neuvillette.libertas.client.render
     .JsonItemRenderer (item form) and .JsonBlockRenderer (in-world ISBRH).
     UV space is 0..16 (the renderers normalize by 16); the texture itself is 64x64.
  2. src/main/resources/assets/libertas/textures/blocks/azure_tear.png
     64x64 sky-blue crystal material atlas painted to exactly match the packed UV rects.
  3. blockbench/azureTear.bbmodel
     Blockbench project (free format, per-face UV, embedded texture) for visual editing.

Faces sharing material + dimensions share one UV rect. Rects are shelf-packed
into the 16x16 uv space, painted at 4 px per uv unit.

Regenerate:  python tools/gen_azure_tear_model.py
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
JSON_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/models/block/azureTear.json")
TEX_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/blocks/azure_tear.png")
BB_OUT = os.path.join(ROOT, "blockbench/azureTear.bbmodel")
TEX_REF = "libertas:blocks/azure_tear"

TEX_PX = 64  # texture resolution (square)
UV_SPACE = 16.0  # uv units available in each axis (renderers divide by 16)
PX_PER_UV = TEX_PX / UV_SPACE
SNAP = 0.25  # uv coordinate granularity (1 px)
GUTTER = 0.25  # gap between packed rects

# Palette (RGB) - sky blue crystal.
DEEP = (28, 82, 148)
MID = (66, 138, 205)
SKY = (118, 190, 238)
SKY_LIGHT = (178, 224, 248)
GLOW_DARK = (64, 140, 200)
GLOW = (135, 206, 250)
GLOW_BRIGHT = (208, 238, 255)
GLOW_WHITE = (242, 250, 255)
STONE_DARK = (38, 46, 60)
STONE = (58, 66, 84)
STONE_HI = (82, 94, 118)
RIM = (18, 52, 100)
BG = (6, 12, 20)


def rot_y45(origin_y, origin_x=8.0, origin_z=8.0):
    return {"angle": 45, "axis": "y", "origin": [origin_x, origin_y, origin_z]}


# (name, from[x,y,z], to[x,y,z], rotation|None, material)
# A sky-blue crystal cluster: slate base, four side shards, one main column with a
# glowing "tear" band, tapering into a bright tip. Kept inside one block cell
# (x/z 4.5..11.5, y 0..15.75) so it never pokes into neighbouring blocks.
CUBES = [
    ("base", (6.0, 0.0, 6.0), (10.0, 1.0, 10.0), None, "stone"),
    ("shard_core", (7.0, 1.0, 7.0), (9.0, 10.0, 9.0), None, "crystal"),
    ("tear", (6.5, 4.5, 6.5), (9.5, 7.0, 9.5), None, "glow"),
    ("core_tip", (7.25, 10.0, 7.25), (8.75, 13.5, 8.75), rot_y45(11.75), "crystal"),
    ("tip_spike", (7.6, 13.5, 7.6), (8.4, 15.75, 8.4), rot_y45(14.625), "crystal_bright"),
    ("shard_n", (7.25, 0.0, 4.75), (8.75, 5.5, 6.75), rot_y45(2.75, 8.0, 5.75), "crystal"),
    ("shard_s", (7.25, 0.0, 9.25), (8.75, 4.0, 11.25), rot_y45(2.0, 8.0, 10.25), "crystal"),
    ("shard_e", (9.25, 0.0, 7.25), (11.25, 4.5, 8.75), rot_y45(2.25, 10.25, 8.0), "crystal"),
    ("shard_w", (4.75, 0.0, 7.25), (6.75, 5.5, 8.75), rot_y45(2.75, 5.75, 8.0), "crystal"),
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


def paint_stone(d, x0, y0, w, h, rng):
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            d.point((px, py), fill=jitter(rng, STONE, 6))
    d.rectangle([x0, y0, x0 + w - 1, y0], fill=STONE_HI)  # top highlight
    d.rectangle([x0, y0 + h - 1, x0 + w - 1, y0 + h - 1], fill=STONE_DARK)
    for _ in range(max(1, w * h // 6)):  # sky-blue flecks in the slate
        d.point((rng.randrange(x0, x0 + w), rng.randrange(y0, y0 + h)), fill=jitter(rng, GLOW_DARK, 10))


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


def paint_crystal(d, x0, y0, w, h, rng):
    stops = [(0.0, SKY_LIGHT), (0.25, SKY), (0.6, MID), (1.0, DEEP)]
    for px in range(x0, x0 + w):
        for py in range(y0, y0 + h):
            t = (py - y0) / max(1.0, h - 1)  # 0 top .. 1 bottom
            base = DEEP
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
    "stone": paint_stone,
    "glow": paint_glow,
    "crystal": paint_crystal,
    "crystal_bright": paint_crystal_bright,
}


def paint_cap(d, x0, y0, w, h, rng, material):
    if w < 3 or h < 3:
        flat = {
            "stone": STONE_HI,
            "glow": GLOW_BRIGHT,
            "crystal": SKY_LIGHT,
            "crystal_bright": GLOW_WHITE,
        }[material]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=flat)
        return
    if material == "stone":
        paint_stone(d, x0, y0, w, h, rng)
        d.rectangle([x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2], outline=STONE_HI)
    elif material == "glow":
        paint_glow(d, x0, y0, w, h, rng)
        d.rectangle([x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2], fill=GLOW_BRIGHT)
    else:  # crystals: flat top tone
        fill = GLOW_WHITE if material == "crystal_bright" else SKY_LIGHT
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=fill)
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GLOW if material == "crystal_bright" else RIM)


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
        "credit": "Generated by tools/gen_azure_tear_model.py - edit blockbench/azureTear.bbmodel instead",
        "textures": {"0": TEX_REF, "particle": TEX_REF},
        "elements": elements_json,
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(model, f, indent="\t", ensure_ascii=False)

    bbmodel = {
        "meta": {"format_version": "4.5", "box_uv": False},
        "name": "azureTear",
        "model_identifier": "",
        "visible_box": [1.0, 1.0, 0.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": 16, "height": 16},
        "elements": elements_bb,
        "outliner": [{
            "name": "azureTear",
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
            "name": "azure_tear.png",
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
