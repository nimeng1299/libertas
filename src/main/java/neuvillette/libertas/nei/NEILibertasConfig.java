package neuvillette.libertas.nei;

import com.gtnewhorizons.aspectrecipeindex.nei.AlchemyRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import neuvillette.libertas.machines.ModMachines;

/**
 * NEI 插件入口（类名按 NEI 的发现约定命名为 NEI*Config）。
 * 把 Advanced Crucible 控制器注册为 AspectRecipeIndex 坩埚配方页
 * （AlchemyRecipeHandler，id = thaumcraft.alchemy）的处理机器，
 * 把 Runewoven Pact（秘纹织契）控制器注册为注魔配方页
 * （InfusionRecipeHandler，id = thaumcraft.infusion）的处理机器：
 * 页面上会与原版机器并列显示，对控制器按 U 可直达该页。
 */
public class NEILibertasConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        if (ModMachines.advancedCrucible != null) {
            API.addRecipeCatalyst(ModMachines.advancedCrucible, AlchemyRecipeHandler.OVERLAY);
        }
        if (ModMachines.runewovenPact != null) {
            API.addRecipeCatalyst(ModMachines.runewovenPact, InfusionRecipeHandler.OVERLAY);
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
