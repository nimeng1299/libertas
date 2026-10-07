package neuvillette.libertas.blocks;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import neuvillette.libertas.ClientProxy;
import neuvillette.libertas.Libertas;
import neuvillette.libertas.gui.ModGuiHandler;

/// 猩红祭仪: 自动把输入物品投进正后方的血魔法血之祭坛, 祭坛完成后产物收入输出槽
/// (见 {@link TileSanguineRite})。放置时正面 (带血珠徽记的一面) 朝向放置者, 祭坛须
/// 紧贴其背面。右键打开 GUI; GUI 里的重置按钮可忘记当前跟踪的配方。挖掉后内容物
/// 随掉落物 NBT 保留, 重新放置即恢复。
public class BlockSanguineRite extends BlockContainer {

    public BlockSanguineRite() {
        super(Material.rock);
        setBlockName("sanguineRite");
        setHardness(5.0f);
        setResistance(10.0f);
        setStepSound(soundTypeStone);
        // 模型外框撑满整个方块
        setBlockBounds(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
    }

    // ---- 渲染: 世界里用 ISBRH 画 JSON 模型 (id 在 ClientProxy.init 里注册), 模型按 meta 2..5 旋转 ----

    @Override
    public int getRenderType() {
        return ClientProxy.SANGUINE_RITE_RENDER_ID;
    }

    // ---- 交互: 右键打开 GUI ----

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            player.swingItem();
            return true;
        }
        if (world.getTileEntity(x, y, z) instanceof TileSanguineRite) {
            ((TileSanguineRite) world.getTileEntity(x, y, z)).syncNow();
        }
        player.openGui(Libertas.instance, ModGuiHandler.ID_SANGUINE_RITE, world, x, y, z);
        return true;
    }

    // ---- 朝向: 放置时正面朝向放置者 (熔炉同款 yaw 计算, 镜像映射), meta 2..5 ----

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        final int l = MathHelper.floor_double((double) (placer.rotationYaw * 4.0f / 360.0f) + 0.5) & 3;
        // l: 0=面朝南 1=西 2=北 3=东 -> 正面转向放置者: 北/南/东/西
        final int facing = l == 0 ? 2 : l == 1 ? 5 : l == 2 ? 3 : 4;
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);

        if (stack.hasTagCompound() && world.getTileEntity(x, y, z) instanceof TileSanguineRite) {
            ((TileSanguineRite) world.getTileEntity(x, y, z)).readItemNBT(stack.getTagCompound());
        }
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    // ---- 挖掉保留内容: 掉落物携带 NBT (物品栏), 放置时恢复 ----

    /// BlockMechanicalRunicAltar 同款流程: removedByPlayer 之后 TE 就销毁了, 必须趁 TE 还在时
    /// 先 dropBlockAsItem; 之后 harvestBlock -> getDrops 再读 TE 时已是 null, 不会重复掉落。
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        dropBlockAsItem(world, x, y, z, meta, 0);
        super.onBlockHarvested(world, x, y, z, meta, player);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        final ArrayList<ItemStack> drops = new ArrayList<>();
        if (world.getTileEntity(x, y, z) instanceof TileSanguineRite) {
            final TileSanguineRite rite = (TileSanguineRite) world.getTileEntity(x, y, z);
            final ItemStack stack = new ItemStack(this);
            final NBTTagCompound tag = new NBTTagCompound();
            rite.writeItemNBT(tag);
            stack.setTagCompound(tag);
            drops.add(stack);
        }
        return drops;
    }

    // ---- TileEntity ----

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSanguineRite();
    }
}
