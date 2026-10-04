package neuvillette.libertas.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

/// 要素罐的 ItemBlock: 最大堆叠 1 (罐子是容器不是耗材); tooltip 展示罐内要素;
/// 手持罐子右键源质容器 (蒸馏器/要素罐/要素熔炼舱等 IAspectContainer) 时接管交互:
/// 普通右键把容器里的要素全部抽进手持罐, 潜行右键把手持罐里的要素尽量倒入容器
/// (全量转移, 罐子本身不设单次上限; 倒入量只受目标容器的容量限制),
/// 交互在罐子物品的 NBT 与目标容器之间进行。
public class ItemBlockEssentiaJar extends ItemBlock {

    /// tooltip 最多列出的要素行数
    private static final int TOOLTIP_MAX_LINES = 5;

    public ItemBlockEssentiaJar(Block block) {
        super(block);
        setMaxStackSize(1);
    }

    // ---- 手持罐与源质容器的装取 (TC 要素瓶 onItemUseFirst 的同款挂点) ----

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!(world.getTileEntity(x, y, z) instanceof IAspectContainer)) return false;

        if (world.isRemote) {
            // 客户端不能在这里返回 true: 1.7.10 的 onPlayerRightClick 会直接吞掉 C08 包, 服务端就收不到交互了
            player.swingItem();
            return false;
        }

        final IAspectContainer container = (IAspectContainer) world.getTileEntity(x, y, z);
        final boolean acted = player.isSneaking() ? pourIntoContainer(stack, container)
            : drawFromContainer(stack, container);
        if (acted) world.playSoundAtEntity(player, "game.neutral.swim", 0.25f, 1.0f);
        player.inventoryContainer.detectAndSendChanges();
        // 指向源质容器时整体接管右键: 不放置, 也不打开目标容器的 GUI
        return true;
    }

    /// 挡掉客户端对着容器时的放置预测 (服务端路径到不了这里——onItemUseFirst 已接管),
    /// 否则客户端会预测放置出一个随即被回滚的幽灵方块。
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.getTileEntity(x, y, z) instanceof IAspectContainer) return false;
        return super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
    }

    /// 从容器抽取: 把容器里所有要素 (每种全量) 抽进手持罐, 按存量从多到少逐要素转移,
    /// 只在罐内装得下时才从容器扣 (不丢要素), 有一点移动即成功。
    private boolean drawFromContainer(ItemStack stack, IAspectContainer container) {
        AspectList stored = getStoredEssentia(stack);
        if (stored == null) stored = new AspectList();
        final AspectList provided = container.getAspects();
        if (provided == null || provided.size() == 0) return false;
        boolean moved = false;
        for (Aspect aspect : provided.getAspectsSortedAmount()) {
            final int available = provided.getAmount(aspect);
            if (available <= 0) continue;
            final int space = (int) Math.min((long) available, (long) Integer.MAX_VALUE - stored.getAmount(aspect));
            if (space <= 0) continue;
            if (container.takeFromContainer(aspect, space)) {
                stored.add(aspect, space);
                moved = true;
            }
        }
        if (moved) writeStoredEssentia(stack, stored);
        return moved;
    }

    /// 向容器倒入: 把手持罐里容器接受的要素 (每种全量) 尽量倒入, 有一点移动即成功;
    /// 装不下的余量保留在罐里 (倒入量只受目标容器自身容量的限制)。
    private boolean pourIntoContainer(ItemStack stack, IAspectContainer container) {
        final AspectList stored = getStoredEssentia(stack);
        if (stored == null || stored.size() == 0) return false;
        boolean moved = false;
        for (Aspect aspect : stored.getAspectsSortedAmount()) {
            final int have = stored.getAmount(aspect);
            if (have <= 0 || !container.doesContainerAccept(aspect)) continue;
            final int leftover = container.addToContainer(aspect, have);
            final int movedAmount = have - leftover;
            if (movedAmount > 0) {
                stored.remove(aspect, movedAmount);
                moved = true;
            }
        }
        if (moved) writeStoredEssentia(stack, stored);
        return moved;
    }

    /// 回写罐子物品 NBT; 抽空后移除 NBT, tooltip 回到"空"
    private static void writeStoredEssentia(ItemStack stack, AspectList stored) {
        if (stored.size() == 0) {
            stack.setTagCompound(null);
            return;
        }
        final NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        stored.writeToNBT(tag, "essentia");
        stack.setTagCompound(tag);
    }

    // ---- tooltip ----

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advancedTooltips) {
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.libertas.essentiaJar.usage"));
        final AspectList essentia = getStoredEssentia(stack);
        if (essentia == null || essentia.size() == 0) {
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.libertas.essentiaJar.empty"));
            return;
        }
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.libertas.essentiaJar.stored"));
        tooltip.addAll(TileEssentiaJar.summarizeLines(essentia, TOOLTIP_MAX_LINES));
    }

    /// 供其他地方读取掉落物里的要素 (NBT 由 BlockEssentiaJar.getDrops 与本类的装取逻辑写入)
    public static AspectList getStoredEssentia(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        final NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("essentia")) return null;
        final AspectList essentia = new AspectList();
        essentia.readFromNBT(tag, "essentia");
        return essentia;
    }
}
