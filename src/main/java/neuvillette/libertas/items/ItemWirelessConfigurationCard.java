package neuvillette.libertas.items;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import neuvillette.libertas.blocks.TileWirelessControlCenter;
import neuvillette.libertas.machines.MTEHatchLinkedWirelessEnergy;
import neuvillette.libertas.wireless.WirelessControlWorldData;

/** Shift-right-click a controller to select it, then right-click any linked wireless hatch to connect it. */
public class ItemWirelessConfigurationCard extends Item {

    private static final String LINK_TAG = "WirelessController";

    public ItemWirelessConfigurationCard() {
        setUnlocalizedName("wirelessConfigurationCard");
        setTextureName("libertas:wireless_configuration_card_icon");
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        boolean centerTarget = tile instanceof TileWirelessControlCenter;
        boolean hatchTarget = tile instanceof IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof MTEHatchLinkedWirelessEnergy;
        if (!centerTarget && !hatchTarget) return false;
        // Forge runs onItemUseFirst on the server; let the normal client item-use path send the interaction packet.
        if (world.isRemote) return false;
        if (!player.canPlayerEdit(x, y, z, side, stack)) return true;
        if (tile instanceof TileWirelessControlCenter center) {
            if (!player.isSneaking()) {
                player.addChatMessage(new ChatComponentTranslation("msg.libertas.wireless.sneak"));
            } else if (!center.canConfigure(player) || center.getControllerId() == null) {
                player.addChatMessage(new ChatComponentTranslation("msg.libertas.wireless.denied"));
            } else {
                center.publish();
                NBTTagCompound link = new NBTTagCompound();
                link.setString(
                    "Id",
                    center.getControllerId()
                        .toString());
                link.setInteger("Dimension", world.provider.dimensionId);
                link.setInteger("X", x);
                link.setInteger("Y", y);
                link.setInteger("Z", z);
                stack.setTagInfo(LINK_TAG, link);
                player.inventory.markDirty();
                player.addChatMessage(new ChatComponentTranslation("msg.libertas.wireless.selected", x, y, z));
            }
            return true;
        }
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        WirelessControlWorldData data = WirelessControlWorldData.get(world);
        UUID id = getControllerId(stack);
        WirelessControlWorldData.Controller controller = data == null ? null : data.find(id);
        if (controller == null) {
            player.addChatMessage(new ChatComponentTranslation("msg.libertas.wireless.missing"));
        } else if (!gt.isUseableByPlayer(player) || !SpaceProjectManager.getLeader(controller.owner)
            .equals(SpaceProjectManager.getLeader(player.getUniqueID()))) {
                player.addChatMessage(new ChatComponentTranslation("msg.libertas.wireless.denied"));
            } else {
                ((MTEHatchLinkedWirelessEnergy) gt.getMetaTileEntity()).bindController(id);
                player.addChatMessage(new ChatComponentTranslation("msg.libertas.wireless.connected"));
            }
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileWirelessControlCenter || (tile instanceof IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof MTEHatchLinkedWirelessEnergy);
    }

    public static UUID getControllerId(ItemStack stack) {
        return stack.hasTagCompound() ? WirelessControlWorldData.readUuid(
            stack.getTagCompound()
                .getCompoundTag(LINK_TAG),
            "Id") : null;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.wireless.card.select"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.wireless.card.connect"));
        UUID id = getControllerId(stack);
        if (id == null) {
            tooltip.add(StatCollector.translateToLocal("tooltip.libertas.wireless.card.empty"));
        } else {
            NBTTagCompound link = stack.getTagCompound()
                .getCompoundTag(LINK_TAG);
            tooltip.add(
                StatCollector.translateToLocalFormatted(
                    "tooltip.libertas.wireless.card.location",
                    link.getInteger("Dimension"),
                    link.getInteger("X"),
                    link.getInteger("Y"),
                    link.getInteger("Z")));
            if (advanced) tooltip.add(id.toString());
        }
    }
}
