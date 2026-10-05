package neuvillette.libertas.nei;

import com.gtnewhorizons.aspectrecipeindex.nei.AlchemyRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
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
 * （Witchery 自带的 NEIWitchesOvenRecipeHandler，id = witchery_cooking）的处理机器：
 * 页面上会与原版机器并列显示，对控制器按 U 可直达该页。
 */
public class NEILibertasConfig implements IConfigureNEI {

    /** Witchery NEIDistilleryRecipeHandler 的 overlay 标识（其源码内硬编码，无公开常量）。 */
    private static final String WITCHERY_DISTILLING = "witchery_distilling";

    /** Witchery NEIWitchesOvenRecipeHandler 的 overlay 标识（其源码内硬编码，无公开常量）。 */
    private static final String WITCHERY_COOKING = "witchery_cooking";

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
