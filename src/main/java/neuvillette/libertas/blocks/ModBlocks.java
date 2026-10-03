package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameRegistry;
import neuvillette.libertas.items.ModCreativeTabs;

public final class ModBlocks {

    public static Block azureTear;
    public static Item itemAzureTear;

    private ModBlocks() {}

    public static void init() {
        azureTear = new BlockAzureTear();
        GameRegistry.registerBlock(azureTear, ItemBlockAzureTear.class, "azureTear");
        itemAzureTear = Item.getItemFromBlock(azureTear);
        GameRegistry.registerTileEntity(TileAzureTear.class, "libertas.azure_tear");

        itemAzureTear.setCreativeTab(ModCreativeTabs.tabLibertas);
    }
}
