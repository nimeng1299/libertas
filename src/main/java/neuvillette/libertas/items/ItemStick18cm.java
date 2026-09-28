package neuvillette.libertas.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import neuvillette.libertas.Libertas;

/// 18cm的木棍 - 一把只能通过代码(stack.addEnchantment 等)附魔的木棍武器.
///
/// 不可通过附魔台 / 铁砧附魔:
/// - 耐久为 0 (不消耗耐久), 原版 Item.isItemTool 判定为 false, 附魔台直接不收;
/// - [#getItemEnchantability] 返回 0, 即使放进去也摇不出附魔;
/// - [#isBookEnchantable] 返回 false, 铁砧上无法与附魔书合成;
/// - setNoRepair + [#getIsRepairable] 返回 false, 铁砧上无法修复/同名合并.
/// 以上都不影响 NBT 层面的附魔, 所以 stack.addEnchantment / 指令附魔依旧生效.
public class ItemStick18cm extends ItemSword {

    public ItemStick18cm() {
        super(ToolMaterial.WOOD);
        this.setUnlocalizedName("18cm");
        // 无限耐久: 最大耐久设为 0 -> isDamageable() 恒为 false, damageItem 永远不生效
        this.setMaxDamage(0);
        this.setNoRepair();
        this.setCreativeTab(CreativeTabs.tabCombat);
        // Fallback icon for non-IItemRenderer code paths (the actual render is a GTNHLib JSON model). With GTNHLib's
        // TextureMap mixin this resolves to assets/libertas/textures/blocks/sakura_tech_stick.png.
        this.setTextureName(Libertas.MODID + ":blocks/sakura_tech_stick");
    }

    @Override
    public int getItemEnchantability() {
        return 0;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return false;
    }
}
