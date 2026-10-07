package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import neuvillette.libertas.blocks.TileMagicSpawner;
import neuvillette.libertas.items.ItemCottonSwab;

/// 神奇的刷怪笼容器。
///
/// 槽位布局 (与 assets/libertas/textures/gui/magicSpawner.png 一一对应):
///
/// ```text
/// 0 输入槽 1x1 x 8, y 49
/// 1..16 输出槽 4x4 x 96, y 49
/// 17..43 玩家背包 3x9 x 8, y 133
/// 44..52 快捷栏 1x9 x 8, y 191
/// ```
public class ContainerMagicSpawner extends Container {

    /// GUI 上玩家背包第一格的槽位下标 (inventorySlots 列表里)
    public static final int PLAYER_SLOT_START = TileMagicSpawner.TOTAL_SLOTS;
    /// 输出槽区域的背景贴图坐标
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 49;
    public static final int OUTPUT_X = 96;
    public static final int OUTPUT_Y = 49;
    public static final int PLAYER_INV_Y = 133;
    public static final int HOTBAR_Y = 191;

    private final TileMagicSpawner tile;

    public ContainerMagicSpawner(InventoryPlayer playerInventory, TileMagicSpawner tile) {
        this.tile = tile;

        addSlotToContainer(new SlotSwabInput(tile, TileMagicSpawner.SLOT_INPUT, INPUT_X, INPUT_Y));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                addSlotToContainer(
                    new SlotSwabOutput(
                        tile,
                        TileMagicSpawner.SLOT_OUTPUT_START + row * 4 + col,
                        OUTPUT_X + col * 18,
                        OUTPUT_Y + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(
                    new Slot(playerInventory, col + row * 9 + 9, INPUT_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col, INPUT_X + col * 18, HOTBAR_Y));
        }
    }

    public TileMagicSpawner getTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }

    /// shift 点击搬运: 机器区 <-> 玩家背包
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        final Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;

        final ItemStack stack = slot.getStack();
        final ItemStack original = stack.copy();

        if (index < PLAYER_SLOT_START) {
            // 机器 → 玩家
            if (!mergeItemStack(stack, PLAYER_SLOT_START, inventorySlots.size(), true)) return null;
        } else {
            // 玩家 → 输入槽 (SlotSwabInput.isItemValid 只收棉签)
            if (!mergeItemStack(stack, 0, TileMagicSpawner.SLOT_OUTPUT_START, false)) return null;
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

    /// 输入槽: 只收棉签
    private static class SlotSwabInput extends Slot {

        SlotSwabInput(TileMagicSpawner tile, int index, int x, int y) {
            super(tile, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return stack != null && stack.getItem() instanceof ItemCottonSwab;
        }
    }

    /// 输出槽: 只许取不许放
    private static class SlotSwabOutput extends Slot {

        SlotSwabOutput(TileMagicSpawner tile, int index, int x, int y) {
            super(tile, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }
}
