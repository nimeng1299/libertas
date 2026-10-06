#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 大巫师炼药锅 (Great Wizard Cauldron) controller front texture.

Mirrors the great wizard oven workflow: the controller keeps the Thaumcraft
arcane-stone frame of the machine family (copied pixel-perfect from
great_wizard_oven.png) and replaces the firebox interior with a witch's
cauldron — bubbling green brew in a dark pot over a fire glow. Two artifacts
are regenerated so the in-game texture and the Blockbench project can never
drift:

  1. src/main/resources/assets/libertas/textures/blocks/machines/great_wizard_cauldron.png
     16x16 front texture consumed by Textures.BlockIcons.custom("libertas",
     "machines/great_wizard_cauldron").
  2. blockbench/greatWizardCauldronController.bbmodel
     Blockbench paint project (free format, embedded texture) for visual editing.

Regenerate:  python tools/gen_great_wizard_cauldron_texture.py
"""

import base64
import io
import json
import os
import uuid

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OVEN_PNG = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/blocks/machines/great_wizard_oven.png")
OUT_PNG = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/blocks/machines/great_wizard_cauldron.png")
OUT_BBMODEL = os.path.join(ROOT, "blockbench/greatWizardCauldronController.bbmodel")

# Cauldron interior palette
POT_DARK = (36, 31, 46, 255) # pot silhouette
POT_LIGHT = (74, 65, 96, 255) # pot rim highlights
BREW_DARK = (26, 92, 42, 255)
BREW_MID = (62, 160, 70, 255)
BREW_BRIGHT = (140, 224, 100, 255)
BREW_SPARKLE = (200, 240, 160, 255)
FIRE_RED = (192, 58, 24, 255)
FIRE_ORANGE = (239, 122, 28, 255)
FIRE_YELLOW = (255, 198, 61, 255)

# The oven's interior opening spans columns 5..10, rows 6..13 (bordered by the
# dark lintel row 5 and sill row 14, which stay part of the arcane-stone frame).
INTERIOR_X0, INTERIOR_X1 = 5, 10
INTERIOR_Y0, INTERIOR_Y1 = 6, 13

# 8 rows (top -> bottom) x 6 columns of the replaced interior:
# pot rim, bubbling brew, pot bottom, fire glow beneath.
INTERIOR_ART = [
    "pPPPPp",
    "gGBGgg",
    "GggSgG",
    "gGggBg",
    "GggGgG",
    "ggGggG",
    "PpPPpP",
    "roffor",
]

ART_COLORS = {
    "P": POT_DARK,
    "p": POT_LIGHT,
    "g": BREW_DARK,
    "G": BREW_MID,
    "B": BREW_BRIGHT,
    "S": BREW_SPARKLE,
    "r": FIRE_RED,
    "o": FIRE_ORANGE,
    "f": FIRE_YELLOW,
}


def paint() -> Image.Image:
    image = Image.open(OVEN_PNG).convert("RGBA")
    pixels = image.load()
    for row, art in enumerate(INTERIOR_ART):
        y = INTERIOR_Y0 + row
        for col, ch in enumerate(art):
            pixels[INTERIOR_X0 + col, y] = ART_COLORS[ch]
    return image


def bbmodel(image: Image.Image) -> dict:
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    data_url = "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")
    return {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": "greatWizardCauldronController",
        "model_identifier": "",
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "ai_used": True,
        "ai_agents": "zcode",
        "resolution": {"width": 16, "height": 16},
        "elements": [],
        "groups": [],
        "outliner": [],
        "textures": [
            {
                "name": "GREAT_WIZARD_CAULDRON",
                "path": "",
                "folder": "",
                "namespace": "",
                "id": "0",
                "group": "",
                "scope": 0,
                "width": 16,
                "height": 16,
                "uv_width": 16,
                "uv_height": 16,
                "particle": False,
                "use_as_default": False,
                "layers_enabled": False,
                "sync_to_project": "",
                "file_format": "png",
                "render_mode": "default",
                "render_sides": "auto",
                "wrap_mode": "limited",
                "pbr_channel": "color",
                "fps": 7,
                "frame_time": 1,
                "frame_order_type": "loop",
                "frame_order": "",
                "frame_interpolate": False,
                "visible": True,
                "internal": True,
                "saved": False,
                "uuid": str(uuid.uuid4()),
                "source": data_url,
            }
        ],
    }


def main() -> None:
    image = paint()
    os.makedirs(os.path.dirname(OUT_PNG), exist_ok=True)
    image.save(OUT_PNG)
    with open(OUT_BBMODEL, "w", encoding="utf-8") as f:
        json.dump(bbmodel(image), f, indent=1, ensure_ascii=False)
    print(f"wrote {os.path.relpath(OUT_PNG, ROOT)}")
    print(f"wrote {os.path.relpath(OUT_BBMODEL, ROOT)}")


if __name__ == "__main__":
    main()
