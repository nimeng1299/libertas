package neuvillette.libertas.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/// 区块重塑机的物品形态: tooltip 说明两种作业与处理速度。
public class ItemBlockChunkReshaper extends ItemBlock {

    public ItemBlockChunkReshaper(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advancedTooltips) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.chunkReshaper.excavate"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.chunkReshaper.pave"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.chunkReshaper.speed"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.chunkReshaper.lore"));
    }
}
