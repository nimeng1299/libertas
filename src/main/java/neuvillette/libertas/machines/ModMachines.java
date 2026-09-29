package neuvillette.libertas.machines;

import net.minecraft.item.ItemStack;

/**
 * 在 GT 的 load 阶段窗口内（GT preInit 开始之后、postInit 之前）构造 MetaTileEntity 完成注册。
 * ID 必须全局唯一，当前运行环境里 18000-19999 段空闲。
 */
public final class ModMachines {

    public static ItemStack advancedCrucible;

    private ModMachines() {}

    public static void init() {
        advancedCrucible = new MTEAdvancedCrucible(19000, "multimachine.advancedcrucible", "Advanced Crucible")
            .getStackForm(1L);
    }
}
