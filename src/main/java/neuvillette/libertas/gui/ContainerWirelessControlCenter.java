package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import neuvillette.libertas.blocks.TileWirelessControlCenter;

public class ContainerWirelessControlCenter extends Container {

    public static final int SLOT_X = 18;
    public static final int SLOT_Y = 106;
    public static final int INVENTORY_X = 40;
    public static final int INVENTORY_Y = 154;
    public static final int HOTBAR_Y = 212;

    private final TileWirelessControlCenter center;
    private NBTTagCompound lastState;

    public ContainerWirelessControlCenter(InventoryPlayer inventory, TileWirelessControlCenter center) {
        this.center = center;
        addSlotToContainer(new Slot(center, 0, SLOT_X, SLOT_Y) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return center.isItemValidForSlot(0, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(
                    new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    public TileWirelessControlCenter getCenter() {
        return center;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return center.isUseableByPlayer(player);
    }

    @Override
    public void addCraftingToCrafters(ICrafting listener) {
        super.addCraftingToCrafters(listener);
        sendState(listener, center.getSyncData());
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (center.getWorldObj().isRemote) return;
        center.publish();
        NBTTagCompound state = center.getSyncData();
        if (state.equals(lastState)) return;
        for (Object listener : crafters) sendState((ICrafting) listener, state);
        lastState = state;
    }

    private void sendState(ICrafting listener, NBTTagCompound state) {
        if (listener instanceof EntityPlayerMP player) {
            player.playerNetServerHandler
                .sendPacket(new S35PacketUpdateTileEntity(center.xCoord, center.yCoord, center.zCoord, 0, state));
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (!slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!mergeItemStack(stack, 1, inventorySlots.size(), true)) return null;
        } else if (!mergeItemStack(stack, 0, 1, false)) return null;
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        if (stack.stackSize == original.stackSize) return null;
        slot.onPickupFromSlot(player, stack);
        center.markDirty();
        return original;
    }
}
