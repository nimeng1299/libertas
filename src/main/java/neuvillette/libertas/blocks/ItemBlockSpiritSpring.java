package neuvillette.libertas.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public class ItemBlockSpiritSpring extends ItemBlock {

    public ItemBlockSpiritSpring(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advancedTooltips) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.spiritSpring.effect"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.spiritSpring.lore"));
    }
}
