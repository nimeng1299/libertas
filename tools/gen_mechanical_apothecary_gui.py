#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 机械花药台 (mechanicalApothecary) GUI background texture.

Writes src/main/resources/assets/libertas/textures/gui/mechanicalApothecary.png
(256x256 canvas, container panel in the top-left 176x216 area). Slot positions
MUST match ContainerMechanicalManaPool (see neuvillette.libertas.gui), which the
generator mirrors:

  - input grid 4x4   at (8, 49)
  - output grid 4x4  at (96, 49)
  - seed slot        at (80, 27)
  - player inv 3x9   at (8, 133)
  - hotbar 1x9       at (8, 191)
  - mana bar frame   x 8..168, y 15..23 (filled at runtime by the GUI)

Vanilla container styling: grey panel with white/dark bevel, dark slot wells
and an inset mana bar. Labels are drawn at runtime (CJK fonts), not baked.

Regenerate:  python tools/gen_mechanical_mana_pool_gui.py
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/libertas/textures/gui/mechanicalApothecary.png")

W, H = 176, 216
PANEL = (198, 198, 198, 255)
BEVEL_LIGHT = (255, 255, 255, 255)
BEVEL_DARK = (85, 85, 85, 255)
OUTLINE = (0, 0, 0, 255)
SLOT_WELL = (139, 139, 139, 255)
SLOT_DARK = (55, 55, 55, 255)
BAR_TRACK = (120, 120, 120, 255)
ARROW = (90, 90, 90, 255)

INPUT_X, INPUT_Y = 8, 49
OUTPUT_X, OUTPUT_Y = 96, 49
UPGRADE_X, UPGRADE_Y = 80, 27
PLAYER_Y = 133
HOTBAR_Y = 191
BAR_X, BAR_Y, BAR_W, BAR_H = 8, 15, 160, 9


def slot(d, x, y):
    """One 16x16 slot: dark well with a 1px dark top/left, light bottom/right."""
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=SLOT_WELL)
    d.line([x - 1, y - 1, x + 16, y - 1], fill=SLOT_DARK)
    d.line([x - 1, y - 1, x - 1, y + 16], fill=SLOT_DARK)
    d.line([x - 1, y + 16, x + 16, y + 16], fill=BEVEL_LIGHT)
    d.line([x + 16, y - 1, x + 16, y + 16], fill=BEVEL_LIGHT)


def main():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # panel: 1px black outline with rounded corners, bevel, grey interior
    d.rectangle([1, 1, W - 2, H - 2], fill=PANEL)
    d.rectangle([0, 0, W - 1, H - 1], outline=OUTLINE)
    d.point((0, 0), fill=(0, 0, 0, 0))
    d.point((W - 1, 0), fill=(0, 0, 0, 0))
    d.point((0, H - 1), fill=(0, 0, 0, 0))
    d.point((W - 1, H - 1), fill=(0, 0, 0, 0))
    d.line([1, 1, W - 2, 1], fill=BEVEL_LIGHT)
    d.line([1, 1, 1, H - 2], fill=BEVEL_LIGHT)
    d.line([W - 2, H - 2, 1, H - 2], fill=BEVEL_DARK)
    d.line([W - 2, H - 2, W - 2, 1], fill=BEVEL_DARK)

    # slots
    for row in range(4):
        for col in range(4):
            slot(d, INPUT_X + col * 18, INPUT_Y + row * 18)
            slot(d, OUTPUT_X + col * 18, OUTPUT_Y + row * 18)
    slot(d, UPGRADE_X, UPGRADE_Y)
    for row in range(3):
        for col in range(9):
            slot(d, INPUT_X + col * 18, PLAYER_Y + row * 18)
    for col in range(9):
        slot(d, INPUT_X + col * 18, HOTBAR_Y)

    # mana bar: inset frame, interior is the runtime-filled track
    d.rectangle([BAR_X, BAR_Y, BAR_X + BAR_W - 1, BAR_Y + BAR_H - 1], fill=BAR_TRACK)
    d.rectangle([BAR_X, BAR_Y, BAR_X + BAR_W - 1, BAR_Y + BAR_H - 1], outline=SLOT_DARK)

    # flow arrow between the grids (input -> output), clear of both slot frames
    d.rectangle([82, 81, 88, 86], fill=ARROW)
    d.polygon([(88, 77), (94, 83.5), (88, 90)], fill=ARROW)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote %s" % OUT)


if __name__ == "__main__":
    main()
