package neuvillette.libertas.recipe;

import gregtech.api.GregTechAPI;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.registry.GameRegistry;
import neuvillette.libertas.multiblock.MultiBlocks;

/**
 * Crafting recipes for multi-block controllers.
 */
public class ControllerRecipes {

    private ControllerRecipes() {}

    /**
     * Called during FMLPostInitializationEvent (postInit phase).
     */
    public static void registerRecipes() {
        // Stone Miner: Stone in outer ring, Brick Block in center → 1x Stone Miner
        GameRegistry.addShapedRecipe(
            new ItemStack(GregTechAPI.sBlockMachines, 1, MultiBlocks.MAIN_STONE_MINER.getId()),
            "SSS",
            "SBS",
            "SSS",
            'S', Blocks.stone,
            'B', Blocks.brick_block);
    }
}
