package neuvillette.libertas;

import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import neuvillette.libertas.client.render.JsonItemRenderer;
import neuvillette.libertas.items.ModItems;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register the JSON model renderer for 18cm的木棍
        MinecraftForgeClient
            .registerItemRenderer(ModItems.stick18cm, new JsonItemRenderer(Libertas.MODID, "item/18cm"));

        // Register the JSON model renderer for 紫色心情
        MinecraftForgeClient
            .registerItemRenderer(ModItems.purpleMood, new JsonItemRenderer(Libertas.MODID, "item/purpleMood"));
    }
}
