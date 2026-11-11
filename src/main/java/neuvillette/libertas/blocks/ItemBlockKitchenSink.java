package neuvillette.libertas.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/// 水槽 (kitchenSink) 的物品形态: tooltip 说明无限水量、六面可抽和抽取速率。
public class ItemBlockKitchenSink extends ItemBlock {

    public ItemBlockKitchenSink(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advancedTooltips) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.kitchenSink.capacity"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.kitchenSink.faces"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.kitchenSink.rate"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.kitchenSink.container"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.kitchenSink.lore"));
    }
}
