package neuvillette.libertas.client.render;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.Libertas;

/// Renders a {@link JsonBakedModel} for a block in the world (ISBRH). The item form of the same block reuses
/// the same model JSON through {@link JsonItemRenderer}.
///
/// Model textures are stitched into the block atlas; chunk tessellation and texture bindings belong to the caller.
@SideOnly(Side.CLIENT)
public class JsonBlockRenderer implements ISimpleBlockRenderingHandler {

    private final ResourceLocation modelLoc;
    private final int renderId;
    private final Map<String, IIcon> textures = new HashMap<>();

    private volatile JsonBakedModel model;
    private volatile boolean baked = false;

    public JsonBlockRenderer(String domain, String modelPath) {
        this.modelLoc = new ResourceLocation(domain, "models/" + modelPath + ".json");
        this.renderId = RenderingRegistry.getNextAvailableRenderId();
        MinecraftForge.EVENT_BUS.register(this);
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

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) return;
        baked = false;
        model = null;
        textures.clear();
        ensureBaked();
        if (model == null) return;
        for (JsonBakedModel.Quad quad : model.quads) {
            if (textures.containsKey(quad.texture)) continue;
            final ResourceLocation texture = new ResourceLocation(
                quad.texture.indexOf(':') >= 0 ? quad.texture : modelLoc.getResourceDomain() + ":" + quad.texture);
            final String path = texture.getResourcePath();
            final String iconName = texture.getResourceDomain() + ":"
                + (path.startsWith("blocks/") ? path.substring("blocks/".length()) : path);
            textures.put(quad.texture, event.map.registerIcon(iconName));
        }
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        // Inventory display is handled by the JsonItemRenderer registered on the block's ItemBlock.
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        ensureBaked();
        if (model == null || model.quads.isEmpty()) return false;

        final Tessellator tessellator = Tessellator.instance;
        final int lightValue = block.getLightValue(world, x, y, z);
        for (JsonBakedModel.Quad quad : model.quads) {
            final ForgeDirection cullFace = quad.cullFace;
            if (!renderer.renderAllFaces && cullFace != ForgeDirection.UNKNOWN
                && !block.shouldSideBeRendered(
                    world,
                    x + cullFace.offsetX,
                    y + cullFace.offsetY,
                    z + cullFace.offsetZ,
                    cullFace.ordinal()))
                continue;

            final IIcon icon = renderer.hasOverrideBlockTexture() ? renderer.overrideBlockTexture
                : textures.get(quad.texture);
            if (icon == null) continue;
            tessellator.setBrightness(
                world.getLightBrightnessForSkyBlocks(x + quad.lightX, y + quad.lightY, z + quad.lightZ, lightValue));
            final float diffuseLight = quad.getDiffuseLight();
            tessellator.setColorOpaque_F(diffuseLight, diffuseLight, diffuseLight);
            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                tessellator.addVertexWithUV(
                    x + quad.x[vertexIndex],
                    y + quad.y[vertexIndex],
                    z + quad.z[vertexIndex],
                    icon.getInterpolatedU(quad.u[vertexIndex] * 16),
                    icon.getInterpolatedV(quad.v[vertexIndex] * 16));
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
