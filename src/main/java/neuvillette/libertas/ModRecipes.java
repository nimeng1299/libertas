package neuvillette.libertas;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
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

        // 棉签: 线的左下角放木棍 (2x2 有序, 2x2 合成格即可摆放)
        GameRegistry.addRecipe(
            new ShapedOreRecipe(new ItemStack(ModItems.cottonSwab), " S", "T ", 'S', Items.string, 'T', "stickWood"));

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
            Libertas.LOG.warn("Botania pool block not found, mechanical mana pool recipe skipped");
        }

        // 机械符文祭坛: Botania 符文祭坛 + 拼图，无序合成
        final Block botaniaRuneAltar = GameRegistry.findBlock("Botania", "runeAltar");
        if (botaniaRuneAltar != null) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(ModBlocks.mechanicalRunicAltar),
                    new ItemStack(botaniaRuneAltar, 1, OreDictionary.WILDCARD_VALUE),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania runic altar block not found, mechanical runic altar recipe skipped");
        }

        // 微型精灵门: 3x3 有序合成
        // 源质钢锭(elementium) / 梦之木 / 源质钢锭
        // 微光活木(livingwood:5) / 拼图 / 微光活木
        // 源质钢锭 / 精灵门核心(alfheimPortal, 通配 meta) / 源质钢锭
        final Item botaniaManaResource = GameRegistry.findItem("Botania", "manaResource");
        final Block dreamwood = GameRegistry.findBlock("Botania", "dreamwood");
        final Block livingwood = GameRegistry.findBlock("Botania", "livingwood");
        final Block alfheimPortal = GameRegistry.findBlock("Botania", "alfheimPortal");
        if (botaniaManaResource != null && dreamwood != null && livingwood != null && alfheimPortal != null) {
            GameRegistry.addRecipe(
                new ShapedOreRecipe(
                    new ItemStack(ModBlocks.miniElfPortal),
                    "EDE",
                    "SJS",
                    "ECE",
                    'E',
                    new ItemStack(botaniaManaResource, 1, 7),
                    'D',
                    new ItemStack(dreamwood, 1, 0),
                    'S',
                    new ItemStack(livingwood, 1, 5),
                    'C',
                    new ItemStack(alfheimPortal, 1, OreDictionary.WILDCARD_VALUE),
                    'J',
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania elven portal materials not found, mini elf portal recipe skipped");
        }

        // 工业凝聚板: Botania 泰拉凝聚板 + 拼图，无序合成
        final Block terraPlate = GameRegistry.findBlock("Botania", "terraPlate");
        if (terraPlate != null) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(ModBlocks.industrialAgglomerationPlate),
                    new ItemStack(terraPlate, 1, OreDictionary.WILDCARD_VALUE),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania terra plate block not found, industrial agglomeration plate recipe skipped");
        }

        // 机械花药台: Botania 花药台 + 拼图，无序合成 (通配 meta, 圆石/各生态石研钵均可)
        final Block altar = GameRegistry.findBlock("Botania", "altar");
        if (altar != null) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(ModBlocks.mechanicalApothecary),
                    new ItemStack(altar, 1, OreDictionary.WILDCARD_VALUE),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania altar block not found, mechanical apothecary recipe skipped");
        }

        // 机械白雏菊: Botania 白雏菊(pure daisy 特花) + 拼图，无序合成。
        // 特花种类由 NBT 的 type 区分，Forge 原版无序配方不比对 NBT，须用带标签匹配的配方锁定白雏菊
        if (ModBotania.hasSpecialFlowerBlock()) {
            GameRegistry.addRecipe(
                new ShapelessNBTRecipe(
                    new ItemStack(ModBlocks.mechanicalDaisy),
                    ModBotania.subtileStack("puredaisy"),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania not found, mechanical daisy recipe skipped");
        }

        // 机械植物酿造台: Botania 植物酿造台 + 拼图，无序合成
        final Block brewery = GameRegistry.findBlock("Botania", "brewery");
        if (brewery != null) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(ModBlocks.mechanicalBrewery),
                    new ItemStack(brewery, 1, OreDictionary.WILDCARD_VALUE),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("Botania brewery block not found, mechanical brewery recipe skipped");
        }

        // 赤泉: 血魔法献祭刀(AWWayofTime:sacrificialKnife) + 拼图，无序合成。
        // 灵泉配方里巫术祭坛的同款思路: 直接按注册名取献祭刀, 不写死物品类引用
        final Item sacrificialKnife = GameRegistry.findItem("AWWayofTime", "sacrificialKnife");
        if (sacrificialKnife != null) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(ModBlocks.crimsonSpring),
                    new ItemStack(sacrificialKnife),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("BloodMagic sacrificial knife not found, crimson spring recipe skipped");
        }

        // 猩红祭仪: 血魔法血之祭坛(AWWayofTime:Altar) + 拼图，无序合成
        final Block bloodAltar = GameRegistry.findBlock("AWWayofTime", "Altar");
        if (bloodAltar != null) {
            GameRegistry.addRecipe(
                new ShapelessOreRecipe(
                    new ItemStack(ModBlocks.sanguineRite),
                    new ItemStack(bloodAltar, 1, OreDictionary.WILDCARD_VALUE),
                    new ItemStack(ModItems.jigsaw)));
        } else {
            Libertas.LOG.warn("BloodMagic altar block not found, sanguine rite recipe skipped");
        }

        // 迷你矿机: 基岩 + 拼图，无序合成
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                ModMachines.miniMining.copy(),
                new ItemStack(Blocks.bedrock),
                new ItemStack(ModItems.jigsaw)));
    }
}
