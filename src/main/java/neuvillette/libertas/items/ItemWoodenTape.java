package neuvillette.libertas.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import neuvillette.libertas.Libertas;

/** 木胶带：通过 craftingDuctTape 矿物词典接入 GT 的原生维护逻辑。 */
public class ItemWoodenTape extends Item {

    public ItemWoodenTape() {
        setUnlocalizedName("woodenTape");
        setTextureName(Libertas.MODID + ":woodenTape");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.woodenTape.usage"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.woodenTape.consumption"));
    }
}
