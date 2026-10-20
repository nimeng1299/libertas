package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import neuvillette.libertas.ClientProxy;
import neuvillette.libertas.Libertas;
import neuvillette.libertas.gui.ModGuiHandler;
import neuvillette.libertas.items.ItemWirelessConfigurationCard;

public class BlockWirelessControlCenter extends BlockContainer {

    public BlockWirelessControlCenter() {
        super(Material.iron);
        setBlockName("wirelessControlCenter");
        setBlockTextureName("libertas:wireless_control_center");
        setHardness(5.0f);
        setResistance(10.0f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public int getRenderType() {
        return ClientProxy.WIRELESS_CONTROL_CENTER_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (player.getHeldItem() != null && player.getHeldItem()
            .getItem() instanceof ItemWirelessConfigurationCard) return false;
        if (world.isRemote) return true;
        if (world.getTileEntity(x, y, z) instanceof TileWirelessControlCenter center && center.canConfigure(player)) {
            player.openGui(Libertas.instance, ModGuiHandler.ID_WIRELESS_CONTROL_CENTER, world, x, y, z);
        } else {
            player.addChatMessage(new ChatComponentTranslation("msg.libertas.wireless.denied"));
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        int facing = MathHelper.floor_double(placer.rotationYaw * 4.0 / 360.0 + 0.5) & 3;
        world.setBlockMetadataWithNotify(x, y, z, new int[] { 2, 5, 3, 4 }[facing], 2);
        if (!world.isRemote && placer instanceof EntityPlayer player
            && world.getTileEntity(x, y, z) instanceof TileWirelessControlCenter center) {
            center.bindOwner(player);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileWirelessControlCenter center) {
            center.removeController();
            ItemStack template = center.getStackInSlot(0);
            if (template != null) {
                world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, template.copy()));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileWirelessControlCenter();
    }
}
