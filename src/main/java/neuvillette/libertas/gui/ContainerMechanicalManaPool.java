package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import neuvillette.libertas.blocks.TileMechanicalManaPool;

/// 机械魔力池容器。
///
/// 槽位布局 (与 assets/libertas/textures/gui/mechanicalManaPool.png 一一对应, 由
/// tools/gen_mechanical_mana_pool_gui.py 生成):
///
/// ```text
/// 0..15 输入槽 4x4 x 8, y 49
/// 16..31 输出槽 4x4 x 96, y 49
/// 32 升级槽 1 x 80, y 27
/// 33..59 玩家背包 3x9 x 8, y 133
/// 60..68 快捷栏 1x9 x 8, y 191
/// ```
public class ContainerMechanicalManaPool extends Container {

    /// GUI 上玩家背包第一格的槽位下标 (inventorySlots 列表里)
    public static final int PLAYER_SLOT_START = TileMechanicalManaPool.TOTAL_SLOTS;
    /// 输入/输出槽区域的背景贴图坐标
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 49;
    public static final int OUTPUT_X = 96;
    public static final int OUTPUT_Y = 49;
    public static final int UPGRADE_X = 80;
    public static final int UPGRADE_Y = 27;
    public static final int PLAYER_INV_Y = 133;
    public static final int HOTBAR_Y = 191;

    private final TileMechanicalManaPool tile;

    public ContainerMechanicalManaPool(InventoryPlayer playerInventory, TileMechanicalManaPool tile) {
        this.tile = tile;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                addSlotToContainer(new Slot(tile, row * 4 + col, INPUT_X + col * 18, INPUT_Y + row * 18));
            }
        }
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                addSlotToContainer(
                    new SlotManaOutput(tile, 16 + row * 4 + col, OUTPUT_X + col * 18, OUTPUT_Y + row * 18));
            }
        }
        addSlotToContainer(new SlotManaUpgrade(tile, TileMechanicalManaPool.SLOT_UPGRADE, UPGRADE_X, UPGRADE_Y));

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

    public TileMechanicalManaPool getTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }

    /// shift 点击搬运: 机器区 <-> 玩家背包。玩家 → 升级槽 (仅催化器) → 输入槽;
    /// 输入/输出/升级 → 玩家背包 (输出优先并堆到已有组)
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
            // 玩家 → 机器: 先试升级槽 (isItemValid 过滤), 再进输入槽
            if (!mergeItemStack(
                stack,
                TileMechanicalManaPool.SLOT_UPGRADE,
                TileMechanicalManaPool.SLOT_UPGRADE + 1,
                false) && !mergeItemStack(stack, 0, TileMechanicalManaPool.INPUT_SLOTS, false)) return null;
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
    private static class SlotManaOutput extends Slot {

        SlotManaOutput(TileMechanicalManaPool tile, int index, int x, int y) {
            super(tile, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }

    /// 升级槽: 只收炼金/炼造催化器, 至多 1 个
    private static class SlotManaUpgrade extends Slot {

        SlotManaUpgrade(TileMechanicalManaPool tile, int index, int x, int y) {
            super(tile, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return TileMechanicalManaPool.isCatalyst(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
