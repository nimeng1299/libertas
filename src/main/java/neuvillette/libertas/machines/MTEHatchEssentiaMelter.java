package neuvillette.libertas.machines;

import static gregtech.api.enums.Textures.BlockIcons.ITEM_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import java.util.List;
import java.util.StringJoiner;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
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
 * 要素不通过管道对外输出（{@link #allowPullStack} 恒 false），只供读取方（控制器逻辑或 WAILA/GUI 查看）使用。
 */
public class MTEHatchEssentiaMelter extends MTEHatch {

    public static final int INPUT_SLOT = 0;
    public static final int MAX_PER_ASPECT = 64;
    /** 转化周期：每秒尝试熔解槽内第一个物品一件。 */
    public static final int MELT_INTERVAL = 20;

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
    // 内容展示：WAILA（服务器端）+ GUI（StringSyncValue 同步）
    // -----------------------------------------------------------------------

    private String getEssentiaSummary(int maxAspects) {
        if (storedEssentia.size() == 0) {
            return StatCollector.translateToLocal("libertas.essentia_hatch.empty");
        }
        final StringJoiner joiner = new StringJoiner(", ");
        int shown = 0;
        for (Aspect aspect : storedEssentia.getAspects()) {
            if (shown++ >= maxAspects) {
                joiner.add(EnumChatFormatting.GRAY + "+" + (storedEssentia.size() - maxAspects));
                break;
            }
            final int amount = storedEssentia.getAmount(aspect);
            final String name = StatCollector.translateToLocal("aspect." + aspect.getTag());
            joiner.add(aspect.getChatcolor() + name + EnumChatFormatting.GRAY + " x" + amount);
        }
        return joiner.toString();
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        currenttip.add(getEssentiaSummary(Integer.MAX_VALUE));
    }

    /**
     * 背景贴图 textures/gui/background/essentia_melter.png（blockbench/essentiaMelterGui.bbmodel）：
     * 槽孔位于 (14,33)，要素屏位于 (40,26)-(164,72)，控件绝对定位与之对齐。
     */
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        final StringSyncValue essentiaText = new StringSyncValue(() -> getEssentiaSummary(4));
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
                    .top(31)
                    .left(44));
    }
}
