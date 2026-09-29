package neuvillette.libertas.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/// Libertas 专属创造模式标签页: 1.7.10 里 mod 自定义标签页排在原版第一行之后,
/// 会自然落到创造栏翻页后的独立页面. 图标即本 mod 的法杖 (IItemRenderer 生效).
public final class ModCreativeTabs {

    public static CreativeTabs tabLibertas;

    private ModCreativeTabs() {}

    public static void init() {
        tabLibertas = new CreativeTabs("libertas") {

            @Override
            public Item getTabIconItem() {
                return ModItems.purpleMood;
            }
        };
    }
}
