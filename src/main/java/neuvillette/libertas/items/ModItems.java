package neuvillette.libertas.items;

import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static Item stick18cm;

    private ModItems() {}

    public static void init() {
        stick18cm = new ItemStick18cm();
        GameRegistry.registerItem(stick18cm, "18cm");
    }
}
