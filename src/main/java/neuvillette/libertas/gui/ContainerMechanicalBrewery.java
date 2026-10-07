package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import neuvillette.libertas.blocks.TileMechanicalBrewery;

/// 机械符文祭坛容器。
///
/// 槽位布局 (与 assets/libertas/textures/gui/mechanicalRunicAltar.png 一一对应,
/// 由 tools/gen_mechanical_runic_altar_gui.py 生成; 与机械魔力池同版式):
///
/// ```text
/// 0 活石槽 1 x 80, y 27
/// 1..16 配料槽 4x4 x 8, y 49
/// 17..32 输出槽 4x4 x 96, y 49
/// 33..59 玩家背包 3x9 x 8, y 133
/// 60..68 快捷栏 1x9 x 8, y 191
/// ```
public class ContainerMechanicalBrewery extends Container {

    /// GUI 上玩家背包第一格的槽位下标 (inventorySlots 列表里)
    public static final int PLAYER_SLOT_START = TileMechanicalBrewery.TOTAL_SLOTS;
    /// 输入/输出槽区域的背景贴图坐标
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 49;
    public static final int OUTPUT_X = 96;
    public static final int OUTPUT_Y = 49;
    public static final int BOTTLE_X = 80;
    public static final int BOTTLE_Y = 27;
    public static final int PLAYER_INV_Y = 133;
    public static final int HOTBAR_Y = 191;
    /// 输入/输出区之间的 NEI 箭头热区
    public static final int ARROW_X1 = 80;
    public static final int ARROW_X2 = 96;
    public static final int ARROW_Y1 = 75;
    public static final int ARROW_Y2 = 92;

    private final TileMechanicalBrewery tile;

    public ContainerMechanicalBrewery(InventoryPlayer playerInventory, TileMechanicalBrewery tile) {
        this.tile = tile;

        addSlotToContainer(new SlotBottle(tile, TileMechanicalBrewery.SLOT_BOTTLE, BOTTLE_X, BOTTLE_Y));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                addSlotToContainer(
                    new Slot(
                        tile,
                        TileMechanicalBrewery.SLOT_INPUT_START + row * 4 + col,
                        INPUT_X + col * 18,
                        INPUT_Y + row * 18));
            }
        }
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                addSlotToContainer(
                    new SlotManaOutput(
                        tile,
                        TileMechanicalBrewery.SLOT_OUTPUT_START + row * 4 + col,
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

    public TileMechanicalBrewery getTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }

    /// shift 点击搬运: 机器区 <-> 玩家背包。玩家 → 活石槽 (仅活石) → 配料槽;
    /// 活石/配料/输出 → 玩家背包
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
            // 玩家 → 机器: 先试瓶子槽 (isItemValid 过滤), 再进配料槽
            if (!mergeItemStack(stack, TileMechanicalBrewery.SLOT_BOTTLE, TileMechanicalBrewery.SLOT_BOTTLE + 1, false)
                && !mergeItemStack(
                    stack,
                    TileMechanicalBrewery.SLOT_INPUT_START,
                    TileMechanicalBrewery.SLOT_OUTPUT_START,
                    false))
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
    private static class SlotManaOutput extends Slot {

        SlotManaOutput(TileMechanicalBrewery tile, int index, int x, int y) {
            super(tile, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }

    /// 瓶子槽: 只收酿造容器 (玻璃瓶/保存瓶等)
    private static class SlotBottle extends Slot {

        SlotBottle(TileMechanicalBrewery tile, int index, int x, int y) {
            super(tile, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return TileMechanicalBrewery.isBrewContainer(stack);
        }
    }
}
