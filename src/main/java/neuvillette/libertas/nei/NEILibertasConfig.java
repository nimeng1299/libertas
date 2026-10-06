package neuvillette.libertas.nei;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.aspectrecipeindex.nei.AlchemyRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import neuvillette.libertas.blocks.ModBlocks;
import neuvillette.libertas.machines.ModMachines;

/**
 * NEI 插件入口（类名按 NEI 的发现约定命名为 NEI*Config）。
 * 把 Advanced Crucible 控制器注册为 Aspect Recipe Index 坩埚配方页
 * （AlchemyRecipeHandler，id = thaumcraft.alchemy）的处理机器，
 * 把 Runewoven Pact（秘纹织契）控制器注册为注魔配方页
 * （InfusionRecipeHandler，id = thaumcraft.infusion）的处理机器，
 * 把 Wizard Distillation Tower（巫师蒸馏塔）控制器注册为巫术蒸馏配方页
 * （Witchery 自带的 NEIDistilleryRecipeHandler，id = witchery_distilling）的处理机器，
 * 把 Great Wizard Oven（大巫师烤炉）控制器注册为巫师烤炉配方页
 * （Witchery 自带的 NEIWitchesOvenRecipeHandler，id = witchery_cooking）的处理机器，
 * 把 Great Wizard Cauldron（大巫师炼药锅）控制器注册为巫师炼药锅配方页
 * （Witchery 自带的 NEICauldronRecipeHandler，id = witchery_brewing_plus）的处理机器，
 * 把 Advanced Mana Pool（机械魔力池）注册为 Botania 魔力池配方页
 * （Botania 自带的 RecipeHandlerManaPool，id = botania.manaPool）的处理机器：
 * 页面上会与原版机器并列显示，对控制器按 U 可直达该页。
 */
public class NEILibertasConfig implements IConfigureNEI {

    /** Witchery NEIDistilleryRecipeHandler 的 overlay 标识（其源码内硬编码，无公开常量）。 */
    private static final String WITCHERY_DISTILLING = "witchery_distilling";

    /** Witchery NEIWitchesOvenRecipeHandler 的 overlay 标识（其源码内硬编码，无公开常量）。 */
    private static final String WITCHERY_COOKING = "witchery_cooking";

    /** Witchery NEICauldronRecipeHandler 的 overlay 标识（其源码内硬编码，无公开常量）。 */
    private static final String WITCHERY_BREWING_PLUS = "witchery_brewing_plus";

    /** Botania RecipeHandlerManaPool 的 overlay 标识（其源码内硬编码，无公开常量）。 */
    private static final String BOTANIA_MANA_POOL = "botania.manaPool";

    /** Botania RecipeHandlerRunicAltar 的 overlay 标识（其源码内硬编码，无公开常量）。 */
    private static final String BOTANIA_RUNIC_ALTAR = "botania.runicAltar";

    /** Botania RecipeHandlerElvenTrade 的 overlay 标识（其源码内公开常量 RecipeHandlerElvenTrade.OVERLAY）。 */
    private static final String BOTANIA_ELVEN_TRADE = "botania.elvenTrade";

    @Override
    public void loadConfig() {
        if (ModMachines.advancedCrucible != null) {
            API.addRecipeCatalyst(ModMachines.advancedCrucible, AlchemyRecipeHandler.OVERLAY);
        }
        if (ModMachines.runewovenPact != null) {
            API.addRecipeCatalyst(ModMachines.runewovenPact, InfusionRecipeHandler.OVERLAY);
        }
        if (ModMachines.wizardDistillationTower != null) {
            API.addRecipeCatalyst(ModMachines.wizardDistillationTower, WITCHERY_DISTILLING);
        }
        if (ModMachines.greatWizardOven != null) {
            API.addRecipeCatalyst(ModMachines.greatWizardOven, WITCHERY_COOKING);
        }
        if (ModMachines.greatWizardCauldron != null) {
            API.addRecipeCatalyst(ModMachines.greatWizardCauldron, WITCHERY_BREWING_PLUS);
        }
        if (ModBlocks.mechanicalManaPool != null) {
            API.addRecipeCatalyst(new ItemStack(ModBlocks.mechanicalManaPool), BOTANIA_MANA_POOL);
        }
        if (ModBlocks.mechanicalRunicAltar != null) {
            API.addRecipeCatalyst(new ItemStack(ModBlocks.mechanicalRunicAltar), BOTANIA_RUNIC_ALTAR);
        }
        if (ModBlocks.miniElfPortal != null) {
            API.addRecipeCatalyst(new ItemStack(ModBlocks.miniElfPortal), BOTANIA_ELVEN_TRADE);
        }
    }

    @Override
    public String getName() {
        return "Libertas NEI Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }
}
