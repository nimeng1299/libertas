package neuvillette.libertas;

import gregtech.api.GregTechAPI;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import net.minecraft.item.ItemStack;
import neuvillette.libertas.gui.RecipeMapFront;

public class LiRecipeMaps {
    public static final RecipeMap<RecipeMapBackend> StoneMinerRecipes = RecipeMapBuilder
        .of("libertas.recipe.StoneMinerRecipes", RecipeMapBackend::new)
        .maxIO(1, 12, 0, 0)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW)
        .frontend(RecipeMapFront::new)
        .neiHandlerInfo(b -> b.setDisplayStack(new ItemStack(GregTechAPI.sBlockMachines, 1, 29100)))
        .build();
}
