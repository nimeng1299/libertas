package neuvillette.libertas.client.render;

import net.minecraftforge.client.event.TextureStitchEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.Libertas;

/// Client-side event handlers. Kept as a public named class - anonymous/inner handlers crash ASMEventHandler with an
/// IllegalAccessError on 1.7.10.
@SideOnly(Side.CLIENT)
public class ClientEventHandler {

    /// Model textures are resolved against the block texture atlas, so our item texture must be stitched there.
    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            // GTNHLib's MixinTextureMap resolves marked sprites to "textures/<name>.png" (no implied base
            // directory), so the name must include "blocks/".
            event.map.registerIcon(Libertas.MODID + ":blocks/sakura_tech_stick");
        }
    }

    /// After a resource reload the atlas has new UVs - all baked JSON models must be re-baked.
    @SubscribeEvent
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() == 0) {
            JsonItemRenderer.invalidateAll();
        }
    }
}
