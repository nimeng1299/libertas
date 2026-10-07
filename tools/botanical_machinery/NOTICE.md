# Botanical Machinery assets (vendored)

- `mechanical_runic_altar.json`: verbatim copy of the Mechanical Runic Altar
  block model from the same repository, used by
  `tools/gen_mechanical_runic_altar_model.py` for the 机械符文祭坛
  (mechanicalRunicAltar) geometry.
- `mechanical_mana_pool.json`: verbatim copy of the Mechanical Mana Pool block
  model from [ChaoticTrials/BotanicalMachinery](https://github.com/ChaoticTrials/BotanicalMachinery)
  (Apache License 2.0), used by `tools/gen_mechanical_mana_pool_model.py` as the
  single source of truth for the 机械魔力池 (mechanicalManaPool) geometry.
- `dragonstone_block_reference.png`: Botania 1.18 `dragonstone_block` texture,
  kept only as the palette reference the frame texture is painted from
  (the runtime texture is painted, not copied).
- `livingrock0.png`: Botania 1.7.10 `livingrock0` texture, embedded into the
  generated `blockbench/mechanicalManaPool.bbmodel` so the pool part previews
  correctly in Blockbench. The runtime model references `botania:blocks/livingrock0`
  directly (resolved from the installed Botania jar; not distributed here).

- `livingwood0.png` / `alfheimPortal0.png`: Botania 1.7.10 textures, embedded
  into the generated `blockbench/miniElfPortal.bbmodel` for preview; the runtime
  model references `botania:blocks/livingwood0` / `botania:blocks/alfheimPortal0`
  directly.
- `industrial_agglomeration_factory.json`: verbatim copy of the Industrial
  Agglomeration Factory block model from the same repository, used by
  `tools/gen_industrial_agglomeration_plate_model.py` for the 工业凝聚板
  (industrialAgglomerationPlate) geometry.
- `lapis_block.png`: vanilla Minecraft lapis block texture, embedded into the
  generated bbmodel for preview; the runtime model references
  `minecraft:blocks/lapis_block` directly.
- `terraPlate0/1/2.png`: Botania 1.7.10 terrestrial agglomeration plate
  textures, embedded into the generated bbmodel for preview; the runtime model
  references `botania:blocks/terraPlate1` (top) / `terraPlate2` (side) directly.
- `mechanical_apothecary.json`: verbatim copy of the Mechanical Apothecary
  block model from the same repository, used by
  `tools/gen_mechanical_apothecary_model.py` for the 机械花药台
  (mechanicalApothecary) geometry.
- `mechanical_daisy.json`: verbatim copy of the Mechanical Daisy block model
  from the same repository, used by `tools/gen_mechanical_daisy_model.py` for
  the 机械白雏菊 (mechanicalDaisy) geometry.
- `enchantedSoil0/1.png` / `puredaisy.png`: Botania 1.7.10 textures, embedded
  into the generated bbmodel for preview; the runtime model references
  `botania:blocks/enchantedSoil0/1` / `botania:blocks/puredaisy` directly.
- `runeAltar0/1/2.png`: Botania 1.7.10 runic altar textures, embedded into the
  generated `blockbench/mechanicalRunicAltar.bbmodel` for preview; the runtime
  model references `botania:blocks/runeAltar0/1/2` directly.

Attribution: model geometry by Noobanidus / ChaoticTrials (Botanical Machinery,
Apache-2.0); Botania textures by Vazkii (Botania License, botaniamod.net).
