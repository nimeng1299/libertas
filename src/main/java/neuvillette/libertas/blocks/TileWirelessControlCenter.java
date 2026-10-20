package neuvillette.libertas.blocks;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import neuvillette.libertas.wireless.EnergyHatchTemplate;
import neuvillette.libertas.wireless.WirelessControlWorldData;
import neuvillette.libertas.wireless.WirelessPowerProfile;

/** One stack of 64 hatches acts as a removable specification template, never as a fuel or consumable. */
public class TileWirelessControlCenter extends TileEntity implements IInventory {

    private UUID controllerId;
    private UUID owner;
    private String ownerName = "";
    private ItemStack template;
    private BigInteger displayedBalance = BigInteger.ZERO;

    public void bindOwner(EntityPlayer player) {
        controllerId = UUID.randomUUID();
        owner = player.getUniqueID();
        ownerName = player.getCommandSenderName();
        WirelessNetworkManager.strongCheckOrAddUser(owner);
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public UUID getControllerId() {
        return controllerId;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean canConfigure(EntityPlayer player) {
        return owner != null && SpaceProjectManager.getLeader(owner)
            .equals(SpaceProjectManager.getLeader(player.getUniqueID()));
    }

    public WirelessPowerProfile getProfile() {
        return EnergyHatchTemplate.activeProfile(template);
    }

    public BigInteger getNetworkBalance() {
        if (worldObj == null || worldObj.isRemote) return displayedBalance;
        return owner == null ? BigInteger.ZERO : WirelessNetworkManager.getUserEU(owner);
    }

    /** Publish on inventory changes and periodically to cover in-place stack mutations by inventory automation. */
    public void publish() {
        WirelessControlWorldData data = WirelessControlWorldData.get(worldObj);
        if (data != null) {
            data.publish(controllerId, owner, worldObj.provider.dimensionId, xCoord, yCoord, zCoord, getProfile());
        }
    }

    public void removeController() {
        WirelessControlWorldData data = WirelessControlWorldData.get(worldObj);
        if (data != null) data.remove(controllerId);
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) publish();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        publish();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        controllerId = WirelessControlWorldData.readUuid(tag, "ControllerId");
        owner = WirelessControlWorldData.readUuid(tag, "Owner");
        ownerName = tag.getString("OwnerName");
        template = tag.hasKey("Template", 10) ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Template")) : null;
        if (template != null) template.stackSize = Math.min(EnergyHatchTemplate.REQUIRED_COUNT, template.stackSize);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (controllerId != null) tag.setString("ControllerId", controllerId.toString());
        if (owner != null) tag.setString("Owner", owner.toString());
        tag.setString("OwnerName", ownerName);
        if (template != null) tag.setTag("Template", template.writeToNBT(new NBTTagCompound()));
    }

    public NBTTagCompound getSyncData() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        tag.setString("NetworkBalance", getNetworkBalance().toString());
        return tag;
    }

    @Override
    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, getSyncData());
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        readFromNBT(tag);
        try {
            displayedBalance = new BigInteger(tag.getString("NetworkBalance"));
        } catch (NumberFormatException ignored) {
            displayedBalance = BigInteger.ZERO;
        }
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? template : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != 0 || template == null || amount <= 0) return null;
        ItemStack result = template.splitStack(Math.min(amount, template.stackSize));
        if (template.stackSize <= 0) template = null;
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0) return null;
        ItemStack result = template;
        template = null;
        markDirty();
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) return;
        template = stack;
        if (template != null) template.stackSize = Math.min(getInventoryStackLimit(), template.stackSize);
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.libertas.wirelessControlCenter";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return EnergyHatchTemplate.REQUIRED_COUNT;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64
            && canConfigure(player);
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && EnergyHatchTemplate.read(stack) != null;
    }
}
