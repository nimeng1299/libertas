package neuvillette.libertas.items;

import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static Item stick18cm;
    public static Item purpleMood;
    public static Item deepSeaEcho;
    public static Item jigsaw;
    public static Item cottonSwab;

    private ModItems() {}

    public static void init() {
        stick18cm = new ItemStick18cm();
        GameRegistry.registerItem(stick18cm, "18cm");

        purpleMood = new ItemPurpleMood();
        GameRegistry.registerItem(purpleMood, "purpleMood");

        deepSeaEcho = new ItemDeepSeaEcho();
        GameRegistry.registerItem(deepSeaEcho, "deepSeaEcho");

        jigsaw = new ItemJigsaw();
        GameRegistry.registerItem(jigsaw, "jigsaw");

        cottonSwab = new ItemCottonSwab();
        GameRegistry.registerItem(cottonSwab, "cottonSwab");

        // 物品构造完再建标签页 (图标引用 purpleMood), 并把法杖从 TC 的标签页挪过来
        ModCreativeTabs.init();
        purpleMood.setCreativeTab(ModCreativeTabs.tabLibertas);
        deepSeaEcho.setCreativeTab(ModCreativeTabs.tabLibertas);
        jigsaw.setCreativeTab(ModCreativeTabs.tabLibertas);
        cottonSwab.setCreativeTab(ModCreativeTabs.tabLibertas);
    }
}
