package neuvillette.libertas.items;

import net.minecraft.item.Item;

import neuvillette.libertas.Libertas;

/// 拼图 (jigsaw) - 纯装饰物品, 没有任何功能: 不可合成、无交互、无 tooltip, 只是能拿在手里的一块拼图.
///
/// 模型为站立式拼图块 (block 风格 JSON 模型), 与 18cm 木棍共用 JsonItemRenderer 渲染管线;
/// 模型/贴图由 tools/gen_jigsaw_model.py 生成, 源文件在 blockbench/jigsaw.bbmodel.
public class ItemJigsaw extends Item {

    public ItemJigsaw() {
        this.setUnlocalizedName("jigsaw");
        // 非 IItemRenderer 代码路径的兜底图标 (本物品注册了全路径的 JsonItemRenderer, 一般不会用到)
        this.setTextureName(Libertas.MODID + ":items/jigsaw");
        // 创造标签页在 ModItems.init 里设为 Libertas 专属标签页
    }
}
