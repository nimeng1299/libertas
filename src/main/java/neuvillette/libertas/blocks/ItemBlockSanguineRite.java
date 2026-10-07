package neuvillette.libertas.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/// 猩红祭仪的物品形态: tooltip 注明须把血之祭坛放在正后方。
public class ItemBlockSanguineRite extends ItemBlock {

    public ItemBlockSanguineRite(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advancedTooltips) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.sanguineRite.usage"));
    }
}
