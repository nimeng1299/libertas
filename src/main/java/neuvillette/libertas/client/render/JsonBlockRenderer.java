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
///
/// With `rotateByMeta` set, the whole model is rotated around the Y axis by the block's metadata 2..5
/// (2=north: unrotated, 3=south: 180°, 4=west: 90°, 5=east: 270°) — for blocks with a furnace-style
/// facing whose model is authored front-towards-north.
@SideOnly(Side.CLIENT)
public class JsonBlockRenderer implements ISimpleBlockRenderingHandler {

    private final ResourceLocation modelLoc;
    private final int renderId;
    private final boolean rotateByMeta;
    private final Map<String, IIcon> textures = new HashMap<>();

    private volatile JsonBakedModel model;
    private volatile boolean baked = false;

    public JsonBlockRenderer(String domain, String modelPath) {
        this(domain, modelPath, false);
    }

    public JsonBlockRenderer(String domain, String modelPath, boolean rotateByMeta) {
        this.modelLoc = new ResourceLocation(domain, "models/" + modelPath + ".json");
        this.renderId = RenderingRegistry.getNextAvailableRenderId();
        this.rotateByMeta = rotateByMeta;
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
        final float cos, sin;
        if (rotateByMeta) {
            final float angle = rotationAngle(world.getBlockMetadata(x, y, z));
            cos = (float) Math.cos(Math.toRadians(angle));
            sin = (float) Math.sin(Math.toRadians(angle));
        } else {
            cos = 1f;
            sin = 0f;
        }
        for (JsonBakedModel.Quad quad : model.quads) {
            final ForgeDirection cullFace = rotateDirection(quad.cullFace, cos, sin);
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

            // 绕 (0.5, 0.5) 旋转 XZ 平面; 不旋转时 cos=1/sin=0 即恒等
            float sumX = 0, sumY = 0, sumZ = 0;
            final float[] rx = new float[4], ry = new float[4], rz = new float[4];
            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                final float dx = quad.x[vertexIndex] - 0.5f;
                final float dz = quad.z[vertexIndex] - 0.5f;
                rx[vertexIndex] = 0.5f + dx * cos + dz * sin;
                ry[vertexIndex] = quad.y[vertexIndex];
                rz[vertexIndex] = 0.5f - dx * sin + dz * cos;
                sumX += rx[vertexIndex];
                sumY += ry[vertexIndex];
                sumZ += rz[vertexIndex];
            }

            final float nx = quad.normal.x * cos + quad.normal.z * sin;
            final float nz = -quad.normal.x * sin + quad.normal.z * cos;
            final float diffuseLight = quad.shade
                ? 0.6f * nx * nx + (quad.normal.y > 0 ? 1f : 0.5f) * quad.normal.y * quad.normal.y + 0.8f * nz * nz
                : 1f;
            tessellator.setBrightness(
                world.getLightBrightnessForSkyBlocks(
                    x + lightOffset(sumX * 0.25f, nx),
                    y + quad.lightY,
                    z + lightOffset(sumZ * 0.25f, nz),
                    lightValue));
            tessellator.setColorOpaque_F(diffuseLight, diffuseLight, diffuseLight);
            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                tessellator.addVertexWithUV(
                    x + rx[vertexIndex],
                    y + ry[vertexIndex],
                    z + rz[vertexIndex],
                    icon.getInterpolatedU(quad.u[vertexIndex] * 16),
                    icon.getInterpolatedV(quad.v[vertexIndex] * 16));
            }
        }
        return true;
    }

    /// meta 2..5 -> 0/180/90/270 度 (模型正面按朝北绘制); 其余 meta 不旋转
    private static float rotationAngle(int meta) {
        switch (meta) {
            case 3:
                return 180f;
            case 4:
                return 90f;
            case 5:
                return 270f;
            default:
                return 0f;
        }
    }

    private static ForgeDirection rotateDirection(ForgeDirection dir, float cos, float sin) {
        if (dir == null || dir.offsetY != 0 || (sin == 0f && cos == 1f)) return dir;
        final int offsetX = Math.round(dir.offsetX * cos + dir.offsetZ * sin);
        final int offsetZ = Math.round(-dir.offsetX * sin + dir.offsetZ * cos);
        for (ForgeDirection candidate : ForgeDirection.VALID_DIRECTIONS) {
            if (candidate.offsetX == offsetX && candidate.offsetY == dir.offsetY && candidate.offsetZ == offsetZ)
                return candidate;
        }
        return dir;
    }

    /// 旋转后四边形中心的向下取整, 加法线同号微小偏移保证贴面取到自身一侧的光照
    private static int lightOffset(float center, float normal) {
        return (int) Math.floor(center + normal * 0.0001f);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        // Inventory display is handled by the JsonItemRenderer registered on the block's ItemBlock.
        return false;
    }
}
