package neuvillette.libertas;

import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameRegistry;
import neuvillette.libertas.item.ItemMistsplitterReforged;

public final class ModItems {

    public static Item mistsplitterReforged;

    private ModItems() {}

    public static void registerItems() {
        mistsplitterReforged = new ItemMistsplitterReforged();
        GameRegistry.registerItem(mistsplitterReforged, "mistsplitter_reforged");
    }
}
