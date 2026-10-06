package neuvillette.libertas.botania;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.registry.GameRegistry;
import neuvillette.libertas.Libertas;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.subtile.SubTileEntity;

/// Botania 集成: 注册产能花"风息花"(zephyrBloom)。
///
/// 特花共用 Botania 的方块(botania:specialFlower), 种类完全由物品 NBT 的 "type" 区分,
/// 花朵本体是一个 SubTileEntity 子类。贴图按 BasicSignature 的约定放在
/// assets/botania/textures/blocks/ 下(FML 会合并各 mod 的同名资源域, 图标注册名 botania:zephyrBloom),
/// 这是 1.7.10 Botania 附加花的通行做法; alt/ 下为"变更花卉贴图"配置项的备选贴图。
public final class ModBotania {

    /// 风息花的 subtile 注册名, 同时决定贴图路径与语言键 tile.botania:flower.zephyrBloom.name。
    public static final String SUBTILE_ZEPHYR_BLOOM = "zephyrBloom";
    /// Botania 特花方块的注册名。
    private static final String SPECIAL_FLOWER_BLOCK = "specialFlower";

    private static Block specialFlowerBlock;

    private ModBotania() {}

    /// preInit 阶段调用: 特花图标在 init 之前的资源拼接期注册, subtile 注册必须赶在其之前。
    public static void init() {
        BotaniaAPI.registerSubTile(SUBTILE_ZEPHYR_BLOOM, SubTileZephyrBloom.class);
        BotaniaAPI.registerSubTileSignature(SubTileZephyrBloom.class, new SubTileZephyrBloom.Signature());
        BotaniaAPI.addSubTileToCreativeMenu(SUBTILE_ZEPHYR_BLOOM);
    }

    /// 特花方块是否已解析成功(Botania 在场且方块注册存在)。
    public static boolean hasSpecialFlowerBlock() {
        return specialFlowerBlock() != null;
    }

    /// 构造一枚指定种类的特花物品(带 type NBT)。
    public static ItemStack subtileStack(String type) {
        final ItemStack stack = new ItemStack(specialFlowerBlock());
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setString(SubTileEntity.TAG_TYPE, type);
        stack.setTagCompound(tag);
        return stack;
    }

    private static Block specialFlowerBlock() {
        // 特花在 Botania 的 preInit 注册, 故放在 init 阶段(各 mod preInit 完毕后)再解析
        if (specialFlowerBlock == null) {
            specialFlowerBlock = GameRegistry.findBlock("Botania", SPECIAL_FLOWER_BLOCK);
            if (specialFlowerBlock == null) Libertas.LOG.warn("Botania special flower block not found");
        }
        return specialFlowerBlock;
    }
}
