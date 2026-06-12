package neuvillette.libertas.recipe;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import neuvillette.libertas.LiRecipeMaps;

/**
 * Registers ore-production recipes for the Stone Miner multi-block.
 * These recipes use circuit configurations to determine which ore to produce.
 */
public class StoneMinerRecipes {

    private StoneMinerRecipes() {}

    /**
     * Registers three recipes:
     *   Circuit 1 → 8x Iron Ore   (100 ticks)
     *   Circuit 2 → 8x Copper Ore (100 ticks)
     *   Circuit 3 → 8x Tin Ore    (100 ticks)
     *
     * Called during FMLPostInitializationEvent (postInit phase).
     */
    public static void registerRecipes() {
        // Circuit 1 → Iron Ore
        GTRecipeBuilder.builder()
            .itemOutputs(GTOreDictUnificator.get(OrePrefixes.ore, Materials.Iron, 8))
            .circuit(1)
            .duration(100)
            .eut(0)
            .addTo(LiRecipeMaps.StoneMinerRecipes);

        // Circuit 2 → Copper Ore
        GTRecipeBuilder.builder()
            .itemOutputs(GTOreDictUnificator.get(OrePrefixes.ore, Materials.Copper, 8))
            .circuit(2)
            .duration(100)
            .eut(0)
            .addTo(LiRecipeMaps.StoneMinerRecipes);

        // Circuit 3 → Tin Ore
        GTRecipeBuilder.builder()
            .itemOutputs(GTOreDictUnificator.get(OrePrefixes.ore, Materials.Tin, 8))
            .circuit(3)
            .duration(100)
            .eut(0)
            .addTo(LiRecipeMaps.StoneMinerRecipes);
    }
}
