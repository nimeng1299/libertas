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
    public static Block spiritSpring;
    public static Item itemSpiritSpring;
    public static Block mechanicalManaPool;
    public static Item itemMechanicalManaPool;
    public static Block mechanicalRunicAltar;
    public static Item itemMechanicalRunicAltar;

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

        spiritSpring = new BlockSpiritSpring();
        GameRegistry.registerBlock(spiritSpring, ItemBlockSpiritSpring.class, "spiritSpring");
        itemSpiritSpring = Item.getItemFromBlock(spiritSpring);

        itemSpiritSpring.setCreativeTab(ModCreativeTabs.tabLibertas);

        mechanicalManaPool = new BlockMechanicalManaPool();
        GameRegistry.registerBlock(mechanicalManaPool, ItemBlockMechanicalManaPool.class, "mechanicalManaPool");
        itemMechanicalManaPool = Item.getItemFromBlock(mechanicalManaPool);
        GameRegistry.registerTileEntity(TileMechanicalManaPool.class, "libertas.mechanical_mana_pool");

        itemMechanicalManaPool.setCreativeTab(ModCreativeTabs.tabLibertas);

        mechanicalRunicAltar = new BlockMechanicalRunicAltar();
        GameRegistry.registerBlock(mechanicalRunicAltar, ItemBlockMechanicalRunicAltar.class, "mechanicalRunicAltar");
        itemMechanicalRunicAltar = Item.getItemFromBlock(mechanicalRunicAltar);
        GameRegistry.registerTileEntity(TileMechanicalRunicAltar.class, "libertas.mechanical_runic_altar");

        itemMechanicalRunicAltar.setCreativeTab(ModCreativeTabs.tabLibertas);
    }
}
