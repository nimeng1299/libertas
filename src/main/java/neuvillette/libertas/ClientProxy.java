package neuvillette.libertas;

import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import neuvillette.libertas.client.render.MistsplitterReforgedRenderer;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForgeClient.registerItemRenderer(ModItems.mistsplitterReforged, new MistsplitterReforgedRenderer());
    }
}
