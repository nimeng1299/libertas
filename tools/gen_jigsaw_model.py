#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 拼图 (jigsaw) model artifacts.

Single source of truth for the puzzle piece's cubes; regenerates three files so the
runtime model, the texture atlas and the Blockbench project can never drift:

  1. src/main/resources/assets/libertas/models/item/jigsaw.json
     1.8-style item model JSON consumed by neuvillette.libertas.client.render.JsonItemRenderer.
     UV space is 0..16 (the renderer normalizes by 16); the texture itself is 64x64.
  2. src/main/resources/assets/libertas/textures/items/jigsaw.png
     64x64 atlas: a 52x52 "picture" region (the piece's printed face) plus kraft
     cardboard swatches for the 2px-thick edges.
  3. blockbench/jigsaw.bbmodel
     Blockbench project (free format, per-face UV, embedded texture) for visual editing.

The piece stands upright in the XY plane (2px thick in Z, 7..9). Its silhouette spans
13x13 world units (x/y 1.5..14.5). The north/south faces map 1:1 from world (x, y) into
the picture region (v flipped), so the print runs continuously across core, knobs,
feet and teeth; all edge/cap faces share four kraft cardboard swatches (sub-rect
sampling of homogeneous noise, so any face size works without its own rect).

Regenerate:  python tools/gen_jigsaw_model.py
"""

import base64
import io
import json
import os
import random
import uuid

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JSON_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/models/item/jigsaw.json")
TEX_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/items/jigsaw.png")
BB_OUT = os.path.join(ROOT, "blockbench/jigsaw.bbmodel")
TEX_REF = "libertas:items/jigsaw"

TEX_PX = 64  # texture resolution (square)
UV_SPACE = 16.0  # uv units available in each axis (JsonItemRenderer divides by 16)
PX_PER_UV = TEX_PX / UV_SPACE  # 4

# --- layout --------------------------------------------------------------
# Picture region: uv (0,0)-(13,13) == the piece silhouette in world units, 1:1.
PIC_X0, PIC_Y0 = 1.5, 1.5  # world-space origin of the silhouette
PIC_SIZE = 13.0
PIC_PX = int(PIC_SIZE * PX_PER_UV)  # 52

# Cardboard swatches. All side strips are exactly 2 wide (the plate thickness), so
# every (2 x h<=8) face samples the top h rows of CARD; caps are 2 tall, every
# (w x 2) face samples the left w columns of one of the cap swatches.
CARD = (13.25, 0.0, 15.25, 8.0)
CAP_8 = (0.0, 13.25, 8.0, 15.25)
CAP_3 = (8.25, 13.25, 11.25, 15.25)
CAP_2 = (11.5, 13.25, 13.5, 15.25)

# (name, from[x,y,z], to[x,y,z]) - classic piece: knobs top/left, notches bottom/right.
CUBES = [
    ("core", (4.5, 3.5, 7), (12.5, 11.5, 9)),
    ("knob_top_neck", (6.5, 11.5, 7), (10.5, 12.5, 9)),
    ("knob_top_head", (5.5, 12.5, 7), (11.5, 14.5, 9)),
    ("knob_left_neck", (3.5, 5.5, 7), (4.5, 9.5, 9)),
    ("knob_left_head", (1.5, 4.5, 7), (3.5, 10.5, 9)),
    ("foot_left", (4.5, 1.5, 7), (7.5, 3.5, 9)),
    ("foot_right", (9.5, 1.5, 7), (12.5, 3.5, 9)),
    ("tooth_top", (12.5, 8.5, 7), (14.5, 11.5, 9)),
    ("tooth_bottom", (12.5, 3.5, 7), (14.5, 6.5, 9)),
]

# --- palette ---------------------------------------------------------------
SKY_STOPS = [
    (0.0, (120, 198, 252)),
    (0.35, (168, 182, 248)),
    (0.68, (152, 112, 218)),
    (1.0, (96, 60, 162)),
]
CLOUD = (246, 249, 255)
CLOUD_SHADE = (206, 214, 244)
SPARK_WARM = (255, 244, 214)
SPARK_CORE = (255, 252, 240)
OUTLINE = (56, 40, 100)
KRAFT = (198, 158, 108)
KRAFT_DK = (172, 134, 88)
KRAFT_HI = (216, 182, 134)
BG = (16, 12, 32)


def clamp(c):
    return max(0, min(255, int(round(c))))


def jitter(rng, base, amount):
    return tuple(clamp(ch + rng.randint(-amount, amount)) for ch in base)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def sky(t):
    for (t0, c0), (t1, c1) in zip(SKY_STOPS, SKY_STOPS[1:]):
        if t <= t1 + 1e-6:
            return lerp(c0, c1, (t - t0) / (t1 - t0))
    return SKY_STOPS[-1][1]


# --- painting ---------------------------------------------------------------
def cloud(d, cx, cy, rng):
    puffs = [(0, 0, 7, 4), (-7, 2, 5, 3), (7, 2, 5, 3), (-2, -3, 5, 3)]
    for ox, oy, w, h in puffs:  # shaded undersides peek out below the white tops
        d.ellipse([cx + ox - w + 1, cy + oy + 1, cx + ox + w, cy + oy + h + 2], fill=CLOUD_SHADE)
    for ox, oy, w, h in puffs:
        d.ellipse([cx + ox - w, cy + oy - h, cx + ox + w, cy + oy + h], fill=jitter(rng, CLOUD, 3))


def sparkle(d, cx, cy):
    for ox, oy in ((-3, 0), (-2, 0), (2, 0), (3, 0), (0, -3), (0, -2), (0, 2), (0, 3)):
        d.point((cx + ox, cy + oy), fill=SPARK_WARM)
    for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1), (0, 0)):
        d.point((cx + ox, cy + oy), fill=SPARK_CORE)


def paint_picture(d, rng):
    for py in range(PIC_PX):
        col = sky(py / (PIC_PX - 1))
        for px in range(PIC_PX):
            d.point((px, py), fill=jitter(rng, col, 6))
    cloud(d, 31, 7, rng)
    cloud(d, 42, 15, rng)
    sparkle(d, 8, 10)
    sparkle(d, 45, 27)
    sparkle(d, 11, 38)
    for sx, sy in ((20, 4), (35, 22), (16, 31), (27, 44), (47, 41), (5, 23)):
        d.point((sx, sy), fill=SPARK_CORE)
    paint_outline(d)


def paint_outline(d):
    """1px dark line tracing the piece silhouette inside the picture region."""
    m = [[False] * PIC_PX for _ in range(PIC_PX)]
    for _name, frm, to in CUBES:
        x0 = int(round((frm[0] - PIC_X0) * PX_PER_UV))
        x1 = int(round((to[0] - PIC_X0) * PX_PER_UV))
        y0 = int(round((PIC_Y0 + PIC_SIZE - to[1]) * PX_PER_UV))
        y1 = int(round((PIC_Y0 + PIC_SIZE - frm[1]) * PX_PER_UV))
        for py in range(y0, y1):
            for px in range(x0, x1):
                m[py][px] = True
    for py in range(PIC_PX):
        for px in range(PIC_PX):
            if not m[py][px]:
                continue
            if px == 0 or px == PIC_PX - 1 or py == 0 or py == PIC_PX - 1 \
                    or not m[py][px - 1] or not m[py][px + 1] or not m[py - 1][px] or not m[py + 1][px]:
                d.point((px, py), fill=OUTLINE)


def paint_card(d, rect, rng):
    x0, y0 = int(round(rect[0] * PX_PER_UV)), int(round(rect[1] * PX_PER_UV))
    x1, y1 = int(round(rect[2] * PX_PER_UV)), int(round(rect[3] * PX_PER_UV))
    for py in range(y0, y1):
        for px in range(x0, x1):
            r = rng.random()
            base = KRAFT_DK if r < 0.12 else (KRAFT_HI if r < 0.3 else KRAFT)
            d.point((px, py), fill=jitter(rng, base, 7))


def build_texture():
    img = Image.new("RGBA", (TEX_PX, TEX_PX), BG + (255,))
    d = ImageDraw.Draw(img)
    paint_picture(d, random.Random("libertas:jigsaw:picture"))
    for rect in (CARD, CAP_8, CAP_3, CAP_2):
        paint_card(d, rect, random.Random("libertas:jigsaw:card|%s" % (rect,)))
    return img


# --- uv mapping ---------------------------------------------------------------
def pic_uv(frm, to):
    """north/south: world (x, y) mapped 1:1 into the picture region (v flipped)."""
    v_top = PIC_Y0 + PIC_SIZE - to[1]
    v_bot = PIC_Y0 + PIC_SIZE - frm[1]
    return [frm[0] - PIC_X0, v_top, to[0] - PIC_X0, v_bot]


def card_uv(frm, to):
    h = to[1] - frm[1]
    return [CARD[0], CARD[1], CARD[2], CARD[1] + h]


def cap_uv(frm, to):
    w = to[0] - frm[0]
    if w >= 4:
        r = CAP_8
    elif w == 3:
        r = CAP_3
    else:
        r = CAP_2
    return [r[0], r[1], r[0] + w, r[3]]


def face_uv(face, frm, to):
    if face in ("north", "south"):
        return pic_uv(frm, to)
    if face in ("east", "west"):
        return card_uv(frm, to)
    return cap_uv(frm, to)


def build_faces(frm, to):
    return {face: {"uv": [round(v, 3) for v in face_uv(face, frm, to)], "texture": "#0"}
            for face in ("north", "east", "south", "west", "up", "down")}


NS = uuid.UUID("9a1b6f3e-2c47-4d5e-8a19-b1c2d3e4f5a6")


def det_uuid(name):
    return str(uuid.uuid5(NS, name))


def main():
    img = build_texture()
    buf = io.BytesIO()
    img.save(buf, "PNG")
    png_bytes = buf.getvalue()

    os.makedirs(os.path.dirname(JSON_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(TEX_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(BB_OUT), exist_ok=True)
    img.save(TEX_OUT)

    elements_json, elements_bb, children = [], [], []
    for name, frm, to in CUBES:
        faces = build_faces(frm, to)
        el = {"name": name, "from": list(frm), "to": list(to)}
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
        elements_bb.append(bb_el)
        children.append(det_uuid(name))

    model = {
        "credit": "Generated by tools/gen_jigsaw_model.py - edit blockbench/jigsaw.bbmodel instead",
        "textures": {"0": TEX_REF, "particle": TEX_REF},
        "elements": elements_json,
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(model, f, indent="\t", ensure_ascii=False)

    bbmodel = {
        "meta": {"format_version": "4.5", "box_uv": False},
        "name": "jigsaw",
        "model_identifier": "",
        "visible_box": [1.0, 1.0, 0.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": 16, "height": 16},
        "elements": elements_bb,
        "outliner": [{
            "name": "jigsaw",
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
            "name": "jigsaw.png",
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
    print("wrote %s (%d cubes), %s, %s" % (JSON_OUT, len(CUBES), TEX_OUT, BB_OUT))


if __name__ == "__main__":
    main()
