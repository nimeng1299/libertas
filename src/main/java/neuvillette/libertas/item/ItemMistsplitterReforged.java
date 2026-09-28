package neuvillette.libertas.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import neuvillette.libertas.Libertas;

/**
 * 雾切之回光 / Mistsplitter Reforged.
 *
 * This weapon can never be enchanted at an enchanting table or with an enchanted book on an anvil.
 * Enchantments can still be applied programmatically (e.g. via commands or ItemStack#addEnchantment).
 */
public class ItemMistsplitterReforged extends ItemSword {

    public ItemMistsplitterReforged() {
        super(ToolMaterial.EMERALD);
        this.setUnlocalizedName("mistsplitterReforged");
        this.setTextureName(Libertas.MODID + ":mistsplitter_reforged");
        this.setMaxDamage(9999);
    }

    @Override
    public int getItemEnchantability() {
        return 0;
    }

    @Override
    public int getItemEnchantability(ItemStack stack) {
        return 0;
    }

    /**
     * Returning false makes ItemStack#isItemEnchantable false, which excludes the item from
     * the enchanting table. Enchantments can still be added via code afterwards.
     */
    @Override
    public boolean isItemTool(ItemStack stack) {
        return false;
    }

    /**
     * Reject enchanted books on anvils as well: the only way to put enchantments on this weapon
     * is through code.
     */
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }
}
