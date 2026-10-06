package neuvillette.libertas;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import cpw.mods.fml.common.registry.GameRegistry;
import neuvillette.libertas.blocks.ModBlocks;
import neuvillette.libertas.botania.ModBotania;
import neuvillette.libertas.items.ModItems;
import neuvillette.libertas.machines.ModMachines;
import thaumcraft.common.config.ConfigBlocks;

/**
 * 工作台配方。在 init 阶段、ModMachines.init() 之后注册：各 mod 的 preInit 已全部完成（TC 的
 * "quicksilver"、ThaumicBases 的 "blockQuicksilver" 等 OreDict 条目此时已就位），MTE 的 ItemStack
 * 也已构造完毕。
 */
public final class ModRecipes {

    private ModRecipes() {}

    public static void init() {
        // 水银 = TC 的 itemResource:3，TC 自身注册为 OreDict "quicksilver"
        // 水银块 = ThaumicBases 的方块；它同时把水银砖块也注册进了 "blockQuicksilver"，
        // 所以这里按注册名精确引用，避免配方吃进砖块
        final Block quicksilverBlock = GameRegistry.findBlock("thaumicbases", "quicksilverBlock");

        // 18cm 的木棍: 中列三根木棍
        GameRegistry
            .addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.stick18cm), " S ", " S ", " S ", 'S', "stickWood"));

        // 紫色心情: 顶行中间水银，下面两行中间木棍
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(ModItems.purpleMood),
                " M ",
                " S ",
                " S ",
                'M',
                "quicksilver",
                'S',
                "stickWood"));

        // 碧空之泪: 中列自上而下 水银 / 木棍 / 水银块（依赖 ThaumicBases）
        if (quicksilverBlock != null) {
            GameRegistry.addRecipe(
                new ShapedOreRecipe(
                    new ItemStack(ModBlocks.azureTear),
                    " M ",
                    " S ",
                    " B ",
                    'M',
                    "quicksilver",
                    'S',
                    "stickWood",
                    'B',
                    new ItemStack(quicksilverBlock)));
        } else {
            Libertas.LOG.warn("ThaumicBases not found, azure tear recipe skipped");
        }

        // 深海回响: 顶行三个圆石台阶（原版石台阶 meta 0，侧面即为圆石贴图），下面两行中间木棍
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(ModItems.deepSeaEcho),
                "CCC",
                " S ",
                " S ",
                'C',
                new ItemStack(Blocks.stone_slab, 1, 0),
                'S',
                "stickWood"));

        // 要素罐: 顶行三个任意木台阶，第二行两侧粘土，底行三个粘土
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(ModBlocks.essentiaJar),
                "WWW",
                "C C",
                "CCC",
                'W',
                "slabWood",
                'C',
                Items.clay_ball));

        // 拼图: 中间木棍，上下左右任意木板
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(ModItems.jigsaw),
                " P ",
                "PSP",
                " P ",
                'P',
                "plankWood",
                'S',
                "stickWood"));

        // 高级坩埚 / 秘纹织契 / 要素熔炼舱: 各自的核心方块 + 拼图，无序合成
        // 坩埚 = TC blockMetalDevice:0，符文矩阵 = TC blockStoneDevice:2（与 MTERunewovenPact 的结构引用一致）
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                ModMachines.advancedCrucible.copy(),
                new ItemStack(ConfigBlocks.blockMetalDevice, 1, 0),
                new ItemStack(ModItems.jigsaw)));
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                ModMachines.runewovenPact.copy(),
                new ItemStack(ConfigBlocks.blockStoneDevice, 1, 2),
                new ItemStack(ModItems.jigsaw)));
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                ModMachines.essentiaMelter.copy(),
                new ItemStack(ModBlocks.essentiaJar),
                new ItemStack(ModItems.jigsaw)));

        // 大巫师烤炉: 巫术巫师烤炉 + 拼图，无序合成
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                ModMachines.greatWizardOven.copy(),
                new ItemStack(com.emoniph.witchery.Witchery.Blocks.OVEN_IDLE, 1, 0),
                new ItemStack(ModItems.jigsaw)));

        // 巫师蒸馏塔: 巫术蒸馏塔 + 拼图，无序合成
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                ModMachines.wizardDistillationTower.copy(),
                new ItemStack(com.emoniph.witchery.Witchery.Blocks.DISTILLERY_IDLE, 1, 0),
                new ItemStack(ModItems.jigsaw)));

        // 大巫师炼药锅: 巫术巫师炼药锅(Witch's Cauldron, witchery:cauldron) + 拼图，无序合成
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                ModMachines.greatWizardCauldron.copy(),
                new ItemStack(com.emoniph.witchery.Witchery.Blocks.CAULDRON, 1, 0),
                new ItemStack(ModItems.jigsaw)));

        // 灵泉: 巫术祭坛(Witchery Altar, witchery:altar) + 拼图，无序合成。
        // 祭坛物品的 meta 会随绑定/多方块连接状态变化(默认掉落保留所在格的 meta)，用通配 meta 匹配
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                new ItemStack(ModBlocks.spiritSpring),
                new ItemStack(com.emoniph.witchery.Witchery.Blocks.ALTAR, 1, OreDictionary.WILDCARD_VALUE),
                new ItemStack(ModItems.jigsaw)));

        // 风息花: Botania 火红莲(endoflame 特花) + 拼图，无序合成。
        // 特花种类由 NBT 的 type 区分，Forge 原版无序配方不比对 NBT，须用带标签匹配的配方
        // 锁定火红莲，否则任意特花 + 拼图都能合成
        if (ModBotania.hasSpecialFlowerBlock()) {
            GameRegistry.addRecipe(
                new ShapelessNBTRecipe(
                    ModBotania.subtileStack(ModBotania.SUBTILE_ZEPHYR_BLOOM),
                    ModBotania.subtileStack("endoflame"),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania not found, zephyr bloom recipe skipped");
        }

        // 机械魔力池: Botania 魔力池 + 拼图，无序合成。
        // 用通配 meta 匹配，常规/创造/活力稀释/华丽魔力池均可作为基底
        final Block botaniaPool = GameRegistry.findBlock("Botania", "pool");
        if (botaniaPool != null) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(ModBlocks.mechanicalManaPool),
                    new ItemStack(botaniaPool, 1, OreDictionary.WILDCARD_VALUE),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania pool block not found, advanced mana pool recipe skipped");
        }
    }
}
