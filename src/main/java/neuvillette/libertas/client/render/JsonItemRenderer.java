package neuvillette.libertas.client.render;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing.VALUES;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.event.TextureStitchEvent;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.client.model.BakedModelQuadContext;
import com.gtnewhorizon.gtnhlib.client.model.ModelISBRH;
import com.gtnewhorizon.gtnhlib.client.model.baked.BakedModel;
import com.gtnewhorizon.gtnhlib.client.model.loading.ModelDeserializer.Position;
import com.gtnewhorizon.gtnhlib.client.model.loading.ModelDeserializer.Position.ModelDisplay;
import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.gtnewhorizon.gtnhlib.client.model.loading.ResourceLoc;
import com.gtnewhorizon.gtnhlib.client.model.unbaked.JSONModel;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/// A generic {@link IItemRenderer} which renders a standalone (non-ItemBlock) item using a GTNHLib JSON model
/// (assets/<domain>/models/<path>.json). Quads are baked against the block texture atlas, which is bound explicitly
/// since vanilla would normally bind the items atlas for these render paths.
///
/// Textures used by the model must be registered onto the block atlas; the ones referenced by the given model are
/// automatically gathered from assets/<domain>/models/** and stitched if the domain was registered via
/// {@link ModelRegistry#registerModid}, or can be stitched manually via
/// {@link TextureStitchEvent.Pre}.
@SideOnly(Side.CLIENT)
public class JsonItemRenderer implements IItemRenderer {

    private static final List<JsonItemRenderer> ALL_RENDERERS = new ArrayList<>();

    /// Invalidates every renderer's baked model. Called on TextureStitchEvent.Post when the block atlas is rebuilt
    /// (resource reload), since all baked sprite UVs become stale.
    public static void invalidateAll() {
        for (JsonItemRenderer renderer : ALL_RENDERERS) {
            renderer.bakedModel = null;
        }
    }

    private final ResourceLoc.ModelLoc modelLoc;
    private final ItemQuadContext context = new ItemQuadContext();

    private volatile BakedModel bakedModel;

    public JsonItemRenderer(String domain, String modelPath) {
        this.modelLoc = new ResourceLoc.ModelLoc(domain, modelPath);
        ALL_RENDERERS.add(this);
    }

    private BakedModel getModel() {
        BakedModel model = bakedModel;
        if (model == null) {
            JSONModel json = ModelRegistry.getJSONModel(modelLoc);
            model = json.bake();
            bakedModel = model;
        }
        return model;
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
        final BakedModel model = getModel();

        final Tessellator tessellator = TessellatorManager.get();
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        tessellator.startDrawingQuads();

        context.stack = stack;
        for (ModelQuadFacing facing : VALUES) {
            context.quadFacing = facing;
            for (ModelQuadView quad : model.getQuads(context)) {
                final float shade = ModelISBRH.diffuseLight(quad.getComputedFaceNormal());
                tessellator.setColorOpaque_F(shade, shade, shade);
                renderQuad(quad, tessellator);
            }
        }
        context.stack = null;
        context.quadFacing = null;

        applyItemDisplay(model, type);

        tessellator.draw();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static void renderQuad(ModelQuadView quad, Tessellator tessellator) {
        for (int i = 0; i < 4; ++i) {
            tessellator.addVertexWithUV(quad.getX(i), quad.getY(i), quad.getZ(i), quad.getTexU(i), quad.getTexV(i));
        }
    }

    private static final Vector3f DEFAULT_ROTATION = new Vector3f(0f, 0f, 0f);
    private static final Vector3f DEFAULT_TRANSLATION = new Vector3f(0f, 0f, 0f);
    private static final Vector3f DEFAULT_SCALE = new Vector3f(1f, 1f, 1f);

    /// Applies the model's BlockBench/NeoForge-style "display" transforms for the given render context. Mirrors
    /// ModelISBRH's item handling, which is package-private.
    private void applyItemDisplay(BakedModel model, ItemRenderType type) {
        final Position pos;
        switch (type) {
            case EQUIPPED:
                pos = Position.THIRDPERSON_RIGHTHAND;
                break;
            case EQUIPPED_FIRST_PERSON:
                pos = Position.FIRSTPERSON_RIGHTHAND;
                break;
            case INVENTORY:
                pos = Position.GUI;
                break;
            case ENTITY:
            default:
                pos = Position.GROUND;
                break;
        }

        final ModelDisplay display = model.getDisplay(pos, context);
        final Vector3f r = display.rotation();
        final Vector3f t = display.translation();
        final Vector3f s = display.scale();

        final float px = 0.5f;
        final float py = 0.5f;
        final float pz = 0.5f;

        switch (type) {
            case EQUIPPED:
                if (t.equals(DEFAULT_TRANSLATION)) {
                    GL11.glTranslatef(0f, 2.5f / 16f, 0f);
                } else {
                    GL11.glTranslatef(-t.z / 16f, t.y / 16f, t.x / 16f);
                }
                GL11.glTranslatef(px, py, pz);
                if (r.equals(DEFAULT_ROTATION)) {
                    GL11.glRotatef(75f, 0.0f, 0.0f, 1.0f);
                    GL11.glRotatef(45f, 0.0f, 1.0f, 0.0f);
                } else {
                    GL11.glRotatef(r.x, 0.0f, 0.0f, 1.0f);
                    GL11.glRotatef(r.y, 0.0f, 1.0f, 0.0f);
                    GL11.glRotatef(-r.z, 1.0f, 0.0f, 0.0f);
                }
                if (s.equals(DEFAULT_SCALE)) {
                    GL11.glScaled(0.375, 0.375, 0.375);
                } else {
                    GL11.glScaled(s.z, s.y, s.x);
                }
                GL11.glTranslatef(-px, -py, -pz);
                break;
            case EQUIPPED_FIRST_PERSON:
                if (!t.equals(DEFAULT_TRANSLATION)) {
                    GL11.glTranslatef(-t.z / 16f, t.y / 16f, t.x / 16f);
                }
                GL11.glTranslatef(px, py, pz);
                if (r.equals(DEFAULT_ROTATION)) {
                    GL11.glRotatef(45f, 0.0f, 1.0f, 0.0f);
                } else {
                    GL11.glRotatef(r.x, 0.0f, 0.0f, 1.0f);
                    GL11.glRotatef(r.y, 0.0f, 1.0f, 0.0f);
                    GL11.glRotatef(-r.z, 1.0f, 0.0f, 0.0f);
                }
                if (s.equals(DEFAULT_SCALE)) {
                    GL11.glScaled(0.4, 0.4, 0.4);
                } else {
                    GL11.glScaled(s.z, s.y, s.x);
                }
                GL11.glTranslatef(-px, -py, -pz);
                break;
            case ENTITY:
                if (t.equals(DEFAULT_TRANSLATION)) {
                    GL11.glTranslatef(0f, 3f / 16f, 0f);
                } else {
                    GL11.glTranslatef(-t.z / 16f, t.y / 16f, t.x / 16f);
                }
                GL11.glTranslatef(px, py, pz);
                if (!r.equals(DEFAULT_ROTATION)) {
                    GL11.glRotatef(r.x, 0.0f, 0.0f, 1.0f);
                    GL11.glRotatef(r.y, 0.0f, 1.0f, 0.0f);
                    GL11.glRotatef(-r.z, 1.0f, 0.0f, 0.0f);
                }
                if (s.equals(DEFAULT_SCALE)) {
                    GL11.glScaled(0.25, 0.25, 0.25);
                } else {
                    GL11.glScaled(s.z, s.y, s.x);
                }
                GL11.glTranslatef(-px, -py, -pz);
                break;
            case INVENTORY:
                if (!t.equals(DEFAULT_TRANSLATION)) {
                    GL11.glTranslatef(-t.z / 16f, t.y / 16f, t.x / 16f);
                }
                GL11.glTranslatef(px, py, pz);
                if (r.equals(DEFAULT_ROTATION)) {
                    GL11.glRotatef(30f, 0.0f, 0.0f, 1.0f);
                    GL11.glRotatef(-135f, 0.0f, 1.0f, 0.0f);
                } else {
                    GL11.glRotatef(r.x, 0.0f, 0.0f, 1.0f);
                    GL11.glRotatef(r.y, 0.0f, 1.0f, 0.0f);
                    GL11.glRotatef(-r.z, 1.0f, 0.0f, 0.0f);
                }
                if (s.equals(DEFAULT_SCALE)) {
                    GL11.glScaled(0.625, 0.625, 0.625);
                } else {
                    GL11.glScaled(s.z, s.y, s.x);
                }
                GL11.glTranslatef(-px, -py, -pz);
                break;
            default:
                break;
        }
    }

    /// Minimal item context. JSON item models don't consult block states, so a null state is fine here - unlike
    /// GTNHLib's ItemContext which builds a blockstate from the stack (and casts to ItemBlock, which would crash for
    /// standalone items).
    private static class ItemQuadContext implements BakedModelQuadContext.Item {

        ItemStack stack;
        ModelQuadFacing quadFacing;
        final Random random = new Random(0);

        @Override
        public ItemStack getItemStack() {
            return stack;
        }

        @Override
        public BlockState getBlockState() {
            return null;
        }

        @Override
        public ModelQuadFacing getQuadFacing() {
            return quadFacing;
        }

        @Override
        public Random getRandom() {
            return random;
        }

        @Override
        public java.util.function.Supplier<ModelQuadViewMutable> getQuadPool() {
            return null;
        }
    }
}
