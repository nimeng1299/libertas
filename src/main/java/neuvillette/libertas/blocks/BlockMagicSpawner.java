package neuvillette.libertas.blocks;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import neuvillette.libertas.ClientProxy;
import neuvillette.libertas.Libertas;
import neuvillette.libertas.gui.ModGuiHandler;

/// 神奇的刷怪笼: GUI 内每 10 秒模拟击杀一次棉签记录的生物, 掉落进输出槽
/// (见 {@link TileMagicSpawner})。右键打开 GUI; 挖掉后内容物随掉落物 NBT 保留,
/// 重新放置即恢复。模型自带发光核心, 方块光照 7。
public class BlockMagicSpawner extends BlockContainer {

    public BlockMagicSpawner() {
        super(Material.rock);
        setBlockName("magicSpawner");
        setHardness(5.0f);
        setResistance(10.0f);
        setStepSound(soundTypeStone);
        setBlockTextureName("libertas:blocks/magic_spawner");
        setBlockBounds(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
    }

    // ---- 渲染: 世界里用 ISBRH 画 JSON 模型 (id 在 ClientProxy.init 里注册), 物品形态走 JsonItemRenderer ----

    @Override
    public int getRenderType() {
        return ClientProxy.MAGIC_SPAWNER_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /// 笼子中央的魔法核心发一点光
    @Override
    public int getLightValue() {
        return 7;
    }

    // ---- 交互: 右键打开 GUI ----

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            player.swingItem();
            return true;
        }
        player.openGui(Libertas.instance, ModGuiHandler.ID_MAGIC_SPAWNER, world, x, y, z);
        return true;
    }

    // ---- 挖掉保留内容: 掉落物携带 NBT (物品栏), 放置时恢复 ----

    /// BlockMiniElfPortal 同款流程: removedByPlayer 之后 TE 就销毁了, 必须趁 TE 还在时先 dropBlockAsItem;
    /// 之后 harvestBlock -> getDrops 再读 TE 时已是 null, 不会重复掉落 (创造模式只走这一次掉落)。
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        dropBlockAsItem(world, x, y, z, meta, 0);
        super.onBlockHarvested(world, x, y, z, meta, player);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        final ArrayList<ItemStack> drops = new ArrayList<>();
        if (world.getTileEntity(x, y, z) instanceof TileMagicSpawner) {
            final TileMagicSpawner spawner = (TileMagicSpawner) world.getTileEntity(x, y, z);
            final ItemStack stack = new ItemStack(this);
            final NBTTagCompound tag = new NBTTagCompound();
            spawner.writeItemNBT(tag);
            stack.setTagCompound(tag);
            drops.add(stack);
        }
        return drops;
    }

    /// ItemBlock.placeBlockAt 在 setBlock (TE 已创建) 之后调用本方法, 读取物品 NBT 恢复内容
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (stack.hasTagCompound() && world.getTileEntity(x, y, z) instanceof TileMagicSpawner) {
            ((TileMagicSpawner) world.getTileEntity(x, y, z)).readItemNBT(stack.getTagCompound());
        }
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    // ---- TileEntity ----

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileMagicSpawner();
    }
}
