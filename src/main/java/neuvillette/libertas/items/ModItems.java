package neuvillette.libertas.items;

import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static Item stick18cm;
    public static Item purpleMood;

    private ModItems() {}

    public static void init() {
        stick18cm = new ItemStick18cm();
        GameRegistry.registerItem(stick18cm, "18cm");

        purpleMood = new ItemPurpleMood();
        GameRegistry.registerItem(purpleMood, "purpleMood");

        // 物品构造完再建标签页 (图标引用 purpleMood), 并把法杖从 TC 的标签页挪过来
        ModCreativeTabs.init();
        purpleMood.setCreativeTab(ModCreativeTabs.tabLibertas);
    }
}
