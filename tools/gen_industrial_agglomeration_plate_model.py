#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 工业凝聚板 (industrialAgglomerationPlate) model artifacts.

Geometry source of truth: the Industrial Agglomeration Factory block model from
[ChaoticTrials/BotanicalMachinery](https://github.com/ChaoticTrials/BotanicalMachinery)
(Apache License 2.0), vendored verbatim at
tools/botanical_machinery/industrial_agglomeration_factory.json (see NOTICE.md
there) - the vanilla terrestrial agglomeration multiblock (lapis + livingrock
platform, plate on top) compressed into a single machine frame block.

  1. src/main/resources/assets/libertas/models/block/industrialAgglomerationPlate.json
     Same elements/faces/uv as upstream (uv space 0..16). Upstream's 1.16
     textures map onto their 1.7.10 equivalents (resolved at runtime):
     livingrock -> botania:blocks/livingrock0, lapis -> minecraft:blocks/lapis_block,
     terra_plate_top -> botania:blocks/terraPlate1, terra_plate_side ->
     botania:blocks/terraPlate2, frame -> the shared painted dragonstone-style
     frame texture (see gen_mechanical_mana_pool_model.py).
  2. blockbench/industrialAgglomerationPlate.bbmodel
     Blockbench project (free format, per-face UV, embedded textures).

Regenerate:  python tools/gen_industrial_agglomeration_plate_model.py
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
SRC_JSON = os.path.join(ROOT, "tools/botanical_machinery/industrial_agglomeration_factory.json")
LIVINGROCK_PNG = os.path.join(ROOT, "tools/botanical_machinery/livingrock0.png")
LAPIS_PNG = os.path.join(ROOT, "tools/botanical_machinery/lapis_block.png")
TERRA_TOP_PNG = os.path.join(ROOT, "tools/botanical_machinery/terraPlate1.png")
TERRA_SIDE_PNG = os.path.join(ROOT, "tools/botanical_machinery/terraPlate2.png")
JSON_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/models/block/industrialAgglomerationPlate.json")
FRAME_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/blocks/mechanical_frame.png")
BB_OUT = os.path.join(ROOT, "blockbench/industrialAgglomerationPlate.bbmodel")

# upstream texture key -> runtime texture (1.7.10 Botania / vanilla / shared painted frame)
TEXTURES = {
    "livingrock": "botania:blocks/livingrock0",
    "lapis": "minecraft:blocks/lapis_block",
    "terra_plate_top": "botania:blocks/terraPlate1",
    "terra_plate_side": "botania:blocks/terraPlate2",
    "frame": "libertas:blocks/mechanical_frame",
    "particle": "botania:blocks/livingrock0",
}

FRAME_TEX_PX = 64  # painted at 4 px per uv unit (uv space stays 0..16)

# dragonstone palette (same as the other mechanical machines)
STONE_DARK = (196, 104, 182)
STONE_MID = (208, 106, 190)
STONE = (230, 168, 218)
STONE_LIGHT = (239, 201, 232)
STONE_PALE = (245, 220, 240)


def paint_frame():
    """Dragonstone-style gemstone noise; byte-identical to the other machines'
    frame texture (same seed)."""
    rng = random.Random("mechanicalManaPool.frame")
    coarse = [[rng.random() for _ in range(5)] for _ in range(5)]
    stops = [(0.18, STONE_DARK), (0.4, STONE_MID), (0.62, STONE), (0.85, STONE_LIGHT), (1.01, STONE_PALE)]
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    for x in range(16):
        for y in range(16):
            gx, gy = x / 16 * 4, y / 16 * 4
            x0, y0 = int(gx), int(gy)
            fx, fy = gx - x0, gy - y0
            v = (
                coarse[y0][x0] * (1 - fx) * (1 - fy)
                + coarse[y0][x0 + 1] * fx * (1 - fy)
                + coarse[y0 + 1][x0] * (1 - fx) * fy
                + coarse[y0 + 1][x0 + 1] * fx * fy
            )
            v = min(1.0, max(0.0, v + rng.uniform(-0.12, 0.12)))
            for stop, col in stops:
                if v <= stop:
                    d.point((x, y), fill=col + (255,))
                    break
    return img.resize((FRAME_TEX_PX, FRAME_TEX_PX), Image.NEAREST)


def resolve_tex_ref(ref):
    key = ref.lstrip("#")
    while key.startswith("#"):
        key = TEXTURES[key.lstrip("#")].lstrip("#")
    return TEXTURES.get(key, ref)


def main():
    with open(SRC_JSON, encoding="utf-8") as f:
        upstream = json.load(f)
    # flatten the parent chain by hand: the only parent (botanical_block) just
    # provides the #frame texture + particle, replicated in TEXTURES above
    elements = upstream["elements"]

    frame_img = paint_frame()
    buf = io.BytesIO()
    frame_img.save(buf, "PNG")
    frame_png = buf.getvalue()
    with open(LIVINGROCK_PNG, "rb") as f:
        livingrock_png = f.read()
    with open(LAPIS_PNG, "rb") as f:
        lapis_png = f.read()
    with open(TERRA_TOP_PNG, "rb") as f:
        terra_top_png = f.read()
    with open(TERRA_SIDE_PNG, "rb") as f:
        terra_side_png = f.read()

    os.makedirs(os.path.dirname(JSON_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(FRAME_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(BB_OUT), exist_ok=True)
    frame_img.save(FRAME_OUT)

    model = {
        "credit": "Geometry from ChaoticTrials/BotanicalMachinery (Apache-2.0),"
                  " industrial_agglomeration_factory.json - converted by tools/gen_industrial_agglomeration_plate_model.py",
        "textures": TEXTURES,
        "elements": elements,
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(model, f, indent="\t", ensure_ascii=False)

    # bbmodel: faces reference concrete texture ids
    tex_index = {
        "libertas:blocks/mechanical_frame": 0,
        "botania:blocks/livingrock0": 1,
        "minecraft:blocks/lapis_block": 2,
        "botania:blocks/terraPlate1": 3,
        "botania:blocks/terraPlate2": 4,
    }
    elements_bb, children = [], []
    for el in elements:
        faces_bb = {}
        for face, spec in el["faces"].items():
            resolved = resolve_tex_ref(spec["texture"])
            faces_bb[face] = {"uv": spec["uv"], "texture": tex_index[resolved]}
        bb_el = {
            "name": el.get("name", "cube"),
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "render_order": "default",
            "allow_mirror_modeling": True,
            "from": list(el["from"]),
            "to": list(el["to"]),
            "autouv": 0,
            "color": 7,
            "origin": [8.0, 8.0, 8.0],
            "faces": faces_bb,
            "type": "cube",
            "uuid": det_uuid(el.get("name", "cube")),
        }
        for f in ("north", "east", "south", "west", "up", "down"):
            if f not in bb_el["faces"]:
                bb_el["faces"][f] = {"uv": [0.0, 0.0, 0.0, 0.0], "texture": None}
        elements_bb.append(bb_el)
        children.append(bb_el["uuid"])

    bbmodel = {
        "meta": {"format_version": "4.5", "box_uv": False},
        "name": "industrialAgglomerationPlate",
        "model_identifier": "",
        "visible_box": [1.0, 1.0, 0.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": 16, "height": 16},
        "elements": elements_bb,
        "outliner": [{
            "name": "industrialAgglomerationPlate",
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
        "textures": [
            _texture_entry("mechanical_frame.png", "0", frame_png, FRAME_TEX_PX),
            _texture_entry("livingrock0.png", "1", livingrock_png, 16),
            _texture_entry("lapis_block.png", "2", lapis_png, 16),
            _texture_entry("terraPlate1.png", "3", terra_top_png, 16),
            _texture_entry("terraPlate2.png", "4", terra_side_png, 16),
        ],
    }
    with open(BB_OUT, "w", encoding="utf-8") as f:
        json.dump(bbmodel, f, indent="\t", ensure_ascii=False)

    json.loads(open(JSON_OUT, encoding="utf-8").read())  # round-trip validation
    json.loads(open(BB_OUT, encoding="utf-8").read())
    print("wrote %s, %s, %s" % (JSON_OUT, FRAME_OUT, BB_OUT))


def _texture_entry(name, tex_id, png_bytes, size):
    return {
        "path": "",
        "name": name,
        "folder": "",
        "namespace": "",
        "id": tex_id,
        "group": "",
        "width": size,
        "height": size,
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
        "uuid": det_uuid("texture" + tex_id),
        "relative_path": "",
        "source": "data:image/png;base64," + base64.b64encode(png_bytes).decode("ascii"),
    }


NS = uuid.UUID("7c2e5a91-4b3d-4f68-9a2c-1d5e8f0a3b6c")


def det_uuid(name):
    return str(uuid.uuid5(NS, "industrialAgglomerationPlate|" + name))


if __name__ == "__main__":
    main()
