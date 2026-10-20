"""Regenerate the wireless controller/card Blockbench projects, game models and pixel textures.

Requires Pillow. Coordinates use the project's existing Java-block JSON renderer (0..16 units).
"""

from __future__ import annotations

import base64
import io
import json
import uuid
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/libertas"
SIDES = ("north", "east", "south", "west", "up", "down")
COLORS = {
    "shell": "#26394e",
    "edge": "#42657b",
    "dark": "#101c2e",
    "cyan": "#5cffe2",
    "screen": "#173d4a",
    "gold": "#ffd382",
    "silver": "#a4c9d0",
    "blue": "#3185ae",
}


def save_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def palette_texture() -> Image.Image:
    image = Image.new("RGBA", (64, 64), COLORS["dark"])
    draw = ImageDraw.Draw(image)
    for index, color in enumerate(COLORS.values()):
        draw.rectangle((index * 8, 0, index * 8 + 7, 7), fill=color)
    # Front display: a waveform and radio link indicator, authored as pixel art.
    draw.rectangle((0, 16, 63, 47), fill=COLORS["screen"])
    draw.rectangle((2, 18, 61, 45), outline=COLORS["edge"])
    draw.line([(5, 35), (16, 35), (20, 28), (25, 39), (30, 24), (35, 35), (57, 35)],
              fill=COLORS["cyan"], width=2)
    for x, height in ((44, 3), (49, 6), (54, 9)):
        draw.rectangle((x, 21 + 9 - height, x + 2, 30), fill=COLORS["cyan"])
    draw.rectangle((5, 22, 16, 24), fill=COLORS["gold"])
    draw.rectangle((5, 41, 39, 42), fill=COLORS["blue"])
    return image


def uv_for(color: str) -> list[float]:
    index = list(COLORS).index(color)
    return [(index * 8 + 3) / 4, 0.75, (index * 8 + 5) / 4, 1.25]


def cube(name: str, start: list[float], end: list[float], color: str,
         front_screen: bool = False) -> dict:
    faces = {side: {"uv": uv_for(color), "texture": "#0"} for side in SIDES}
    if front_screen:
        faces["north"]["uv"] = [0, 4, 16, 12]
    return {"name": name, "from": start, "to": end, "faces": faces}


def export(name: str, kind: str, cubes: list[dict], texture: Image.Image, texture_name: str) -> None:
    texture_path = ASSETS / f"textures/{texture_name}.png"
    texture_path.parent.mkdir(parents=True, exist_ok=True)
    texture.save(texture_path)
    model = {
        "credit": f"Source: blockbench/{name}.bbmodel; generator: blockbench/generate_wireless_models.py",
        "ambientocclusion": False,
        "textures": {"0": f"libertas:{texture_name}", "particle": f"libertas:{texture_name}"},
        "elements": cubes,
    }
    save_json(ASSETS / f"models/{kind}/{name}.json", model)
    png = io.BytesIO()
    texture.save(png, format="PNG")
    elements = []
    for part in cubes:
        element = dict(part)
        element.update(type="cube", uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, name + "/" + part["name"])),
                       box_uv=False, autouv=0, origin=[8, 8, 8])
        # Blockbench uses native texture pixels; the in-game JSON uses a 0..16 UV space.
        element["faces"] = {
            side: {"uv": [coordinate * texture.width / 16 for coordinate in face["uv"]], "texture": 0}
            for side, face in part["faces"].items()
        }
        elements.append(element)
    save_json(ROOT / f"blockbench/{name}.bbmodel", {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": name,
        "resolution": {"width": texture.width, "height": texture.height},
        "elements": elements,
        "outliner": [element["uuid"] for element in elements],
        "textures": [{
            "name": Path(texture_name).name + ".png", "id": "0", "folder": str(Path(texture_name).parent),
            "namespace": "libertas", "width": texture.width, "height": texture.height,
            "uv_width": texture.width, "uv_height": texture.height,
            "source": "data:image/png;base64," + base64.b64encode(png.getvalue()).decode(),
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, name + "/texture")),
        }],
    })


def main() -> None:
    texture = palette_texture()
    controller = [
        cube("bottom_foot", [0, 0, 0], [16, 2, 16], "dark"),
        cube("lower_trim", [0, 2, 0], [16, 3, 16], "edge"),
        cube("main_housing", [1, 3, 1], [15, 13, 15], "shell"),
        cube("top_trim", [0, 13, 0], [16, 14, 16], "edge"),
        cube("top_cover", [1, 14, 1], [15, 15, 15], "dark"),
        cube("screen_frame", [2, 6, 0.4], [14, 12, 1], "dark"),
        cube("telemetry_display", [2.5, 6.5, 0.3], [13.5, 11.5, 0.5], "screen", True),
        cube("template_slot", [4, 3.8, 0.5], [11, 5.3, 1], "dark"),
        cube("slot_contact", [4.5, 4, 0.35], [10.5, 4.4, 0.5], "gold"),
        cube("status_led", [12, 4, 0.35], [13, 5, 0.5], "cyan"),
        cube("antenna_left", [2, 15, 11], [3, 16, 12], "silver"),
        cube("antenna_right", [13, 15, 11], [14, 16, 12], "silver"),
    ]
    for side, x in (("left", 0.6), ("right", 15)):
        for index in range(4):
            controller.append(cube(f"{side}_vent_{index}", [x, 5 + index * 1.5, 5],
                                   [x + 0.4, 5.5 + index * 1.5, 12], "dark"))
    export("wirelessControlCenter", "block", controller, texture, "blocks/wireless_control_center")
    card = [
        cube("card_body", [3, 0, 4], [13, 1, 12], "dark"),
        cube("circuit_board", [3.5, 1, 4.5], [12.5, 1.25, 11.5], "screen"),
        cube("control_chip", [6, 1.25, 6], [10, 1.6, 10], "edge"),
        cube("chip_light", [7, 1.6, 7], [9, 1.7, 9], "cyan"),
        cube("signal_track", [4, 1.25, 5], [11.5, 1.35, 5.5], "cyan"),
    ]
    for index in range(5):
        card.append(cube(f"contact_{index}", [4 + index * 1.5, 1, 10.5],
                         [4.8 + index * 1.5, 1.3, 12], "gold"))
    export("wirelessConfigurationCard", "item", card, texture, "items/wireless_configuration_card")
    # Inventory icon fallback for packs that replace the custom item renderer.
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(icon)
    draw.rectangle((2, 3, 13, 12), fill=COLORS["edge"])
    draw.rectangle((3, 4, 12, 11), fill=COLORS["screen"])
    draw.rectangle((6, 5, 9, 8), fill=COLORS["cyan"])
    for x in (4, 6, 8, 10):
        draw.rectangle((x, 10, x, 12), fill=COLORS["gold"])
    # Keep the palette texture used by the 3D card; this separate icon is editable in Blockbench as well.
    icon.save(ASSETS / "textures/items/wireless_configuration_card_icon.png")
    hatch = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(hatch)
    draw.rectangle((2, 2, 13, 13), fill=COLORS["dark"], outline=COLORS["edge"])
    draw.arc((3, 3, 12, 12), 205, 335, fill=COLORS["cyan"], width=1)
    draw.arc((5, 6, 10, 11), 205, 335, fill=COLORS["cyan"], width=1)
    draw.rectangle((7, 10, 8, 11), fill=COLORS["gold"])
    draw.rectangle((5, 13, 10, 13), fill=COLORS["cyan"])
    hatch_path = ASSETS / "textures/blocks/machines/linked_wireless_energy.png"
    hatch_path.parent.mkdir(parents=True, exist_ok=True)
    hatch.save(hatch_path)
    print("Exported wireless control center, configuration card and hatch overlay.")


if __name__ == "__main__":
    main()
