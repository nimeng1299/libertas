"""Generate the 区块重塑机 (chunk reshaper) block model, block textures and GUI progress bar.

- `textures/blocks/chunk_reshaper.png`        机身面板 (16x16)
- `textures/blocks/chunk_reshaper_screen.png` 正面屏幕与指示灯 (16x16)
- `textures/gui/chunk_reshaper_progress.png`  进度条：上半 8px 空槽 + 下半 8px 满条 (182x16)
- `models/block/chunkReshaper.json`           Blockbench 风格的方块模型

The model is authored front-towards-north and rendered by JsonBlockRenderer / JsonItemRenderer,
so the JSON only needs elements + full-face UVs.

Requires Pillow. Run from anywhere: python blockbench/generate_chunk_reshaper.py
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/libertas"

CASE_TOP = (70, 83, 107, 255)
CASE_LIGHT = (52, 62, 80, 255)
CASE_MID = (42, 50, 66, 255)
CASE_DARK = (26, 31, 41, 255)
CASE_DEEP = (18, 22, 30, 255)
RIVET = (108, 126, 155, 255)
CYAN = (86, 226, 244, 255)
CYAN_DIM = (34, 132, 150, 255)

SCREEN_BG = (6, 12, 16, 255)
SCREEN_FRAME = (31, 110, 122, 255)
SCREEN_LINE = (16, 43, 51, 255)
SCREEN_BRIGHT = (76, 224, 240, 255)

BLOCK_MODEL = "block/chunkReshaper"
SIDES = ("north", "south", "east", "west", "up", "down")


def casing_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16), CASE_MID)
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                # 上/左高光, 下/右阴影, 让机身有倒角
                pixels[x, y] = CASE_DARK if (x == 15 or y == 15) else CASE_LIGHT
            elif edge == 1:
                pixels[x, y] = CASE_LIGHT if (x <= 1 or y <= 1) else CASE_DARK
            else:
                pixels[x, y] = CASE_MID
    # 四角铆钉
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        pixels[x, y] = RIVET
    # 中央散热格栅
    for y in (5, 7, 9, 11):
        for x in range(5, 11):
            pixels[x, y] = CASE_DEEP
        for x in range(5, 11):
            pixels[x, y - 1] = CASE_TOP
    # 格栅两侧的青色能量槽
    for y in range(4, 12):
        pixels[2, y] = CYAN_DIM
        pixels[13, y] = CYAN_DIM
    return image


def screen_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16), SCREEN_BG)
    pixels = image.load()
    for i in range(16):
        pixels[i, 0] = SCREEN_FRAME
        pixels[i, 15] = SCREEN_FRAME
        pixels[0, i] = SCREEN_FRAME
        pixels[15, i] = SCREEN_FRAME
    for y in (3, 5, 7, 9, 11, 13):
        for x in range(2, 14):
            pixels[x, y] = SCREEN_LINE
    # 区块"剖面"折线
    for x, y in ((2, 11), (3, 10), (4, 9), (5, 10), (6, 11), (7, 9), (8, 6), (9, 4), (10, 5), (11, 8), (12, 9), (13, 10)):
        pixels[x, y] = SCREEN_BRIGHT
    # 左上角像素供机身指示灯取样
    pixels[0, 0] = SCREEN_BRIGHT
    pixels[1, 0] = SCREEN_BRIGHT
    pixels[0, 1] = SCREEN_BRIGHT
    pixels[1, 1] = SCREEN_BRIGHT
    return image


def progress_texture(width: int = 182, height: int = 8) -> Image.Image:
    image = Image.new("RGBA", (width, height * 2), (10, 16, 22, 255))
    pixels = image.load()
    # 上半: 空槽
    for y in range(height):
        for x in range(width):
            if y == 0 or y == height - 1:
                pixels[x, y] = (47, 74, 86, 255)
            elif x == 0 or x == width - 1:
                pixels[x, y] = (40, 63, 74, 255)
            else:
                pixels[x, y] = (10, 16, 22, 255)
    # 下半: 满条 (竖向渐变, 裁切到任意宽度都好看)
    gradient = [
        (196, 252, 255, 255),
        (140, 244, 255, 255),
        (96, 228, 248, 255),
        (66, 205, 228, 255),
        (48, 178, 202, 255),
        (36, 150, 174, 255),
        (28, 122, 145, 255),
        (22, 96, 116, 255),
    ]
    for y in range(height):
        for x in range(width):
            pixels[x, height + y] = gradient[min(y, len(gradient) - 1)]
    return image


def cube(name: str, start, end, texture: str, uv=(0, 0, 16, 16), sides=SIDES) -> dict:
    return {
        "name": name,
        "from": list(start),
        "to": list(end),
        "faces": {side: {"uv": list(uv), "texture": texture} for side in sides},
    }


def build_model() -> dict:
    north = ("north",)
    return {
        "credit": "Source: blockbench/generate_chunk_reshaper.py",
        "ambientocclusion": False,
        "textures": {
            "0": "libertas:blocks/chunk_reshaper",
            "1": "libertas:blocks/chunk_reshaper_screen",
            "particle": "libertas:blocks/chunk_reshaper",
        },
        "elements": [
            cube("base", (0, 0, 0), (16, 2, 16), "#0"),
            cube("body", (1, 2, 1), (15, 15, 15), "#0"),
            cube("cap", (0, 15, 0), (16, 16, 16), "#0"),
            # 正面屏幕外框与屏幕, 从机身正面 (z=1) 向 -Z 凸出
            cube("screen_bezel", (2, 4, 0.2), (14, 13, 1.05), "#0"),
            cube("screen", (2.5, 4.6, 0.08), (13.5, 12.4, 0.25), "#1", uv=(0, 0, 16, 16), sides=north),
            # 屏幕下方的两枚状态指示灯, 取屏幕贴图左上角的亮青像素
            cube("led_left", (3, 2.4, 0.15), (4, 3.6, 1.0), "#1", uv=(0, 0, 2, 2), sides=north),
            cube("led_right", (12, 2.4, 0.15), (13, 3.6, 1.0), "#1", uv=(0, 0, 2, 2), sides=north),
        ],
    }


def main() -> None:
    textures = ASSETS / "textures"
    textures.mkdir(parents=True, exist_ok=True)
    (textures / "blocks").mkdir(exist_ok=True)
    (textures / "gui").mkdir(exist_ok=True)
    (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)

    casing_texture().save(textures / "blocks/chunk_reshaper.png")
    screen_texture().save(textures / "blocks/chunk_reshaper_screen.png")
    progress_texture().save(textures / "gui/chunk_reshaper_progress.png")

    model_path = ASSETS / "models" / f"{BLOCK_MODEL}.json"
    model_path.write_text(json.dumps(build_model(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {model_path.relative_to(ROOT)} and 3 textures")


if __name__ == "__main__":
    main()
