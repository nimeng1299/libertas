#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 风息花 (zephyrBloom) flower artifacts.

Botania special flowers are cross-sprite blocks: Botania renders them with its
own crossed-squares renderer using a single 16x16 icon registered as
``botania:<subtile>``. The icon therefore lives in Botania's asset domain
(FML merges assets from every mod jar) and the "model" work is the sprite
itself plus a Blockbench project that previews the crossed planes:

  1. src/main/resources/assets/botania/textures/blocks/zephyrBloom.png
     16x16 flower sprite; resolved by Botania's BasicSignature icon lookup.
  2. src/main/resources/assets/botania/textures/blocks/alt/zephyrBloom.png
     Dusk variant shown when Botania's "alt flower textures" option is on
     (Botania ships one for every flower, so we must too).
  3. blockbench/zephyrBloom.bbmodel
     Blockbench project (free format) with two crossed planes UV-mapped to the
     full sprite, mirroring Botania's in-world crossed-squares rendering.

Regenerate:  python tools/gen_zephyr_bloom_texture.py
"""

import base64
import io
import json
import os
import uuid

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_OUT = os.path.join(ROOT, "src/main/resources/assets/botania/textures/blocks/zephyrBloom.png")
TEX_ALT_OUT = os.path.join(ROOT, "src/main/resources/assets/botania/textures/blocks/alt/zephyrBloom.png")
BB_OUT = os.path.join(ROOT, "blockbench/zephyrBloom.bbmodel")

# Sprite palette, 16x16, one char per pixel. Fresh mint wind-flower: a bell
# bloom on a slim stem, two leaves, and breeze curls drifting to the right.
#
# . transparent          o petal outline      d petal deep
# P petal mid            p petal light        g glow (throat)
# s stem                 l leaf               L leaf deep
# w breeze light         W breeze shade
GRID = [
    "................",
    ".......oo.......",
    "......opPo......",
    "......pPPp......",
    ".....opPPpo..w..",
    ".....opPPpo.W...",
    ".....opggpo.W...",
    "......oppo.W....",
    ".......os.W.....",
    ".......s........",
    "....L..s..w.....",
    "...lLls...W.....",
    "...LlLs..W......",
    "....Ls..........",
    "......s.........",
    "................",
]

NORMAL = {
    ".": (0, 0, 0, 0),
    "o": (47, 138, 112, 255),   # deep mint outline
    "d": (88, 184, 150, 255),   # deep petal
    "P": (127, 217, 184, 255),  # mid petal
    "p": (169, 239, 210, 255),  # light petal
    "g": (244, 255, 233, 255),  # pale throat glow
    "s": (78, 158, 114, 255),   # stem
    "l": (111, 199, 150, 255),  # leaf
    "L": (64, 138, 96, 255),    # deep leaf
    "w": (233, 255, 246, 255),  # breeze, light
    "W": (189, 239, 224, 255),  # breeze, shade
}

# Dusk variant (alt): the same sprite after nightfall - slate-blue petals,
# moonlit throat, deep teal foliage.
ALT = {
    ".": (0, 0, 0, 0),
    "o": (44, 74, 110, 255),
    "d": (92, 121, 200, 255),
    "P": (126, 156, 232, 255),
    "p": (159, 184, 240, 255),
    "g": (234, 242, 255, 255),
    "s": (88, 120, 160, 255),
    "l": (126, 156, 232, 255),
    "L": (62, 90, 140, 255),
    "w": (214, 228, 252, 255),
    "W": (158, 180, 228, 255),
}


def det_uuid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "libertas:zephyrBloom:" + name))


def build_image(palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(GRID):
        assert len(row) == 16, "row %d has %d chars" % (y, len(row))
        for x, ch in enumerate(row):
            img.putpixel((x, y), palette[ch])
    return img


def crossed_plane(name, angle):
    """A 16x16 thin plane through the block centre, rotated `angle` around Y."""
    return {
        "name": name,
        "box_uv": False,
        "rescale": False,
        "locked": False,
        "render_order": "default",
        "allow_mirror_modeling": True,
        "from": [0.0, 0.0, 7.875],
        "to": [16.0, 16.0, 8.125],
        "autouv": 0,
        "color": 7,
        "origin": [8.0, 8.0, 8.0],
        "rotation": {"angle": angle, "axis": "y", "origin": [8.0, 8.0, 8.0]},
        "uv_offset": [0, 0],
        "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": 0},
            "south": {"uv": [0, 0, 16, 16], "texture": 0},
            # Thin edges / caps: collapse onto the transparent top row.
            "east": {"uv": [0, 0, 16, 0], "texture": 0},
            "west": {"uv": [0, 0, 16, 0], "texture": 0},
            "up": {"uv": [0, 0, 16, 0], "texture": 0},
            "down": {"uv": [0, 0, 16, 0], "texture": 0},
        },
        "type": "cube",
        "uuid": det_uuid(name),
    }


def write_bbmodel(png_path, png_name):
    with open(png_path, "rb") as f:
        png_bytes = f.read()

    plane_a = crossed_plane("cross_a", 45)
    plane_b = crossed_plane("cross_b", -45)
    elements = [plane_a, plane_b]

    bbmodel = {
        "meta": {"format_version": "4.5", "box_uv": False},
        "name": "zephyrBloom",
        "model_identifier": "",
        "visible_box": [1.0, 1.0, 0.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": 16, "height": 16},
        "elements": elements,
        "outliner": [{
            "name": "zephyrBloom",
            "origin": [8.0, 8.0, 8.0],
            "color": 7,
            "uuid": det_uuid("group"),
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": [plane_a["uuid"], plane_b["uuid"]],
        }],
        "textures": [{
            "path": os.path.relpath(png_path, os.path.dirname(BB_OUT)).replace("\\", "/"),
            "name": png_name,
            "folder": "",
            "namespace": "",
            "id": "0",
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
    json.loads(open(BB_OUT, encoding="utf-8").read())  # round-trip validation


def main():
    normal = build_image(NORMAL)
    alt = build_image(ALT)

    os.makedirs(os.path.dirname(TEX_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(TEX_ALT_OUT), exist_ok=True)
    normal.save(TEX_OUT)
    alt.save(TEX_ALT_OUT)
    write_bbmodel(TEX_OUT, "zephyrBloom.png")

    # Enlarged previews for eyeballing (next to the script output, not committed).
    preview_dir = os.path.join(ROOT, "build", "zephyrBloom_preview")
    os.makedirs(preview_dir, exist_ok=True)
    normal.resize((256, 256), Image.NEAREST).save(os.path.join(preview_dir, "normal.png"))
    alt.resize((256, 256), Image.NEAREST).save(os.path.join(preview_dir, "alt.png"))
    print("wrote %s, %s, %s" % (TEX_OUT, TEX_ALT_OUT, BB_OUT))


if __name__ == "__main__":
    main()
