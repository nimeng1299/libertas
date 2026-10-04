package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameRegistry;
import neuvillette.libertas.items.ModCreativeTabs;

public final class ModBlocks {

    public static Block azureTear;
    public static Item itemAzureTear;
    public static Block essentiaJar;
    public static Item itemEssentiaJar;

    private ModBlocks() {}

    public static void init() {
        azureTear = new BlockAzureTear();
        GameRegistry.registerBlock(azureTear, ItemBlockAzureTear.class, "azureTear");
        itemAzureTear = Item.getItemFromBlock(azureTear);
        GameRegistry.registerTileEntity(TileAzureTear.class, "libertas.azure_tear");

        itemAzureTear.setCreativeTab(ModCreativeTabs.tabLibertas);

        essentiaJar = new BlockEssentiaJar();
        GameRegistry.registerBlock(essentiaJar, ItemBlockEssentiaJar.class, "essentiaJar");
        itemEssentiaJar = Item.getItemFromBlock(essentiaJar);
        GameRegistry.registerTileEntity(TileEssentiaJar.class, "libertas.essentia_jar");

        itemEssentiaJar.setCreativeTab(ModCreativeTabs.tabLibertas);
    }
}
