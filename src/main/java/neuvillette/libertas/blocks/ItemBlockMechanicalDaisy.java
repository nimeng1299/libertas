package neuvillette.libertas.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/// 机械白雏菊的物品形态: tooltip 注明方块模型出处。
public class ItemBlockMechanicalDaisy extends ItemBlock {

    public ItemBlockMechanicalDaisy(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advancedTooltips) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.modelSource"));
    }
}
