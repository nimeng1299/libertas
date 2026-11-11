package neuvillette.libertas.machines;

import net.minecraft.item.ItemStack;

import gregtech.api.recipe.check.CheckRecipeResultRegistry;

/**
 * 在 GT 的 load 阶段窗口内（GT preInit 开始之后、postInit 之前）构造 MetaTileEntity 完成注册。
 * ID 必须全局唯一：多方块控制器占用 24760-24765，仓室（hatch）占用 24766 起的连续段。
 */
public final class ModMachines {

    public static ItemStack advancedCrucible;
    public static ItemStack essentiaMelter;
    public static ItemStack runewovenPact;
    public static ItemStack greatWizardOven;
    public static ItemStack wizardDistillationTower;
    public static ItemStack greatWizardCauldron;
    public static ItemStack miniMining;
    public static ItemStack woodenInputBus;
    public static ItemStack woodenOutputBus;
    public static ItemStack woodenInputHatch;
    public static ItemStack woodenOutputHatch;
    public static ItemStack linkedWirelessEnergyHatch;

    private ModMachines() {}

    public static void init() {
        advancedCrucible = new MTEAdvancedCrucible(24760, "multimachine.advancedcrucible", "Advanced Crucible")
            .getStackForm(1L);
        essentiaMelter = new MTEHatchEssentiaMelter(24766, "hatch.essentiamelter", "Essentia Melter Hatch")
            .getStackForm(1L);
        runewovenPact = new MTERunewovenPact(24761, "multimachine.runewovenpact", "秘纹织契").getStackForm(1L);
        greatWizardOven = new MTEGreatWizardOven(24762, "multimachine.greatwizardoven", "大巫师烤炉").getStackForm(1L);
        wizardDistillationTower = new MTEWizardDistillationTower(24763, "multimachine.wizarddistillationtower", "巫师蒸馏塔")
            .getStackForm(1L);
        greatWizardCauldron = new MTEGreatWizardCauldron(24764, "multimachine.greatwizardcauldron", "大巫师炼药锅")
            .getStackForm(1L);
        miniMining = new MTEMiniMining(24765, "multimachine.minimining", "迷你矿机").getStackForm(1L);
        woodenInputBus = new MTEHatchWoodenInputBus(24767, "hatch.woodeninputbus", "Wooden Input Bus").getStackForm(1L);
        woodenOutputBus = new MTEHatchWoodenOutputBus(24768, "hatch.woodenoutputbus", "Wooden Output Bus")
            .getStackForm(1L);
        woodenInputHatch = new MTEHatchWoodenInput(24769, "hatch.woodeninput", "Wooden Input Hatch").getStackForm(1L);
        woodenOutputHatch = new MTEHatchWoodenOutput(24770, "hatch.woodenoutput", "Wooden Output Hatch")
            .getStackForm(1L);
        linkedWirelessEnergyHatch = new MTEHatchLinkedWirelessEnergy(
            24771,
            "hatch.linkedwirelessenergy",
            "Linked Wireless Energy Hatch").getStackForm(1L);
        ModGuiThemes.init();
        // 自定义配方检查失败原因（GUI 状态区显示），必须先注册才能被同步显示
        CheckRecipeResultRegistry.register(new ResultInsufficientEssentia());
        // 大巫师烤炉的"缺少空黏土罐"用 SimpleCheckRecipeResult：GT 已按 "simple_result" ID
        // 注册过该类型（key 随 NBT 同步），不能再重复注册，只需补本地化键
    }
}
