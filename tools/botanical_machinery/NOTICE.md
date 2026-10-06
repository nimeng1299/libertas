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

- `runeAltar0/1/2.png`: Botania 1.7.10 runic altar textures, embedded into the
  generated `blockbench/mechanicalRunicAltar.bbmodel` for preview; the runtime
  model references `botania:blocks/runeAltar0/1/2` directly.

Attribution: model geometry by Noobanidus / ChaoticTrials (Botanical Machinery,
Apache-2.0); Botania textures by Vazkii (Botania License, botaniamod.net).
