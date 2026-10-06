package neuvillette.libertas;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/// 带标签匹配的无序合成。Forge 的 ShapelessOreRecipe 只比对 item + damage,
/// 会把任意 NBT(任意种类)的同物品都吃进配方; 本类对带 NBT 的 ItemStack 输入额外要求
/// NBT 一致(用于锁定特花的 type, 如"火红莲 + 拼图"不会被"任意特花 + 拼图"蒙混)。
///
/// 继承 {@link ShapelessOreRecipe} 以兼容 NEI 展示, 仅重写 matches 为带 NBT 的
/// 多重集合匹配(槽位数必须恰好用尽)。父类的 input 列表是 private, 故按相同的
/// 归一化规则自行重建一份(NBT 匹配只认这份)。
public class ShapelessNBTRecipe extends ShapelessOreRecipe {

    static {
        RecipeSorter.register(
            "libertas:shapeless_nbt",
            ShapelessNBTRecipe.class,
            RecipeSorter.Category.SHAPELESS,
            "after:forge:shapelessore");
    }

    /// 与父类 input 平行的输入列表: ItemStack(带 NBT 匹配)或 List<ItemStack>(OreDict)。
    private final List<Object> taggedInputs = new ArrayList<>();

    public ShapelessNBTRecipe(ItemStack result, Object... recipe) {
        super(result, recipe);
        for (Object in : recipe) {
            if (in instanceof ItemStack) {
                taggedInputs.add(((ItemStack) in).copy());
            } else if (in instanceof Item) {
                taggedInputs.add(new ItemStack((Item) in));
            } else if (in instanceof Block) {
                taggedInputs.add(new ItemStack((Block) in));
            } else if (in instanceof String) {
                // 保留 OreDictionary 返回的同一 List 实例: 之后注册的同组原料会自动进入配方
                taggedInputs.add(OreDictionary.getOres((String) in));
            } else {
                throw new IllegalArgumentException("Invalid shapeless NBT recipe input: " + in);
            }
        }
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        final List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            final ItemStack slot = inv.getStackInSlot(i);
            if (slot != null) slots.add(slot);
        }
        if (slots.size() != taggedInputs.size()) return false;

        final boolean[] used = new boolean[slots.size()];
        for (Object required : taggedInputs) {
            boolean found = false;
            for (int i = 0; i < slots.size(); i++) {
                if (!used[i] && itemNbtMatches(required, slots.get(i))) {
                    used[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean itemNbtMatches(Object required, ItemStack slot) {
        if (required instanceof List) {
            for (Object entry : (List<?>) required) {
                if (itemNbtMatches(entry, slot)) return true;
            }
            return false;
        }
        if (!(required instanceof ItemStack)) return false;

        final ItemStack target = (ItemStack) required;
        if (target.getItem() != slot.getItem()) return false;
        if (target.getItemDamage() != slot.getItemDamage() && target.getItemDamage() != OreDictionary.WILDCARD_VALUE)
            return false;
        // 输入带 NBT 时要求一致; 不带则放行(如拼图无 NBT)
        return !target.hasTagCompound() || ItemStack.areItemStackTagsEqual(target, slot);
    }
}
