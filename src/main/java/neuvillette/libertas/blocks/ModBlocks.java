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
    public static Block miniElfPortal;
    public static Item itemMiniElfPortal;
    public static Block industrialAgglomerationPlate;
    public static Item itemIndustrialAgglomerationPlate;
    public static Block mechanicalApothecary;
    public static Item itemMechanicalApothecary;
    public static Block mechanicalDaisy;
    public static Item itemMechanicalDaisy;
    public static Block mechanicalBrewery;
    public static Item itemMechanicalBrewery;

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

        miniElfPortal = new BlockMiniElfPortal();
        GameRegistry.registerBlock(miniElfPortal, ItemBlockMiniElfPortal.class, "miniElfPortal");
        itemMiniElfPortal = Item.getItemFromBlock(miniElfPortal);
        GameRegistry.registerTileEntity(TileMiniElfPortal.class, "libertas.mini_elf_portal");

        itemMiniElfPortal.setCreativeTab(ModCreativeTabs.tabLibertas);

        industrialAgglomerationPlate = new BlockIndustrialAgglomerationPlate();
        GameRegistry.registerBlock(
            industrialAgglomerationPlate,
            ItemBlockIndustrialAgglomerationPlate.class,
            "industrialAgglomerationPlate");
        itemIndustrialAgglomerationPlate = Item.getItemFromBlock(industrialAgglomerationPlate);
        GameRegistry
            .registerTileEntity(TileIndustrialAgglomerationPlate.class, "libertas.industrial_agglomeration_plate");

        itemIndustrialAgglomerationPlate.setCreativeTab(ModCreativeTabs.tabLibertas);

        mechanicalApothecary = new BlockMechanicalApothecary();
        GameRegistry.registerBlock(mechanicalApothecary, ItemBlockMechanicalApothecary.class, "mechanicalApothecary");
        itemMechanicalApothecary = Item.getItemFromBlock(mechanicalApothecary);
        GameRegistry.registerTileEntity(TileMechanicalApothecary.class, "libertas.mechanical_apothecary");

        itemMechanicalApothecary.setCreativeTab(ModCreativeTabs.tabLibertas);

        mechanicalDaisy = new BlockMechanicalDaisy();
        GameRegistry.registerBlock(mechanicalDaisy, ItemBlockMechanicalDaisy.class, "mechanicalDaisy");
        itemMechanicalDaisy = Item.getItemFromBlock(mechanicalDaisy);
        GameRegistry.registerTileEntity(TileMechanicalDaisy.class, "libertas.mechanical_daisy");

        itemMechanicalDaisy.setCreativeTab(ModCreativeTabs.tabLibertas);

        mechanicalBrewery = new BlockMechanicalBrewery();
        GameRegistry.registerBlock(mechanicalBrewery, ItemBlockMechanicalBrewery.class, "mechanicalBrewery");
        itemMechanicalBrewery = Item.getItemFromBlock(mechanicalBrewery);
        GameRegistry.registerTileEntity(TileMechanicalBrewery.class, "libertas.mechanical_brewery");

        itemMechanicalBrewery.setCreativeTab(ModCreativeTabs.tabLibertas);
    }
}
