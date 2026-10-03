package neuvillette.libertas.client.render;

import java.io.InputStream;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.Libertas;

/// Renders a {@link JsonBakedModel} for a block in the world (ISBRH). The item form of the same block reuses
/// the same model JSON through {@link JsonItemRenderer}.
///
/// The world renderer keeps ONE tessellator session per chunk section with the block atlas bound, so switching
/// to the model's own texture requires flushing with draw() before and after the custom quads, then restoring
/// the atlas binding and leaving an open session for the rest of the chunk.
@SideOnly(Side.CLIENT)
public class JsonBlockRenderer implements ISimpleBlockRenderingHandler {

    private final ResourceLocation modelLoc;
    private final int renderId;

    private volatile JsonBakedModel model;
    private volatile boolean baked = false;

    public JsonBlockRenderer(String domain, String modelPath) {
        this.modelLoc = new ResourceLocation(domain, "models/" + modelPath + ".json");
        this.renderId = RenderingRegistry.getNextAvailableRenderId();
    }

    /// The render id blocks must report from {@link Block#getRenderType}; register with
    /// `RenderingRegistry.registerBlockHandler(renderer.getRenderId(), renderer)`.
    public int getRenderId() {
        return renderId;
    }

    private void ensureBaked() {
        if (baked) return;
        synchronized (this) {
            if (baked) return;
            try (InputStream is = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(modelLoc)
                .getInputStream()) {
                model = JsonBakedModel.fromJson(is);
                baked = true;
            } catch (Exception e) {
                // leave baked = false so the next frame retries (same behaviour as JsonItemRenderer)
                Libertas.LOG.error("Failed to bake block model {}", modelLoc, e);
            }
        }
    }

    private void bindTexture(String texPath) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(JsonBakedModel.textureLocation(texPath, modelLoc.getResourceDomain()));
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        // Inventory display is handled by the JsonItemRenderer registered on the block's ItemBlock.
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        ensureBaked();
        if (model == null || model.quads.isEmpty()) return true;

        final Tessellator t = Tessellator.instance;
        final int brightness = block.getMixedBrightnessForBlock(world, x, y, z);

        String bound = null;
        try {
            for (JsonBakedModel.Quad q : model.quads) {
                if (!q.texture.equals(bound)) {
                    // First group: the outer session still holds the chunk's atlas quads - draw() flushes them
                    // while the atlas is (still) bound. Later groups: flush the previous custom group.
                    t.draw();
                    bindTexture(q.texture);
                    bound = q.texture;
                    t.startDrawingQuads();
                }
                t.setBrightness(brightness);
                t.setColorOpaque_F(1f, 1f, 1f);
                for (int i = 0; i < 4; i++) {
                    t.addVertexWithUV(x + q.x[i], y + q.y[i], z + q.z[i], q.u[i], q.v[i]);
                }
            }
        } finally {
            if (bound != null) {
                t.draw();
                // Restore the block atlas and leave an open session for the remaining chunk quads.
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(TextureMap.locationBlocksTexture);
                t.startDrawingQuads();
            }
        }
        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        // Inventory display is handled by the JsonItemRenderer registered on the block's ItemBlock.
        return false;
    }
}
