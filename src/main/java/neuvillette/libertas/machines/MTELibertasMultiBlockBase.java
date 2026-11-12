package neuvillette.libertas.machines;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
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

    /** NEI 预览里是否已经用创造式搭建放过结构（BR 会反复调用 survivalConstruct）。 */
    private boolean previewConstructed;

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

    /**
     * 是否运行在 BlockRenderer6343 的 NEI 预览假世界（{@code blockrenderer6343.client.world.TrackedDummyWorld}）里。
     *
     * <p>
     * NEI 预览走的是<b>生存建造</b>（{@code survivalConstruct}）：要用 {@code IItemSource} 提供物品，而且结构元素返回
     * {@code PlaceResult.REJECT} 时 StructureLib 的遍历会<b>整轮终止</b>（{@code SurvivalBuildStructureWalker}）。
     * 所以只要结构里有"只能手放"或"没有物品形态"的方块（台座、符文矩阵、火焰……），预览就会只剩控制器或者搭到一半。
     *
     * <p>
     * 预览只要求"把结构画出来"，不要求真的消耗物品，因此多方块可以据此退回<b>创造式搭建</b>
     * （{@link #construct(ItemStack, boolean)}，它走 {@code placeBlock}，忽略返回值、不需要物品），
     * 既不影响游戏内建造，也不影响结构检查。
     */
    protected boolean isInStructurePreview() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        return world != null && world.getClass()
            .getName()
            .startsWith("blockrenderer6343.");
    }

    /**
     * 预览世界里的结构搭建：用创造式搭建把结构放一次，然后告诉调用方"已完成"。
     *
     * <p>
     * BlockRenderer6343 的循环是 {@code do { survivalConstruct(...) } while (world.hasChanged())}，而创造式搭建走的是
     * {@code placeBlock}，假世界里每次 {@code setBlock} 都会把 {@code hasChanged} 置真（不比较旧方块），
     * 所以这里必须只搭一次，否则会一直"有变化"撞到 2000 轮上限。
     *
     * @return 与 {@code survivalConstruct} 一致：-1 表示结构已完成
     */
    protected int previewConstruct(ItemStack stackSize) {
        if (!previewConstructed) {
            previewConstructed = true;
            construct(stackSize, false);
        }
        return -1;
    }
}
