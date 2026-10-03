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

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register the JSON model renderer for 18cm的木棍
        MinecraftForgeClient
            .registerItemRenderer(ModItems.stick18cm, new JsonItemRenderer(Libertas.MODID, "item/18cm"));

        // Register the JSON model renderer for 紫色心情
        MinecraftForgeClient
            .registerItemRenderer(ModItems.purpleMood, new JsonItemRenderer(Libertas.MODID, "item/purpleMood"));

        // 碧空之泪: 物品形态复用同一份 JSON 模型 (标准方块展示, 不做整体缩放/旋转),
        // 世界形态走 ISBRH 渲染同一份模型
        final JsonBlockRenderer azureTearRenderer = new JsonBlockRenderer(Libertas.MODID, "block/azureTear");
        RenderingRegistry.registerBlockHandler(azureTearRenderer.getRenderId(), azureTearRenderer);
        JSON_BLOCK_RENDER_ID = azureTearRenderer.getRenderId();
        MinecraftForgeClient.registerItemRenderer(
            ModBlocks.itemAzureTear,
            new JsonItemRenderer(Libertas.MODID, "block/azureTear", 1.0f, 0.0f));
    }
}
