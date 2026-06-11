package neuvillette.libertas.recipe;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import bartworks.system.material.WerkstoffLoader;
import gtnhlanth.common.register.BotWerkstoffMaterialPool;


/**
 * GregTech recipe additions for Libertas.
 */
public class GregTechRecipes {

    private GregTechRecipes() {} // utility class

    /**
     * Register all GregTech recipe modifications.
     * Called during FMLInitializationEvent (init phase).
     */
    public static void registerRecipes() {
        addSiliconSolarGradeChemicalRecipes();
        addCobbleToSiliconCentrifugeRecipes();
        addWaterElectrolyzerRecipes();
        addIlmeniteCentrifugeRecipes();
        addIlmeniteIngotCentrifugeRecipes();
        addRutileCentrifugeRecipes();
        addRutileIngotCentrifugeRecipes();
        addTungstateToLithiumMixerRecipes();
        addHuebneritToManganeseMixerRecipes();
        addFerberiteToIronMixerRecipes();
        addScheeliteToCalciumMixerRecipes();
        addIndiumLargeChemicalReactorRecipe();
    }

    /**
     * Helper to safely get an ItemStack for a material with given prefix and amount.
     */
    private static ItemStack safeStack(OrePrefixes prefix, Materials material, long amount) {
        if (material == null || material == Materials._NULL) {
            throw new IllegalStateException(
                "Material not found for " + prefix + " (amount " + amount + "). Is it registered?");
        }
        ItemStack stack = GTOreDictUnificator.get(prefix, material, amount);
        if (stack == null) {
            throw new IllegalStateException("Could not find " + prefix + " of " + material + ". Is GregTech loaded?");
        }
        return stack;
    }

    /**
     * Adds Chemical Reactor and Large Chemical Reactor recipes:
     *   1x Silicon Dust + Circuit (17) → 1x Solar Grade Silicon Dust
     *   Voltage: 120 EU/t, Duration: 300 ticks
     *
     * The same recipe is registered for both the single-block Chemical Reactor
     * and the multi-block Large Chemical Reactor, so players can use either.
     */
    private static void addSiliconSolarGradeChemicalRecipes() {
        ItemStack siliconDust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Silicon, 1);
        ItemStack solarGradeSilicon = GTOreDictUnificator.get(OrePrefixes.dust, Materials.SiliconSG, 1);

        if (siliconDust == null) {
            throw new IllegalStateException("Could not find Silicon dust. Is GregTech loaded?");
        }
        if (solarGradeSilicon == null) {
            throw new IllegalStateException("Could not find Solar Grade Silicon (SiliconSG). Is GregTech loaded?");
        }

        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .itemInputs(siliconDust)
            .itemOutputs(solarGradeSilicon)
            .circuit(17)
            .duration(300)
            .eut(120);

        // Register for both single-block and multi-block chemical reactors
        builder.addTo(RecipeMaps.chemicalReactorRecipes);
        builder.addTo(RecipeMaps.multiblockChemicalReactorRecipes);
    }

    /**
     * Adds Centrifuge / Industrial Centrifuge recipe:
     *   3x Cobblestone + Circuit (17) → 2x Silicon Dust
     *   Voltage: 120 EU/t, Duration: 600 ticks
     *
     * The Industrial Centrifuge (multi-block) uses the same recipe map,
     * so a single registration covers both machines.
     */
    private static void addCobbleToSiliconCentrifugeRecipes() {
        ItemStack cobblestone = new ItemStack(Blocks.cobblestone, 3);
        ItemStack siliconDust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Silicon, 2);

        if (siliconDust == null) {
            throw new IllegalStateException("Could not find Silicon dust. Is GregTech loaded?");
        }

        GTRecipeBuilder.builder()
            .itemInputs(cobblestone)
            .itemOutputs(siliconDust)
            .circuit(17)
            .duration(600)
            .eut(120)
            .addTo(RecipeMaps.centrifugeRecipes);
    }

    /**
     * Adds Electrolyzer recipes for water electrolysis:
     *   1000L Water + Circuit 17 → 1000L Oxygen   (30 EU/t, 2400 ticks)
     *   1000L Water + Circuit 18 → 2000L Hydrogen  (30 EU/t, 2400 ticks)
     */
    private static void addWaterElectrolyzerRecipes() {
        // Recipe 1: Water → Oxygen (Circuit 17)
        GTRecipeBuilder.builder()
            .fluidInputs(Materials.Water.getFluid(1000))
            .fluidOutputs(Materials.Oxygen.getGas(1000))
            .circuit(17)
            .duration(2400)
            .eut(30)
            .addTo(RecipeMaps.electrolyzerRecipes);

        // Recipe 2: Water → Hydrogen (Circuit 18)
        GTRecipeBuilder.builder()
            .fluidInputs(Materials.Water.getFluid(1000))
            .fluidOutputs(Materials.Hydrogen.getGas(2000))
            .circuit(18)
            .duration(2400)
            .eut(30)
            .addTo(RecipeMaps.electrolyzerRecipes);
    }

    /**
     * Adds Centrifuge / Industrial Centrifuge recipe:
     *   33x Ilmenite Dust + Circuit (17) → 22x Titanium, 10x Manganese, 12x Magnesium,
     *   49x Iron, 4x Tantalum, 1x Niobium Dust + 64000L Oxygen
     *   Voltage: 500 EU/t, Duration: 1200 ticks
     */
    private static void addIlmeniteCentrifugeRecipes() {
        ItemStack ilmenite = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ilmenite, 33);

        if (ilmenite == null) {
            throw new IllegalStateException("Could not find Ilmenite dust. Is GregTech loaded?");
        }

        GTRecipeBuilder.builder()
            .itemInputs(ilmenite)
            .itemOutputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Titanium, 22),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Manganese, 10),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Magnesium, 12),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Iron, 49),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tantalum, 4),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Niobium, 1)
            )
            .fluidOutputs(Materials.Oxygen.getGas(64000))
            .circuit(17)
            .duration(1200)
            .eut(500)
            .addTo(RecipeMaps.centrifugeRecipes);
    }

    /**
     * Adds Centrifuge / Industrial Centrifuge recipe:
     *   3x Rutile Dust + Circuit (17) → 1x Titanium Dust + 2000L Oxygen
     *   Voltage: 500 EU/t, Duration: 200 ticks
     */
    private static void addRutileCentrifugeRecipes() {
        ItemStack rutile = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Rutile, 3);

        if (rutile == null) {
            throw new IllegalStateException("Could not find Rutile dust. Is GregTech loaded?");
        }

        GTRecipeBuilder.builder()
            .itemInputs(rutile)
            .itemOutputs(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Titanium, 1))
            .fluidOutputs(Materials.Oxygen.getGas(2000))
            .circuit(17)
            .duration(200)
            .eut(500)
            .addTo(RecipeMaps.centrifugeRecipes);
    }

    /**
     * Same as above but outputs Titanium Ingot instead of dust (Circuit 18):
     *   33x Ilmenite Dust + Circuit (18) → 22x Titanium Ingot, 10x Manganese, 12x Magnesium,
     *   49x Iron, 4x Tantalum, 1x Niobium Dust + 64000L Oxygen
     *   Voltage: 500 EU/t, Duration: 1200 ticks
     */
    private static void addIlmeniteIngotCentrifugeRecipes() {
        ItemStack ilmenite = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ilmenite, 33);

        if (ilmenite == null) {
            throw new IllegalStateException("Could not find Ilmenite dust. Is GregTech loaded?");
        }

        GTRecipeBuilder.builder()
            .itemInputs(ilmenite)
            .itemOutputs(
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Titanium, 22),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Manganese, 10),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Magnesium, 12),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Iron, 49),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tantalum, 4),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Niobium, 1)
            )
            .fluidOutputs(Materials.Oxygen.getGas(64000))
            .circuit(18)
            .duration(1200)
            .eut(500)
            .addTo(RecipeMaps.centrifugeRecipes);
    }

    /**
     * Same as above but outputs Titanium Ingot instead of dust (Circuit 18):
     *   3x Rutile Dust + Circuit (18) → 1x Titanium Ingot + 2000L Oxygen
     *   Voltage: 500 EU/t, Duration: 200 ticks
     */
    private static void addRutileIngotCentrifugeRecipes() {
        ItemStack rutile = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Rutile, 3);

        if (rutile == null) {
            throw new IllegalStateException("Could not find Rutile dust. Is GregTech loaded?");
        }

        GTRecipeBuilder.builder()
            .itemInputs(rutile)
            .itemOutputs(GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Titanium, 1))
            .fluidOutputs(Materials.Oxygen.getGas(2000))
            .circuit(18)
            .duration(200)
            .eut(500)
            .addTo(RecipeMaps.centrifugeRecipes);
    }

    // ============================================================
    // Mixer / Large Mixer recipes
    // ============================================================

    /**
     * Adds Mixer / Large Mixer recipe:
     *   7x Tungstate Dust + 8000L Hydrogen + Circuit (17) → 2x Lithium Dust + 1x Tungsten Dust
     *   Voltage: 1920 EU/t, Duration: 200 ticks
     */
    private static void addTungstateToLithiumMixerRecipes() {
        ItemStack tungstate = safeStack(OrePrefixes.dust, Materials.Tungstate, 7);
        ItemStack lithium = safeStack(OrePrefixes.dust, Materials.Lithium, 2);
        ItemStack tungsten = safeStack(OrePrefixes.dust, Materials.Tungsten, 1);

        GTRecipeBuilder.builder()
            .itemInputs(tungstate)
            .itemOutputs(lithium, tungsten)
            .fluidInputs(Materials.Hydrogen.getGas(8000))
            .circuit(17)
            .duration(200)
            .eut(1920)
            .addTo(RecipeMaps.mixerRecipes);
    }

    /**
     * Adds Mixer / Large Mixer recipe:
     *   6x Huebnerit Dust + 8000L Hydrogen + Circuit (17) → 1x Manganese Dust + 1x Tungsten Dust
     *   Voltage: 1920 EU/t, Duration: 200 ticks
     */
    private static void addHuebneritToManganeseMixerRecipes() {
        ItemStack huebnerit = safeStack(OrePrefixes.dust, WerkstoffLoader.Huebnerit.getGTMaterial(), 6);
        ItemStack manganese = safeStack(OrePrefixes.dust, Materials.Manganese, 1);
        ItemStack tungsten = safeStack(OrePrefixes.dust, Materials.Tungsten, 1);

        GTRecipeBuilder.builder()
            .itemInputs(huebnerit)
            .itemOutputs(manganese, tungsten)
            .fluidInputs(Materials.Hydrogen.getGas(8000))
            .circuit(17)
            .duration(200)
            .eut(1920)
            .addTo(RecipeMaps.mixerRecipes);
    }

    /**
     * Adds Mixer / Large Mixer recipe:
     *   6x Ferberite Dust + 8000L Hydrogen + Circuit (17) → 1x Iron Dust + 1x Tungsten Dust
     *   Voltage: 1920 EU/t, Duration: 200 ticks
     */
    private static void addFerberiteToIronMixerRecipes() {
        ItemStack ferberite = safeStack(OrePrefixes.dust, WerkstoffLoader.Ferberite.getGTMaterial(), 6);
        ItemStack iron = safeStack(OrePrefixes.dust, Materials.Iron, 1);
        ItemStack tungsten = safeStack(OrePrefixes.dust, Materials.Tungsten, 1);

        GTRecipeBuilder.builder()
            .itemInputs(ferberite)
            .itemOutputs(iron, tungsten)
            .fluidInputs(Materials.Hydrogen.getGas(8000))
            .circuit(17)
            .duration(200)
            .eut(1920)
            .addTo(RecipeMaps.mixerRecipes);
    }

    /**
     * Adds Mixer / Large Mixer recipe:
     *   6x Scheelite Dust + 8000L Hydrogen + Circuit (17) → 1x Calcium Dust + 1x Tungsten Dust
     *   Voltage: 1920 EU/t, Duration: 200 ticks
     */
    private static void addScheeliteToCalciumMixerRecipes() {
        ItemStack scheelite = safeStack(OrePrefixes.dust, Materials.Scheelite, 6);
        ItemStack calcium = safeStack(OrePrefixes.dust, Materials.Calcium, 1);
        ItemStack tungsten = safeStack(OrePrefixes.dust, Materials.Tungsten, 1);

        GTRecipeBuilder.builder()
            .itemInputs(scheelite)
            .itemOutputs(calcium, tungsten)
            .fluidInputs(Materials.Hydrogen.getGas(8000))
            .circuit(17)
            .duration(200)
            .eut(1920)
            .addTo(RecipeMaps.mixerRecipes);
    }

    // ============================================================
    // Large Chemical Reactor recipes
    // ============================================================

    /**
     * Adds Large Chemical Reactor recipe:
     *   27x Galena Dust + 9x Sphalerite Dust + 36x Aluminium Dust + Circuit (17)
     *   + 18000L Water + 108000L Oxygen
     *   → 1x Indium Dust + 9x Silver Dust + 9x Zinc Dust + 27x Lead Dust
     *   Voltage: 480 EU/t, Duration: 600 ticks
     */
    private static void addIndiumLargeChemicalReactorRecipe() {
        ItemStack galena = safeStack(OrePrefixes.dust, Materials.Galena, 27);
        ItemStack sphalerite = safeStack(OrePrefixes.dust, Materials.Sphalerite, 9);
        ItemStack aluminium = safeStack(OrePrefixes.dust, Materials.Aluminium, 36);
        ItemStack indium = safeStack(OrePrefixes.dust, Materials.Indium, 1);
        ItemStack silver = safeStack(OrePrefixes.dust, Materials.Silver, 9);
        ItemStack zinc = safeStack(OrePrefixes.dust, Materials.Zinc, 9);
        ItemStack lead = safeStack(OrePrefixes.dust, Materials.Lead, 27);

        GTRecipeBuilder.builder()
            .itemInputs(galena, sphalerite, aluminium)
            .itemOutputs(indium, silver, zinc, lead)
            .fluidInputs(Materials.Water.getFluid(18000), Materials.Oxygen.getGas(108000))
            .circuit(17)
            .duration(600)
            .eut(480)
            .addTo(RecipeMaps.multiblockChemicalReactorRecipes);
    }
}
