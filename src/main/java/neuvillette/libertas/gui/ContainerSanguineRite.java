package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import neuvillette.libertas.blocks.TileSanguineRite;

/// 猩红祭仪容器。
///
/// 槽位布局 (与 assets/libertas/textures/gui/sanguineRite.png 一一对应,
/// 由 tools/gen_sanguine_rite_gui.py 生成):
///
/// ```text
/// 0 输入槽 x 44, y 30
/// 1 输出槽 x 116, y 30
/// 2..28 玩家背包 3x9 x 8, y 84
/// 29..37 快捷栏 1x9 x 8, y 142
/// ```
public class ContainerSanguineRite extends Container {

    /// GUI 上玩家背包第一格的槽位下标 (inventorySlots 列表里)
    public static final int PLAYER_SLOT_START = TileSanguineRite.TOTAL_SLOTS;
    /// 玩家背包/快捷栏的起始 x (贴图同款; 与机器槽位的 x 无关)
    public static final int PLAYER_X = 8;
    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 30;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 30;
    public static final int PLAYER_INV_Y = 84;
    public static final int HOTBAR_Y = 142;
    /// 输入/输出槽之间的 NEI 箭头热区 (与背景贴图中的箭头一致)
    public static final int ARROW_X1 = 80;
    public static final int ARROW_X2 = 96;
    public static final int ARROW_Y1 = 31;
    public static final int ARROW_Y2 = 47;

    private final TileSanguineRite tile;

    public ContainerSanguineRite(InventoryPlayer playerInventory, TileSanguineRite tile) {
        this.tile = tile;

        addSlotToContainer(new Slot(tile, TileSanguineRite.SLOT_INPUT, INPUT_X, INPUT_Y));
        addSlotToContainer(new SlotOutput(tile, TileSanguineRite.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(
                    new Slot(playerInventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    public TileSanguineRite getTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }

    /// shift 点击搬运: 机器区 <-> 玩家背包。玩家 -> 输入槽; 输入/输出 -> 玩家背包
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        final Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;

        final ItemStack stack = slot.getStack();
        final ItemStack original = stack.copy();

        if (index < PLAYER_SLOT_START) {
            // 机器 -> 玩家
            if (!mergeItemStack(stack, PLAYER_SLOT_START, inventorySlots.size(), true)) return null;
        } else {
            // 玩家 -> 机器: 只进输入槽 (输出槽 isItemValid 恒 false, 无需再过滤)
            if (!mergeItemStack(stack, TileSanguineRite.SLOT_INPUT, TileSanguineRite.SLOT_OUTPUT + 1, false))
                return null;
        }

        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (stack.stackSize == original.stackSize) return null;
        slot.onPickupFromSlot(player, stack);
        return original;
    }

    /// 输出槽: 只许取不许放
    private static class SlotOutput extends Slot {

        SlotOutput(TileSanguineRite tile, int index, int x, int y) {
            super(tile, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }
}
