package neuvillette.libertas;

import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import neuvillette.libertas.client.render.ClientEventHandler;
import neuvillette.libertas.client.render.JsonItemRenderer;
import neuvillette.libertas.items.ModItems;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Let GTNHLib scan this mod's resource pack for JSON models/textures
        ModelRegistry.registerModid(Libertas.MODID);

        // Texture stitching for JSON model sprites + baked-model invalidation on resource reload
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());

        // Register the JSON model renderer for 18cm的木棍
        MinecraftForgeClient
            .registerItemRenderer(ModItems.stick18cm, new JsonItemRenderer(Libertas.MODID, "item/18cm"));
    }
}
