package neuvillette.libertas.machines;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.modularui2.GTGuiTheme;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import neuvillette.libertas.util.LibertasTooltips;

/**
 * Libertas 多方块机器统一基类：所有多方块控制器继承它即可自动获得
 * 未来科技风 GUI（{@link ModGuiThemes#LIBERTAS_MACHINE} 主题 + 显式面板背景兜底）。
 *
 * <p>
 * 需要差异化外观时再覆写 {@link #getGuiTheme()} / {@link #getGui()}。
 */
public abstract class MTELibertasMultiBlockBase<T extends MTELibertasMultiBlockBase<T>>
    extends MTEEnhancedMultiBlockBase<T> {

    public MTELibertasMultiBlockBase(String aName) {
        super(aName);
    }

    public MTELibertasMultiBlockBase(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    public GTGuiTheme getGuiTheme() {
        return ModGuiThemes.LIBERTAS_MACHINE;
    }

    /**
     * 主题的面板背景在部分环境下不生效（主题 JSON 间接解析），这里在 GUI 构建时显式指定背景贴图兜底。
     */
    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return ModGuiThemes.createMultiblockGui(this);
    }

    /**
     * 物品形态的 tooltip 追加署名行："由 <i>Libertas</i> 添加"（中间的 Libertas 为彩虹斜体）。
     *
     * <p>
     * GT 的 {@code ItemMachines#addInformation} 会在多方块描述之后调用本方法，因此放在基类里即可覆盖
     * 所有继承 {@link MTELibertasMultiBlockBase} 的多方块控制器，新增多方块无需重复实现。
     */
    @Override
    public void addAdditionalTooltipInformation(ItemStack aStack, List<String> aTooltip) {
        LibertasTooltips.addCredit(aTooltip);
    }
}
