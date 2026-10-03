package neuvillette.libertas.machines;

import org.jetbrains.annotations.NotNull;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.modularui2.GTGuiTheme;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

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
}
