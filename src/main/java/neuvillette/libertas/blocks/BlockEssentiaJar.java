package neuvillette.libertas.blocks;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.ClientProxy;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.ItemEssence;

/// 要素罐 (essentiaJar) - 和神秘时代要素罐功能一致, 但不挑要素、也永远装不满:
/// 可同时存储任意多种原质, 每种上限为 int 最大值 (见 {@link TileEssentiaJar})。
///
/// 与 TC 罐相同的用法:
/// - 手持要素瓶右键: 空瓶取样 8 点 (取存量最多的一种), 满瓶整瓶倒入;
/// - 罐顶接要素管道会持续吸入要素 (吸力 32, 每 5 tick 吸 1 点);
/// - 实现 IAspectSource, 注魔祭坛可直接从周围的罐子里抽配方要素;
/// - 挖掉后掉落物经 NBT 保留全部内容, 放回原样恢复。
public class BlockEssentiaJar extends BlockContainer {

    public BlockEssentiaJar() {
        super(Material.glass);
        setBlockName("essentiaJar");
        setHardness(1.0f);
        setResistance(10.0f);
        setStepSound(soundTypeGlass);
        // 碰撞箱/选取框贴着模型主体 (瓶身 8x8, 高 16, 标签带外扩到 x/z 3.5..12.5 不计入)
        setBlockBounds(4.0f / 16f, 0.0f, 4.0f / 16f, 12.0f / 16f, 1.0f, 12.0f / 16f);
    }

    // ---- 渲染: 世界里用 ISBRH 画 JSON 模型 (id 在 ClientProxy.init 里注册), 物品形态走 JsonItemRenderer ----

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderType() {
        return ClientProxy.ESSENTIA_JAR_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    // ---- 要素瓶装取 (TC ItemEssence.onItemUseFirst 只认它自家的 blockJar, 这里自己实现同款交互) ----

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemEssence)) return false;

        if (world.isRemote) {
            player.swingItem();
            return true;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEssentiaJar)) return true;
        TileEssentiaJar jar = (TileEssentiaJar) te;
        ItemEssence phial = (ItemEssence) held.getItem();

        if (held.getItemDamage() == 0) {
            // 空瓶: 取 8 点存量最多的要素 (TC 罐只有一种要素, 这里取 max; 数量不足 8 点则什么都不发生)
            Aspect[] sorted = jar.getEssentia()
                .getAspectsSortedAmount();
            if (sorted.length == 0 || jar.containerContains(sorted[0]) < TileEssentiaJar.PHIAL_AMOUNT) return true;

            Aspect aspect = sorted[0];
            if (!jar.takeFromContainer(aspect, TileEssentiaJar.PHIAL_AMOUNT)) return true;

            player.inventory.decrStackSize(player.inventory.currentItem, 1);
            giveOrDrop(player, world, x, y, z, phial, new AspectList().add(aspect, TileEssentiaJar.PHIAL_AMOUNT), 1);
        } else {
            // 满瓶: 整瓶倒入 (无限容量, 永远装得下; TC 固定倒 8 点, 这里按瓶里实际数量倒, 不丢要素)
            AspectList tags = phial.getAspects(held);
            if (tags == null || tags.size() != 1) return true;
            Aspect aspect = tags.getAspects()[0];
            int amount = tags.getAmount(aspect);
            if (amount <= 0 || jar.addToContainer(aspect, amount) != 0) return true;

            player.inventory.decrStackSize(player.inventory.currentItem, 1);
            giveOrDrop(player, world, x, y, z, phial, null, 0);
        }

        world.playSoundAtEntity(player, "game.neutral.swim", 0.25f, 1.0f);
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }

    /// 装好要素的瓶子塞回玩家背包, 塞不下就掉在罐子边 (TC 同款处理)
    private void giveOrDrop(EntityPlayer player, World world, int x, int y, int z, ItemEssence phial,
        AspectList aspects, int damage) {
        ItemStack phialStack = new ItemStack(phial, 1, damage);
        if (aspects != null) phial.setAspects(phialStack, aspects);
        if (!player.inventory.addItemStackToInventory(phialStack)) {
            dropStack(world, x, y, z, phialStack);
        }
    }

    private static void dropStack(World world, int x, int y, int z, ItemStack stack) {
        final EntityItem entity = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack);
        world.spawnEntityInWorld(entity);
    }

    // ---- 挖掉保留内容: 掉落物携带 NBT, 放置时恢复 ----

    /// TC BlockJar 同款流程: removedByPlayer 之后 TE 就销毁了, 必须趁 TE 还在时先 dropBlockAsItem;
    /// 之后 harvestBlock -> getDrops 再读 TE 时已是 null, 不会重复掉落 (创造模式只走这一次掉落)。
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        dropBlockAsItem(world, x, y, z, meta, 0);
        super.onBlockHarvested(world, x, y, z, meta, player);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        final ArrayList<ItemStack> drops = new ArrayList<>();
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEssentiaJar) {
            final ItemStack stack = new ItemStack(this);
            final AspectList essentia = ((TileEssentiaJar) te).getEssentia();
            if (essentia.size() > 0) {
                final NBTTagCompound tag = new NBTTagCompound();
                essentia.writeToNBT(tag, "essentia");
                stack.setTagCompound(tag);
            }
            drops.add(stack);
        }
        return drops;
    }

    /// ItemBlock.placeBlockAt 在 setBlock (TE 已创建) 之后调用本方法, 读取物品 NBT 恢复内容
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (stack.hasTagCompound()) {
            final TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEssentiaJar) {
                final AspectList essentia = new AspectList();
                essentia.readFromNBT(stack.getTagCompound(), "essentia");
                ((TileEssentiaJar) te).setAspects(essentia);
            }
        }
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    // ---- TileEntity ----

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEssentiaJar();
    }
}
