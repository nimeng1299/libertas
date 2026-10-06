#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 微型精灵门 (miniElfPortal) model artifacts.

Design: Botanical Machinery's machine frame (plate/bars/rails, geometry reused
verbatim from the vendored mechanical_mana_pool.json for family consistency)
wrapped around a miniature elven gateway - an alfheim-portal core base, two
livingwood pillars, a livingwood top bar and a shimmering portal plane.

  1. src/main/resources/assets/libertas/models/block/miniElfPortal.json
     1.8-style model JSON consumed by neuvillette.libertas.client.render
     .JsonItemRenderer (item form) and .JsonBlockRenderer (in-world ISBRH).
     Textures resolve at runtime from the installed Botania jar
     (livingwood pillars, alfheim portal core); the portal plane and the
     machine frame are painted.
  2. src/main/resources/assets/libertas/textures/blocks/mini_elf_portal_surface.png
     Painted shimmering portal surface (cutout, teal sparkle lattice).
  3. src/main/resources/assets/libertas/textures/blocks/mechanical_frame.png
     The shared dragonstone-style machine frame texture (same as the other
     mechanical machines; repainted here with the same seed).
  4. blockbench/miniElfPortal.bbmodel
     Blockbench project (free format, per-face UV, embedded textures).

Regenerate:  python tools/gen_mini_elf_portal_model.py
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
BM_POOL_JSON = os.path.join(ROOT, "tools/botanical_machinery/mechanical_mana_pool.json")
LIVINGWOOD_PNG = os.path.join(ROOT, "tools/botanical_machinery/livingwood0.png")
ALFPORTAL_PNG = os.path.join(ROOT, "tools/botanical_machinery/alfheimPortal0.png")
JSON_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/models/block/miniElfPortal.json")
SURFACE_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/blocks/mini_elf_portal_surface.png")
FRAME_OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/blocks/mechanical_frame.png")
BB_OUT = os.path.join(ROOT, "blockbench/miniElfPortal.bbmodel")

FRAME_REF = "libertas:blocks/mechanical_frame"
SURFACE_REF = "libertas:blocks/mini_elf_portal_surface"
LIVINGWOOD_REF = "botania:blocks/livingwood0"
CORE_REF = "botania:blocks/alfheimPortal0"

TEX_PX = 64  # painted textures are 4 px per uv unit

# dragonstone palette (same as the other mechanical machines)
STONE_DARK = (196, 104, 182)
STONE_MID = (208, 106, 190)
STONE = (230, 168, 218)
STONE_LIGHT = (239, 201, 232)
STONE_PALE = (245, 220, 240)

# portal surface palette - luminous teal shimmer
SURFACE_TEAL = (36, 172, 190)
SURFACE_TEAL_LIGHT = (110, 220, 224)
SURFACE_SHIMMER = (190, 244, 244)
SURFACE_SPARK = (240, 254, 254)


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
    return img.resize((TEX_PX, TEX_PX), Image.NEAREST)


def paint_surface():
    """Shimmering elven portal surface: translucent-looking teal lattice with
    vertical shimmer streaks and sparkles (cutout holes between strands)."""
    rng = random.Random("miniElfPortal.surface")
    size = 16
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # vertical shimmer strands with gaps (cutout holes)
    for x in range(size):
        phase = rng.random() * size
        strand = (x * 5 + 3) % 7 in (0, 1, 2)  # deterministic-ish strand pattern
        for y in range(size):
            wave = math.sin((y + phase) * 0.8 + x * 0.9)
            if strand and wave > -0.2:
                base = SURFACE_TEAL if wave < 0.45 else (SURFACE_TEAL_LIGHT if wave < 0.85 else SURFACE_SHIMMER)
                d.point((x, y), fill=base + (255,))
    # sparkles
    for _ in range(10):
        x, y = rng.randrange(size), rng.randrange(size)
        if img.getpixel((x, y))[3] > 0:
            d.point((x, y), fill=SURFACE_SPARK + (255,))
    return img.resize((TEX_PX, TEX_PX), Image.NEAREST)


def load_frame_elements():
    """The machine frame cubes (plate/bars/rails) verbatim from the vendored
    Botanical Machinery model - they reference #frame."""
    with open(BM_POOL_JSON, encoding="utf-8") as f:
        upstream = json.load(f)
    return [el for el in upstream["elements"] if el["name"] in
            ("plate", "bar_nw", "bar_ne", "bar_sw", "bar_se", "rail_n", "rail_e", "rail_s", "rail_w")]


def mini_gateway_elements():
    """The miniature elven gateway inside the frame.

    - core base (4,1,6)-(12,4,10): alfheim portal core texture (down skipped,
      coplanar with the frame plate top)
    - pillars (2..4 / 12..14, y1..13, z7..9): livingwood (down skipped)
    - top bar (4,13,7)-(12,15,9): livingwood (down skipped, coplanar with the
      plane top edge)
    - portal plane (4,4,7.75)-(12,13,8.25): painted shimmer surface,
      north/south faces (the plane's large faces face the open frame sides)
    """
    full = ["north", "east", "south", "west", "up"]
    return [
        ("core", (4.0, 1.0, 6.0), (12.0, 4.0, 10.0), CORE_REF, full),
        ("pillar_w", (2.0, 1.0, 7.0), (4.0, 13.0, 9.0), LIVINGWOOD_REF, full),
        ("pillar_e", (12.0, 1.0, 7.0), (14.0, 13.0, 9.0), LIVINGWOOD_REF, full),
        ("bar_top", (4.0, 13.0, 7.0), (12.0, 15.0, 9.0), LIVINGWOOD_REF, full),
        ("portal_plane", (4.0, 4.0, 7.75), (12.0, 13.0, 8.25), SURFACE_REF, ["north", "south"]),
    ]


def main():
    frame_img = paint_frame()
    surface_img = paint_surface()
    buf = io.BytesIO()
    frame_img.save(buf, "PNG")
    frame_png = buf.getvalue()
    buf = io.BytesIO()
    surface_img.save(buf, "PNG")
    surface_png = buf.getvalue()
    with open(LIVINGWOOD_PNG, "rb") as f:
        livingwood_png = f.read()
    with open(ALFPORTAL_PNG, "rb") as f:
        alfportal_png = f.read()

    os.makedirs(os.path.dirname(JSON_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(SURFACE_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(BB_OUT), exist_ok=True)
    frame_img.save(FRAME_OUT)
    surface_img.save(SURFACE_OUT)

    textures = {"frame": FRAME_REF, "surface": SURFACE_REF,
                "livingwood": LIVINGWOOD_REF, "core": CORE_REF,
                "particle": LIVINGWOOD_REF}

    elements = []
    for el in load_frame_elements():
        elements.append(el)  # faces reference #frame already
    for name, frm, to, tex, faces in mini_gateway_elements():
        elements.append({
            "name": name,
            "from": list(frm),
            "to": list(to),
            "faces": {f: {"uv": [0, 0, 16, 16], "texture": tex} for f in faces},
        })

    model = {
        "credit": "Frame geometry from ChaoticTrials/BotanicalMachinery (Apache-2.0);"
                  " gateway composed by tools/gen_mini_elf_portal_model.py",
        "textures": textures,
        "elements": elements,
    }
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(model, f, indent="\t", ensure_ascii=False)

    # bbmodel
    tex_index = {FRAME_REF: 0, SURFACE_REF: 1, LIVINGWOOD_REF: 2, CORE_REF: 3}
    elements_bb, children = [], []
    for el in elements:
        faces_bb = {}
        for face, spec in el["faces"].items():
            resolved = resolve_tex_ref(textures, spec["texture"])
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
        "name": "miniElfPortal",
        "model_identifier": "",
        "visible_box": [1.0, 1.0, 0.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": 16, "height": 16},
        "elements": elements_bb,
        "outliner": [{
            "name": "miniElfPortal",
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
            _texture_entry("mechanical_frame.png", "0", frame_png, TEX_PX),
            _texture_entry("mini_elf_portal_surface.png", "1", surface_png, TEX_PX),
            _texture_entry("livingwood0.png", "2", livingwood_png, 16),
            _texture_entry("alfheimPortal0.png", "3", alfportal_png, 16),
        ],
    }
    with open(BB_OUT, "w", encoding="utf-8") as f:
        json.dump(bbmodel, f, indent="\t", ensure_ascii=False)

    json.loads(open(JSON_OUT, encoding="utf-8").read())  # round-trip validation
    json.loads(open(BB_OUT, encoding="utf-8").read())
    print("wrote %s, %s, %s, %s" % (JSON_OUT, SURFACE_OUT, FRAME_OUT, BB_OUT))


def resolve_tex_ref(textures, ref):
    key = ref.lstrip("#")
    while key.startswith("#"):
        key = textures[key.lstrip("#")].lstrip("#")
    return textures.get(key, ref)


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
    return str(uuid.uuid5(NS, "miniElfPortal|" + name))


if __name__ == "__main__":
    main()
