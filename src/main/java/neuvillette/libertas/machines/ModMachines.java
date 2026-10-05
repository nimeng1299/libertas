package neuvillette.libertas.machines;

import net.minecraft.item.ItemStack;

import gregtech.api.recipe.check.CheckRecipeResultRegistry;

/**
 * 在 GT 的 load 阶段窗口内（GT preInit 开始之后、postInit 之前）构造 MetaTileEntity 完成注册。
 * ID 必须全局唯一，当前运行环境里 18000-19999 段空闲。
 */
public final class ModMachines {

    public static ItemStack advancedCrucible;
    public static ItemStack essentiaMelter;
    public static ItemStack runewovenPact;
    public static ItemStack greatWizardOven;
    public static ItemStack wizardDistillationTower;

    private ModMachines() {}

    public static void init() {
        advancedCrucible = new MTEAdvancedCrucible(19000, "multimachine.advancedcrucible", "Advanced Crucible")
            .getStackForm(1L);
        essentiaMelter = new MTEHatchEssentiaMelter(19001, "hatch.essentiamelter", "Essentia Melter Hatch")
            .getStackForm(1L);
        runewovenPact = new MTERunewovenPact(19002, "multimachine.runewovenpact", "秘纹织契").getStackForm(1L);
        greatWizardOven = new MTEGreatWizardOven(19003, "multimachine.greatwizardoven", "大巫师烤炉").getStackForm(1L);
        wizardDistillationTower = new MTEWizardDistillationTower(19004, "multimachine.wizarddistillationtower", "巫师蒸馏塔")
            .getStackForm(1L);
        ModGuiThemes.init();
        // 自定义配方检查失败原因（GUI 状态区显示），必须先注册才能被同步显示
        CheckRecipeResultRegistry.register(new ResultInsufficientEssentia());
        // 大巫师烤炉的"缺少空黏土罐"用 SimpleCheckRecipeResult：GT 已按 "simple_result" ID
        // 注册过该类型（key 随 NBT 同步），不能再重复注册，只需补本地化键
    }
}
