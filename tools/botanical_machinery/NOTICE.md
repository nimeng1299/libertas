# Botanical Machinery assets (vendored)

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

Attribution: model geometry by Noobanidus / ChaoticTrials (Botanical Machinery,
Apache-2.0); Botania textures by Vazkii (Botania License, botaniamod.net).
