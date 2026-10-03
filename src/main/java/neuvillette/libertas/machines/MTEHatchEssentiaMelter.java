package neuvillette.libertas.machines;

import static gregtech.api.enums.Textures.BlockIcons.ITEM_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HarvestTool;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTGuiTheme;
import gregtech.api.modularui2.GTGuis;
import gregtech.api.render.TextureFactory;
import gregtech.common.tileentities.machines.ISmartInputHatch;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * 要素熔炼舱：唯一的物品输入槽，服务器端周期性尝试把槽内物品按神秘坩埚规则（getObjectTags + bonusTags，1:1）
 * 熔解为要素并储存。要素不衰减、不自然消失，并写入 TE NBT（区块卸载/重启后保留）；挖掉方块时 TE 被销毁，
 * 掉落物不带要素，内容随之清空。
 *
 * <p>
 * 作为多方块部件（Advanced Crucible）时实现 {@link ISmartInputHatch}：要素变化即时通知控制器重查配方，
 * 且控制器会做周期性检查；要素仅供多方块控制器抽取（{@link #takeAspect}），不通过管道输出。
 */
public class MTEHatchEssentiaMelter extends MTEHatch implements ISmartInputHatch {

    public static final int INPUT_SLOT = 0;
    public static final int MAX_PER_ASPECT = 64;
    /** 转化周期：每秒尝试熔解槽内第一个物品一件。 */
    public static final int MELT_INTERVAL = 20;
    /** 要素屏 (40,26)-(164,72) 内约 46px，字体 9px/行，GUI 最多显示 4 行。 */
    public static final int GUI_MAX_LINES = 4;
    public static final int WAILA_MAX_LINES = 8;

    private final AspectList storedEssentia = new AspectList();

    public MTEHatchEssentiaMelter(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            0,
            1,
            new String[] { "Melts items into Thaumcraft essentia, crucible-style",
                "Essentia is kept until the block is broken", "Stores up to " + MAX_PER_ASPECT + " per aspect" });
    }

    public MTEHatchEssentiaMelter(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 1, aDescription, aTextures);
    }

    @Override
    public MTEHatchEssentiaMelter newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchEssentiaMelter(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public byte getTileEntityBaseType() {
        return HarvestTool.PickaxeLevel0.toTileEntityBaseType();
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    // -----------------------------------------------------------------------
    // GUI：MTEHatch 默认 useMui2()=false 且不覆写 onRightclick，两者都会导致右键无 GUI
    // -----------------------------------------------------------------------

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isClientSide()) return true;
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public GTGuiTheme getGuiTheme() {
        return ModGuiThemes.ESSENTIA_MELTER;
    }

    // -----------------------------------------------------------------------
    // 熔解逻辑
    // -----------------------------------------------------------------------

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && aTick % MELT_INTERVAL == 0) {
            trySmeltOneItem();
        }
    }

    private void trySmeltOneItem() {
        final ItemStack input = mInventory[INPUT_SLOT];
        if (input == null) return;

        // 与 TileCrucible.attemptSmelt 相同的来源：物品注册要素 + bonus 要素，1:1 转化
        AspectList tags = ThaumcraftApiHelper.getObjectAspects(input);
        if (tags != null) tags = ThaumcraftApiHelper.getBonusObjectTags(input, tags);
        if (tags == null || tags.size() == 0) return;

        // 全部要素都放得下才消耗物品（all-or-nothing，不做静默损失）
        for (Aspect aspect : tags.getAspects()) {
            if (storedEssentia.getAmount(aspect) + tags.getAmount(aspect) > MAX_PER_ASPECT) return;
        }
        for (Aspect aspect : tags.getAspects()) {
            storedEssentia.add(aspect, tags.getAmount(aspect));
        }

        if (--input.stackSize <= 0) mInventory[INPUT_SLOT] = null;
        getBaseMetaTileEntity().markDirty();
        // 要素变化即时通知挂接的多方块控制器重查配方
        notifyWatchers();
    }

    // -----------------------------------------------------------------------
    // 要素读取与抽取：供多方块控制器（Advanced Crucible）查询和消耗
    // -----------------------------------------------------------------------

    /** 当前存储的要素只读视图。 */
    public AspectList getStoredEssentia() {
        return storedEssentia;
    }

    public int getAspectAmount(Aspect aspect) {
        return storedEssentia.getAmount(aspect);
    }

    /**
     * 从本舱抽取指定要素，返回实际抽取量（不超过存量与 max）。
     */
    public int takeAspect(Aspect aspect, int max) {
        int take = Math.min(storedEssentia.getAmount(aspect), max);
        if (take > 0) {
            storedEssentia.remove(aspect, take);
            getBaseMetaTileEntity().markDirty();
        }
        return take;
    }

    @Override
    public boolean needsPeriodicChecks() {
        // 要素缓慢累积（无物品变化事件），控制器需要周期性重查配方
        return true;
    }

    // -----------------------------------------------------------------------
    // 物品自动化：只进不出
    // -----------------------------------------------------------------------

    @Override
    public int[] getAccessibleSlotsFromSide(int ordinalSide) {
        return new int[] { INPUT_SLOT };
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStack, int ordinalSide) {
        return index == INPUT_SLOT;
    }

    @Override
    public boolean canExtractItem(int index, ItemStack itemStack, int ordinalSide) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex == INPUT_SLOT;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    // -----------------------------------------------------------------------
    // NBT：要素随 TE 持久化（区块卸载后保留），方块被挖掉时 TE 销毁即清空
    // -----------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        storedEssentia.writeToNBT(aNBT, "essentia");
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        storedEssentia.readFromNBT(aNBT, "essentia");
    }

    // -----------------------------------------------------------------------
    // 外观：青铜镶砖外壳 + 输入管口指示，与 Advanced Crucible 配色一致
    // -----------------------------------------------------------------------

    @Override
    public ITexture getCasingTexture() {
        return Casings.BronzePlatedBricks.getCasingTexture();
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), TextureFactory.of(ITEM_IN_SIGN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return getTexturesActive(aBaseTexture);
    }

    // -----------------------------------------------------------------------
    // 内容展示：WAILA 正文在客户端合成，服务端数据经 getWailaNBTData -> accessor.getNBTData()
    // 传递（GT5U 电源状态同款模式）；GUI 走 StringSyncValue
    // -----------------------------------------------------------------------

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        storedEssentia.writeToNBT(tag, "essentia");
    }

    private List<String> summarizeLines(AspectList essentia, int maxLines) {
        final List<String> lines = new ArrayList<>();
        if (essentia == null || essentia.size() == 0) {
            lines.add(StatCollector.translateToLocal("libertas.essentia_hatch.empty"));
            return lines;
        }
        int shown = 0;
        for (Aspect aspect : essentia.getAspects()) {
            if (shown++ >= maxLines) {
                lines.add(EnumChatFormatting.GRAY + "...");
                break;
            }
            // Aspect.getName() = tag 首字母大写（TC 自身的显示方式）；getChatcolor() 在部分要素上为 null，不使用
            lines.add(
                EnumChatFormatting.WHITE + aspect.getName()
                    + EnumChatFormatting.GRAY
                    + " x"
                    + essentia.getAmount(aspect));
        }
        return lines;
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        final AspectList essentia = new AspectList();
        essentia.readFromNBT(accessor.getNBTData(), "essentia");
        currenttip.addAll(summarizeLines(essentia, WAILA_MAX_LINES));
    }

    /**
     * 背景贴图 textures/gui/background/essentia_melter.png（blockbench/essentiaMelterGui.bbmodel）：
     * 槽孔位于 (14,33)，要素屏位于 (40,26)-(164,72)，控件绝对定位与之对齐。
     */
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        // TextRenderer.draw 检测到 \n 走多行路径，直接以换行符连接各要素
        final StringSyncValue essentiaText = new StringSyncValue(
            () -> String.join("\n", summarizeLines(storedEssentia, GUI_MAX_LINES)));
        syncManager.syncValue("essentiaText", essentiaText);
        syncManager.registerSlotGroup("item_inv", 0);

        final ItemSlot inputSlot = new ItemSlot()
            .slot(new ModularSlot(inventoryHandler, INPUT_SLOT).slotGroup("item_inv"))
            .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD)
            .top(33)
            .left(14);

        return GTGuis.mteTemplatePanelBuilder(this, guiData, syncManager, uiSettings)
            .build()
            // 主题面板背景在部分环境下不生效，这里显式指定兜底
            .background(ModGuiThemes.MELTER_BACKGROUND)
            .child(inputSlot)
            .child(
                IKey.dynamic(essentiaText::getValue)
                    .asWidget()
                    .top(26)
                    .left(44));
    }
}
