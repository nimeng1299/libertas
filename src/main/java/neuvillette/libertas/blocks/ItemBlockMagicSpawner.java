package neuvillette.libertas.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/// 神奇的刷怪笼的物品形态: tooltip 注明用法与红石暂停。
public class ItemBlockMagicSpawner extends ItemBlock {

    public ItemBlockMagicSpawner(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advancedTooltips) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.magicSpawner.effect"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.magicSpawner.redstone"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.magicSpawner.lore"));
    }
}
