package neuvillette.libertas;

import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import neuvillette.libertas.blocks.ModBlocks;
import neuvillette.libertas.client.render.JsonBlockRenderer;
import neuvillette.libertas.client.render.JsonItemRenderer;
import neuvillette.libertas.items.ModItems;

public class ClientProxy extends CommonProxy {

    /// ISBRH render id of the JSON block renderer. ClientProxy is only ever class-loaded on the client;
    /// the id is assigned in init(), long before any world rendering happens.
    public static int JSON_BLOCK_RENDER_ID = -1;
    /// 要素罐 (essentiaJar) 的 ISBRH render id, 同上
    public static int ESSENTIA_JAR_RENDER_ID = -1;
    /// 灵泉 (spiritSpring) 的 ISBRH render id, 同上
    public static int SPIRIT_SPRING_RENDER_ID = -1;
    /// 机械魔力池 (mechanicalManaPool) 的 ISBRH render id, 同上
    public static int MECHANICAL_MANA_POOL_RENDER_ID = -1;
    /// 机械符文祭坛 (mechanicalRunicAltar) 的 ISBRH render id, 同上
    public static int MECHANICAL_RUNIC_ALTAR_RENDER_ID = -1;
    /// 微型精灵门 (miniElfPortal) 的 ISBRH render id, 同上
    public static int MINI_ELF_PORTAL_RENDER_ID = -1;
    /// 工业凝聚板 (industrialAgglomerationPlate) 的 ISBRH render id, 同上
    public static int INDUSTRIAL_AGGLOMERATION_PLATE_RENDER_ID = -1;
    /// 机械花药台 (mechanicalApothecary) 的 ISBRH render id, 同上
    public static int MECHANICAL_APOTHECARY_RENDER_ID = -1;
    /// 机械白雏菊 (mechanicalDaisy) 的 ISBRH render id, 同上
    public static int MECHANICAL_DAISY_RENDER_ID = -1;

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register the JSON model renderer for 18cm的木棍
        MinecraftForgeClient
            .registerItemRenderer(ModItems.stick18cm, new JsonItemRenderer(Libertas.MODID, "item/18cm"));

        // Register the JSON model renderer for 紫色心情
        MinecraftForgeClient
            .registerItemRenderer(ModItems.purpleMood, new JsonItemRenderer(Libertas.MODID, "item/purpleMood"));

        // 拼图: 站立式拼图块, 按方块风格展示 (不做整体缩放/旋转)
        MinecraftForgeClient
            .registerItemRenderer(ModItems.jigsaw, new JsonItemRenderer(Libertas.MODID, "item/jigsaw", 1.0f, 0.0f));

        // 碧空之泪: 物品形态复用同一份 JSON 模型 (标准方块展示, 不做整体缩放/旋转),
        // 世界形态走 ISBRH 渲染同一份模型
        final JsonBlockRenderer azureTearRenderer = new JsonBlockRenderer(Libertas.MODID, "block/azureTear");
        RenderingRegistry.registerBlockHandler(azureTearRenderer.getRenderId(), azureTearRenderer);
        JSON_BLOCK_RENDER_ID = azureTearRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemAzureTear,
            new JsonItemRenderer(Libertas.MODID, "block/azureTear", 1.0f, 0.0f));

        // 要素罐: 同碧空之泪的渲染管线 (世界 ISBRH + 物品复用同一份 JSON 模型)
        final JsonBlockRenderer essentiaJarRenderer = new JsonBlockRenderer(Libertas.MODID, "block/essentiaJar");
        RenderingRegistry.registerBlockHandler(essentiaJarRenderer.getRenderId(), essentiaJarRenderer);
        ESSENTIA_JAR_RENDER_ID = essentiaJarRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemEssentiaJar,
            new JsonItemRenderer(Libertas.MODID, "block/essentiaJar", 1.0f, 0.0f));

        // 灵泉: 同碧空之泪的渲染管线 (世界 ISBRH + 物品复用同一份 JSON 模型)
        final JsonBlockRenderer spiritSpringRenderer = new JsonBlockRenderer(Libertas.MODID, "block/spiritSpring");
        RenderingRegistry.registerBlockHandler(spiritSpringRenderer.getRenderId(), spiritSpringRenderer);
        SPIRIT_SPRING_RENDER_ID = spiritSpringRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemSpiritSpring,
            new JsonItemRenderer(Libertas.MODID, "block/spiritSpring", 1.0f, 0.0f));

        // 机械魔力池: 同灵泉的渲染管线 (世界 ISBRH + 物品复用同一份 JSON 模型)
        final JsonBlockRenderer mechanicalManaPoolRenderer = new JsonBlockRenderer(
            Libertas.MODID,
            "block/mechanicalManaPool");
        RenderingRegistry.registerBlockHandler(mechanicalManaPoolRenderer.getRenderId(), mechanicalManaPoolRenderer);
        MECHANICAL_MANA_POOL_RENDER_ID = mechanicalManaPoolRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemMechanicalManaPool,
            new JsonItemRenderer(Libertas.MODID, "block/mechanicalManaPool", 1.0f, 0.0f));

        // 机械符文祭坛: 同机械魔力池的渲染管线
        final JsonBlockRenderer mechanicalRunicAltarRenderer = new JsonBlockRenderer(
            Libertas.MODID,
            "block/mechanicalRunicAltar");
        RenderingRegistry
            .registerBlockHandler(mechanicalRunicAltarRenderer.getRenderId(), mechanicalRunicAltarRenderer);
        MECHANICAL_RUNIC_ALTAR_RENDER_ID = mechanicalRunicAltarRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemMechanicalRunicAltar,
            new JsonItemRenderer(Libertas.MODID, "block/mechanicalRunicAltar", 1.0f, 0.0f));

        // 微型精灵门: 同机械魔力池的渲染管线
        final JsonBlockRenderer miniElfPortalRenderer = new JsonBlockRenderer(Libertas.MODID, "block/miniElfPortal");
        RenderingRegistry.registerBlockHandler(miniElfPortalRenderer.getRenderId(), miniElfPortalRenderer);
        MINI_ELF_PORTAL_RENDER_ID = miniElfPortalRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemMiniElfPortal,
            new JsonItemRenderer(Libertas.MODID, "block/miniElfPortal", 1.0f, 0.0f));

        // 工业凝聚板: 同机械魔力池的渲染管线
        final JsonBlockRenderer industrialAgglomerationPlateRenderer = new JsonBlockRenderer(
            Libertas.MODID,
            "block/industrialAgglomerationPlate");
        RenderingRegistry.registerBlockHandler(
            industrialAgglomerationPlateRenderer.getRenderId(),
            industrialAgglomerationPlateRenderer);
        INDUSTRIAL_AGGLOMERATION_PLATE_RENDER_ID = industrialAgglomerationPlateRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemIndustrialAgglomerationPlate,
            new JsonItemRenderer(Libertas.MODID, "block/industrialAgglomerationPlate", 1.0f, 0.0f));

        // 机械花药台: 同机械魔力池的渲染管线
        final JsonBlockRenderer mechanicalApothecaryRenderer = new JsonBlockRenderer(
            Libertas.MODID,
            "block/mechanicalApothecary");
        RenderingRegistry
            .registerBlockHandler(mechanicalApothecaryRenderer.getRenderId(), mechanicalApothecaryRenderer);
        MECHANICAL_APOTHECARY_RENDER_ID = mechanicalApothecaryRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemMechanicalApothecary,
            new JsonItemRenderer(Libertas.MODID, "block/mechanicalApothecary", 1.0f, 0.0f));

        // 机械白雏菊: 同机械魔力池的渲染管线
        final JsonBlockRenderer mechanicalDaisyRenderer = new JsonBlockRenderer(
            Libertas.MODID,
            "block/mechanicalDaisy");
        RenderingRegistry.registerBlockHandler(mechanicalDaisyRenderer.getRenderId(), mechanicalDaisyRenderer);
        MECHANICAL_DAISY_RENDER_ID = mechanicalDaisyRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemMechanicalDaisy,
            new JsonItemRenderer(Libertas.MODID, "block/mechanicalDaisy", 1.0f, 0.0f));
    }
}
