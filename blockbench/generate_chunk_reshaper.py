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

# 米黄色机身 + 青铜描边 + 琥珀色能量读数
CASE_TOP = (241, 232, 209, 255)
CASE_LIGHT = (224, 211, 180, 255)
CASE_MID = (205, 189, 154, 255)
CASE_DARK = (154, 136, 100, 255)
CASE_DEEP = (98, 83, 59, 255)
RIVET = (128, 106, 68, 255)
AMBER = (232, 156, 58, 255)
AMBER_DIM = (186, 120, 48, 255)

SCREEN_BG = (35, 28, 19, 255)
SCREEN_FRAME = (146, 110, 62, 255)
SCREEN_LINE = (62, 49, 33, 255)
SCREEN_BRIGHT = (255, 205, 116, 255)
SCREEN_FILL = (126, 90, 44, 255)

BLOCK_MODEL = "block/chunkReshaper"
SIDES = ("north", "south", "east", "west", "up", "down")


def casing_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16), CASE_MID)
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            # 2px 倒角: 上/左受光, 下/右压暗
            if x <= 1 or y <= 1:
                pixels[x, y] = CASE_TOP if (x == 0 or y == 0) else CASE_LIGHT
            elif x >= 14 or y >= 14:
                pixels[x, y] = CASE_DEEP if (x == 15 or y == 15) else CASE_DARK
            else:
                pixels[x, y] = CASE_MID
    # 四角铆钉
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        pixels[x, y] = RIVET
    # 中央散热格栅: 深色凹槽 + 上方受光棱
    for y in (5, 7, 9, 11):
        for x in range(5, 11):
            pixels[x, y] = CASE_DEEP
    for y in (6, 8, 10):
        for x in range(5, 11):
            pixels[x, y] = CASE_LIGHT
    # 格栅两侧的琥珀色能量槽
    for y in range(4, 12):
        pixels[2, y] = AMBER_DIM
        pixels[13, y] = AMBER_DIM
    for y in (4, 11):
        pixels[2, y] = AMBER
        pixels[13, y] = AMBER
    return image


# 区块剖面的台阶采样: (起始列, 结束列, 地表行)
PLATEAUS = ((2, 4, 11), (5, 7, 9), (8, 10, 10), (11, 13, 7))


def screen_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16), SCREEN_BG)
    pixels = image.load()
    # 青铜外框 + 一圈暗描边
    for i in range(16):
        pixels[i, 0] = SCREEN_FRAME
        pixels[i, 15] = SCREEN_FRAME
        pixels[0, i] = SCREEN_FRAME
        pixels[15, i] = SCREEN_FRAME
    for i in range(1, 15):
        pixels[i, 1] = SCREEN_LINE
        pixels[i, 14] = SCREEN_LINE
        pixels[1, i] = SCREEN_LINE
        pixels[14, i] = SCREEN_LINE
    # 剖面: 地表以下填暗琥珀, 地表与台阶立面用亮琥珀勾边
    previous_top = None
    for x_start, x_end, top in PLATEAUS:
        for x in range(x_start, x_end + 1):
            for y in range(top + 1, 14):
                pixels[x, y] = SCREEN_FILL
            pixels[x, top] = SCREEN_BRIGHT
        if previous_top is not None:
            low, high = sorted((previous_top, top))
            for y in range(low, high + 1):
                pixels[x_start, y] = SCREEN_BRIGHT
        previous_top = top
    # 左上角像素供机身指示灯取样
    for x, y in ((0, 0), (1, 0), (0, 1), (1, 1)):
        pixels[x, y] = SCREEN_BRIGHT
    return image


def progress_texture(width: int = 182, height: int = 8) -> Image.Image:
    image = Image.new("RGBA", (width, height * 2), (48, 39, 27, 255))
    pixels = image.load()
    # 上半: 空槽
    for y in range(height):
        for x in range(width):
            if y == 0 or y == height - 1:
                pixels[x, y] = (139, 108, 64, 255)
            elif x == 0 or x == width - 1:
                pixels[x, y] = (118, 92, 55, 255)
            else:
                pixels[x, y] = (48, 39, 27, 255)
    # 下半: 满条 (竖向渐变, 裁切到任意宽度都好看)
    gradient = [
        (255, 246, 216, 255),
        (250, 230, 176, 255),
        (244, 208, 138, 255),
        (236, 184, 100, 255),
        (224, 162, 72, 255),
        (206, 142, 58, 255),
        (184, 122, 46, 255),
        (162, 104, 38, 255),
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
