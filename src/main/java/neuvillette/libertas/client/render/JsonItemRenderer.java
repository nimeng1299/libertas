package neuvillette.libertas.client.render;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.Libertas;

/// Self-contained {@link IItemRenderer} that parses a Blockbench/Minecraft-style item JSON directly and renders it
/// the same way vanilla renders ItemBlocks - no GTNHLib baked-model pipeline. GTNHLib's item-model support is
/// incomplete (missing faces/bad transforms for standalone items), so this bypasses it while keeping the JSON format.
///
/// - Elements are baked to quads in the 0..1 block-space cube (from/to divided by 16).
/// - Element `rotation` uses the correct T(origin)·R·T(-origin) transform.
/// - Face UVs map to a standalone texture bound as `assets/<domain>/textures/<name>.png` (any size, e.g. 64x64).
/// - GL state is restored afterwards so it doesn't corrupt the rest of the frame.
@SideOnly(Side.CLIENT)
public class JsonItemRenderer implements IItemRenderer {

    private final ResourceLocation modelLoc;
    private final List<BakedQuad> quads = new ArrayList<>();

    private volatile boolean baked = false;

    public JsonItemRenderer(String domain, String modelPath) {
        this.modelLoc = new ResourceLocation(domain, "models/" + modelPath + ".json");
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
                JsonObject root = new JsonParser().parse(new InputStreamReader(is))
                    .getAsJsonObject();
                bake(root);
                baked = true;
            } catch (Exception e) {
                Libertas.LOG.error("Failed to bake item model {}", modelLoc, e);
            }
        }
    }

    private void bake(JsonObject root) {
        final JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : new JsonObject();
        final JsonArray elements = root.has("elements") ? root.getAsJsonArray("elements") : new JsonArray();

        for (JsonElement el : elements) {
            final JsonObject obj = el.getAsJsonObject();
            final Vector3f from = loadVec3(obj.getAsJsonArray("from")).div(16f);
            final Vector3f to = loadVec3(obj.getAsJsonArray("to")).div(16f);

            final Matrix4f rot = obj.has("rotation") ? parseRotation(obj.getAsJsonObject("rotation")) : null;

            final JsonObject faces = obj.has("faces") ? obj.getAsJsonObject("faces") : new JsonObject();
            for (String faceName : new String[] { "north", "south", "east", "west", "up", "down" }) {
                if (!faces.has(faceName)) continue;
                final JsonObject face = faces.getAsJsonObject(faceName);
                final String texRef = face.get("texture")
                    .getAsString();
                final String texPath = resolveTexture(textures, texRef);
                final Vector4f uv = face.has("uv") ? loadVec4(face.getAsJsonArray("uv"))
                    : defaultUV(faceName, from, to);

                quads.add(new BakedQuad(faceName, from, to, rot, uv, texPath));
            }
        }
    }

    private static Matrix4f parseRotation(JsonObject json) {
        final Vector3f origin = loadVec3(json.getAsJsonArray("origin")).div(16f);
        final float angle = (float) Math.toRadians(
            json.get("angle")
                .getAsFloat());
        final boolean rescale = json.has("rescale") && json.get("rescale")
            .getAsBoolean();

        final Matrix4f m = new Matrix4f();
        // translate to origin, rotate, translate back: T(o)·R·T(-o)
        m.translate(origin.x, origin.y, origin.z);
        switch (json.get("axis")
            .getAsString()) {
            case "x":
                m.rotateX(angle);
                break;
            case "y":
                m.rotateY(angle);
                break;
            case "z":
                m.rotateZ(angle);
                break;
            default:
                break;
        }
        m.translate(-origin.x, -origin.y, -origin.z);
        if (rescale) {
            // rescale to prevent 45-degree squishing - approximate by scaling the non-axis axes
            final float s = (float) Math.sqrt(2);
            switch (json.get("axis")
                .getAsString()) {
                case "x":
                    m.scale(1, s, s);
                    break;
                case "y":
                    m.scale(s, 1, s);
                    break;
                case "z":
                    m.scale(s, s, 1);
                    break;
                default:
                    break;
            }
        }
        return m;
    }

    private static Vector3f loadVec3(JsonArray a) {
        return new Vector3f(
            a.get(0)
                .getAsFloat(),
            a.get(1)
                .getAsFloat(),
            a.get(2)
                .getAsFloat());
    }

    private static Vector4f loadVec4(JsonArray a) {
        return new Vector4f(
            a.get(0)
                .getAsFloat(),
            a.get(1)
                .getAsFloat(),
            a.get(2)
                .getAsFloat(),
            a.get(3)
                .getAsFloat());
    }

    private static Vector4f defaultUV(String face, Vector3f from, Vector3f to) {
        // vanilla-style default uvs mapped to 0..16 pixel space
        switch (face) {
            case "up":
            case "down":
                return new Vector4f(from.x * 16, from.z * 16, to.x * 16, to.z * 16);
            case "north":
            case "south":
                return new Vector4f(from.x * 16, from.y * 16, to.x * 16, to.y * 16);
            case "east":
            case "west":
                return new Vector4f(from.z * 16, from.y * 16, to.z * 16, to.y * 16);
            default:
                return new Vector4f(0, 0, 16, 16);
        }
    }

    private static String resolveTexture(JsonObject textures, String ref) {
        // "#0" -> textures["0"]; if the value itself starts with "#", follow the chain.
        String key = ref.startsWith("#") ? ref.substring(1) : ref;
        String val = textures.has(key) ? textures.get(key)
            .getAsString() : ref;
        while (val.startsWith("#") && textures.has(val.substring(1))) {
            val = textures.get(val.substring(1))
                .getAsString();
        }
        return val;
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
            applyItemTransform(type);

            // The texture is authored emissive in Blockbench (full-bright, flat preview), so faces render unshaded
            // to match. Lighting is disabled; the per-vertex color is still written explicitly so a glColor4f left
            // over by callers cannot tint the model. Alpha test (instead of blending) keeps any cutout pixels from
            // writing depth and occluding quads behind them.
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // The Tessellator buffers everything and draws on draw() with the currently bound texture, so quads must
            // be flushed whenever the texture changes.
            final Tessellator t = Tessellator.instance;
            String boundTexture = null;
            for (BakedQuad q : quads) {
                if (!q.texture.equals(boundTexture)) {
                    if (boundTexture != null) t.draw();
                    bindTexture(q.texture);
                    boundTexture = q.texture;
                    t.startDrawingQuads();
                }
                t.setColorOpaque_F(1f, 1f, 1f);
                emitQuad(q, t);
            }
            if (boundTexture != null) t.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
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
        // textures/<path>.png, in the model's namespace
        final ResourceLocation loc;
        final int colon = texPath.indexOf(':');
        if (colon >= 0) {
            loc = new ResourceLocation(
                texPath.substring(0, colon),
                "textures/" + texPath.substring(colon + 1) + ".png");
        } else {
            loc = new ResourceLocation(modelLoc.getResourceDomain(), "textures/" + texPath + ".png");
        }
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(loc);
    }

    private void emitQuad(BakedQuad q, Tessellator t) {
        for (int i = 0; i < 4; i++) {
            t.addVertexWithUV(q.x[i], q.y[i], q.z[i], q.u[i], q.v[i]);
        }
    }

    /// A single pre-baked quad: 4 vertices + uvs + texture + face name.
    private static final class BakedQuad {

        final String face;
        final float[] x = new float[4];
        final float[] y = new float[4];
        final float[] z = new float[4];
        final float[] u = new float[4];
        final float[] v = new float[4];
        final String texture;

        BakedQuad(String face, Vector3f from, Vector3f to, Matrix4f rot, Vector4f uv, String texture) {
            this.face = face;
            this.texture = texture;
            buildVertices(face, from, to, rot);
            buildUVs(face, uv);
        }

        /// Builds CCW vertices for the given face (correct outward winding in vanilla's coordinate system).
        private void buildVertices(String face, Vector3f from, Vector3f to, Matrix4f rot) {
            final Vector3f[] verts;
            switch (face) {
                case "north": // -Z
                    verts = new Vector3f[] { new Vector3f(to.x, to.y, from.z), new Vector3f(to.x, from.y, from.z),
                        new Vector3f(from.x, from.y, from.z), new Vector3f(from.x, to.y, from.z) };
                    break;
                case "south": // +Z
                    verts = new Vector3f[] { new Vector3f(from.x, to.y, to.z), new Vector3f(from.x, from.y, to.z),
                        new Vector3f(to.x, from.y, to.z), new Vector3f(to.x, to.y, to.z) };
                    break;
                case "east": // +X
                    verts = new Vector3f[] { new Vector3f(to.x, to.y, to.z), new Vector3f(to.x, from.y, to.z),
                        new Vector3f(to.x, from.y, from.z), new Vector3f(to.x, to.y, from.z) };
                    break;
                case "west": // -X
                    verts = new Vector3f[] { new Vector3f(from.x, to.y, from.z), new Vector3f(from.x, from.y, from.z),
                        new Vector3f(from.x, from.y, to.z), new Vector3f(from.x, to.y, to.z) };
                    break;
                case "up": // +Y
                    verts = new Vector3f[] { new Vector3f(from.x, to.y, from.z), new Vector3f(from.x, to.y, to.z),
                        new Vector3f(to.x, to.y, to.z), new Vector3f(to.x, to.y, from.z) };
                    break;
                case "down": // -Y
                    verts = new Vector3f[] { new Vector3f(from.x, from.y, to.z), new Vector3f(from.x, from.y, from.z),
                        new Vector3f(to.x, from.y, from.z), new Vector3f(to.x, from.y, to.z) };
                    break;
                default:
                    verts = new Vector3f[] { new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f() };
                    break;
            }

            for (int i = 0; i < 4; i++) {
                if (rot != null) verts[i].mulPosition(rot);
                x[i] = verts[i].x;
                y[i] = verts[i].y;
                z[i] = verts[i].z;
            }
        }

        /// Assigns UVs in the same winding order as the vertices (normalized 0..1 against a 16-pixel convention).
        private void buildUVs(String face, Vector4f uv) {
            switch (face) {
                case "up":
                    u[0] = uv.x;
                    v[0] = uv.y;
                    u[1] = uv.x;
                    v[1] = uv.w;
                    u[2] = uv.z;
                    v[2] = uv.w;
                    u[3] = uv.z;
                    v[3] = uv.y;
                    break;
                case "down":
                    u[0] = uv.x;
                    v[0] = uv.w;
                    u[1] = uv.x;
                    v[1] = uv.y;
                    u[2] = uv.z;
                    v[2] = uv.y;
                    u[3] = uv.z;
                    v[3] = uv.w;
                    break;
                case "north":
                case "south":
                case "east":
                case "west":
                default:
                    u[0] = uv.x;
                    v[0] = uv.y;
                    u[1] = uv.x;
                    v[1] = uv.w;
                    u[2] = uv.z;
                    v[2] = uv.w;
                    u[3] = uv.z;
                    v[3] = uv.y;
                    break;
            }
            for (int i = 0; i < 4; i++) {
                u[i] /= 16f;
                v[i] /= 16f;
            }
        }
    }
}
