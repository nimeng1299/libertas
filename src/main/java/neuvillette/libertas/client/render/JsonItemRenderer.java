package neuvillette.libertas.client.render;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.Libertas;

/// Self-contained {@link IItemRenderer} that parses a Blockbench/Minecraft-style item JSON directly and renders it
/// the same way vanilla renders ItemBlocks - no GTNHLib baked-model pipeline. GTNHLib's item-model support is
/// incomplete (missing faces/bad transforms for standalone items), so this bypasses it while keeping the JSON format.
///
/// Model parsing/baking is shared with the in-world block renderer, see {@link JsonBakedModel}.
///
/// - GL state is restored afterwards so it doesn't corrupt the rest of the frame.
@SideOnly(Side.CLIENT)
public class JsonItemRenderer implements IItemRenderer {

    private final ResourceLocation modelLoc;
    private final List<JsonBakedModel.Quad> quads = new ArrayList<>();
    private volatile boolean baked = false;

    public JsonItemRenderer(String domain, String modelPath) {
        this(domain, modelPath, MODEL_SCALE_WAND, MODEL_SPIN_DEG_WAND);
    }

    /// Whole-model presentation tweaks, applied in model space before any per-path transform so all four render
    /// paths (GUI / both hands / ground) stay consistent. The anchor is the model's base centre (0.5, 0, 0.5),
    /// which is where the weapon is gripped - scaling keeps the hold point fixed and the weapon grows upward,
    /// and spinning turns it around its own vertical axis. Block-style models should pass 1.0f / 0.0f.
    private static final float MODEL_SCALE_WAND = 1.5f;
    /// Counter-clockwise (viewed from above, +Y down at the XZ plane) around the vertical axis; positive GL
    /// rotation about +Y moves +X toward -Z, which is exactly CCW in that view.
    private static final float MODEL_SPIN_DEG_WAND = 22.5f;

    private final float modelScale;
    private final float modelSpinDeg;
    private final boolean shaded;

    public JsonItemRenderer(String domain, String modelPath, float modelScale, float modelSpinDeg) {
        this(domain, modelPath, modelScale, modelSpinDeg, false);
    }

    public JsonItemRenderer(String domain, String modelPath, float modelScale, float modelSpinDeg, boolean shaded) {
        this.modelLoc = new ResourceLocation(domain, "models/" + modelPath + ".json");
        this.modelScale = modelScale;
        this.modelSpinDeg = modelSpinDeg;
        this.shaded = shaded;
        MinecraftForge.EVENT_BUS.register(this);
    }

    /// Rebake after resource reloads so texture/model file changes are picked up.
    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Post event) {
        baked = false;
    }

    private void ensureBaked() {
        if (baked) return;
        synchronized (this) {
            if (baked) return;
            quads.clear();
            try (InputStream is = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(modelLoc)
                .getInputStream()) {
                quads.addAll(JsonBakedModel.fromJson(is).quads);
                baked = true;
            } catch (Exception e) {
                Libertas.LOG.error("Failed to bake item model {}", modelLoc, e);
            }
        }
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        ensureBaked();
        if (quads.isEmpty()) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            // GL post-multiplies: the LAST emitted transform is applied to the vertices FIRST. The model tweak must
            // run on raw 0..1 model coordinates (innermost), with the per-path transform layered on top (outer).
            applyItemTransform(type);
            applyModelTweak();

            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // The Tessellator buffers everything and draws on draw() with the currently bound texture, so quads must
            // be flushed whenever the texture changes.
            final Tessellator t = Tessellator.instance;
            String boundTexture = null;
            boolean lighting = false;
            for (JsonBakedModel.Quad q : quads) {
                final boolean quadLighting = shaded && q.shade;
                if (!q.texture.equals(boundTexture) || lighting != quadLighting) {
                    if (boundTexture != null) t.draw();
                    bindTexture(q.texture);
                    boundTexture = q.texture;
                    lighting = quadLighting;
                    if (lighting) {
                        GL11.glEnable(GL11.GL_LIGHTING);
                    } else {
                        GL11.glDisable(GL11.GL_LIGHTING);
                    }
                    t.startDrawingQuads();
                }
                t.setColorOpaque_F(1f, 1f, 1f);
                t.setNormal(q.normal.x, q.normal.y, q.normal.z);
                for (int i = 0; i < 4; i++) {
                    t.addVertexWithUV(q.x[i], q.y[i], q.z[i], q.u[i], q.v[i]);
                }
            }
            if (boundTexture != null) t.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void applyModelTweak() {
        if (modelScale == 1.0f && modelSpinDeg == 0.0f) return;
        GL11.glTranslatef(0.5f, 0f, 0.5f);
        GL11.glScalef(modelScale, modelScale, modelScale);
        GL11.glRotatef(modelSpinDeg, 0f, 1f, 0f);
        GL11.glTranslatef(-0.5f, 0f, -0.5f);
    }

    private void applyItemTransform(ItemRenderType type) {
        // The model is drawn in 0..1 block space (JSON from/to divided by 16). What the caller has already applied
        // differs per path (verified against Forge 1.7.10), so the extra transform differs too:
        // - INVENTORY: ForgeHooksClient.renderInventoryItem applies the full GUI isometric transform (scale 10,
        // rotate 210X/45Y/-90Y) but NOT the centering - renderBlockAsItem's translate(-0.5) is on us.
        // - EQUIPPED_FIRST_PERSON: ItemRenderer applied translate(0.56,-0.52,-0.72), rotate 45Y, scale 0.4, and
        // ForgeHooksClient.renderEquippedItem centered with translate(-0.5). Nothing left to do - drawing the
        // model as-is looks exactly like a vanilla held block.
        // - ENTITY: ForgeHooksClient.renderEntityItem applied the spin rotation + scale 0.5; centering is on us.
        // - EQUIPPED: RenderBiped applied the flat-item hand pose (translate up 0.1875, scale 0.625 with -Y flip,
        // rotate -100X/45Y) and renderEquippedItem centered with translate(-0.5); a block-style model needs the
        // standard block-in-hand compensation on top to stand upright in the palm.
        switch (type) {
            case EQUIPPED:
                GL11.glTranslatef(0.0f, 0.25f, 0.0f);
                GL11.glTranslatef(0.5f, 0.5f, 0.5f);
                GL11.glRotatef(75f, 0f, 0f, 1f);
                GL11.glRotatef(45f, 0f, 1f, 0f);
                GL11.glScalef(0.375f, 0.375f, 0.375f);
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                break;
            case EQUIPPED_FIRST_PERSON:
                break;
            case ENTITY:
            case INVENTORY:
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                break;
            default:
                break;
        }
    }

    private void bindTexture(String texPath) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(JsonBakedModel.textureLocation(texPath, modelLoc.getResourceDomain()));
    }
}
