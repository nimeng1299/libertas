package neuvillette.libertas.client.render;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.Libertas;
import neuvillette.libertas.ModItems;

/**
 * Renders the Mistsplitter Reforged katana.
 *
 * <p>
 * The geometry is kept in the original Blockbench element format (converted to a mod resource,
 * assets/libertas/models/mistsplitter_reforged.json), parsed once into a flat vertex array and
 * drawn with immediate-mode GL.
 *
 * <p>
 * Render behaviour per context:
 * <ul>
 * <li>EQUIPPED / EQUIPPED_FIRST_PERSON: 3D model drawn in vanilla icon space so it lines up like an
 * item icon (blade diagonal, hilt bottom-left).</li>
 * <li>ENTITY: dropped item, model lies flat and spins like vanilla items.</li>
 * <li>ENTITY inside an item frame: simple upright icon quad.</li>
 * <li>INVENTORY: the item icon is drawn as a plain 16x16 quad.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MistsplitterReforgedRenderer implements IItemRenderer {

    private static final ResourceLocation MODEL = new ResourceLocation(
        Libertas.MODID,
        "models/mistsplitter_reforged.json");

    /**
     * Parsed model: quads, 4 vertices each, stride 8 floats per vertex
     * (x, y, z, u, v, nx, ny, nz). Null until parsed; empty array if parsing failed.
     */
    private float[] modelData = null;

    /** Centre of the model's bounding box in model units, filled when the model is parsed. */
    private static final float[] MODEL_CENTRE = { 8.0F, 8.0F, 8.0F };

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null && item.getItem() == ModItems.mistsplitterReforged
            && type != ItemRenderType.FIRST_PERSON_MAP;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        switch (helper) {
            case ENTITY_ROTATION:
            case ENTITY_BOBBING:
                return true;
            case EQUIPPED_BLOCK:
                return false;
            default:
                // BLOCK_3D / INVENTORY_BLOCK stay false.
                return false;
        }
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        try {
            switch (type) {
                case INVENTORY:
                    drawIcon2D(item, 16, 16);
                    break;
                case EQUIPPED:
                    if (this.ensureModel()) {
                        drawExtrudedIcon(item);
                        drawEquippedThirdPerson();
                    } else {
                        drawIcon2D(item, 1, 1);
                    }
                    break;
                case EQUIPPED_FIRST_PERSON:
                    // First person: draw the item as the vanilla extruded icon so it is
                    // always visible and correctly oriented; the custom transform chain
                    // is identical to what vanilla does for ItemRenderer#renderItemIn2D.
                    drawExtrudedIcon(item);
                    break;
                case ENTITY:
                    if (RenderItem.renderInFrame) {
                        GL11.glRotatef(-45.0F, 0.0F, 0.0F, 1.0F);
                        drawIcon2D(item, 1, 1);
                    } else if (this.ensureModel()) {
                        drawEntity();
                    }
                    break;
                default:
                    break;
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Third person. Draws the katana centred where vanilla draws the held icon
     * (upper-left of the hand anchor), rolled 180 degrees to point the blade
     * tip up and the hilt into the hand.
     */
    private void drawEquippedThirdPerson() {
        GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
        drawModelScaled(1.3F);
    }

    /**
     * First person. Same placement as third person plus a slight forward push
     * so the model stays clear of the 0.05 near clip plane.
     */
    /**
     * First person held: vanilla-equivalent of ItemRenderer#renderItemIn2D using
     * the item icon so the katana shows as the familiar "icon with thickness".
     * Draws in icon space ([0,1]x[0,1] on XY, extruded along -z by 1/16).
     */
    private void drawExtrudedIcon(ItemStack item) {
        IIcon icon = item.getItem()
            .getIconFromDamage(item.getItemDamage());
        if (icon == null) {
            return;
        }
        net.minecraft.client.renderer.ItemRenderer.renderItemIn2D(
            Tessellator.instance,
            icon.getMaxU(),
            icon.getMinV(),
            icon.getMinU(),
            icon.getMaxV(),
            icon.getIconWidth(),
            icon.getIconHeight(),
            0.0625F);
    }

    /** Dropped item: model lies flat on the ground and rotates via the entity rotation helper. */
    private void drawEntity() {
        // Lift it a little above the entity origin.
        GL11.glTranslatef(0.0F, 0.1F, 0.0F);
        // Lay the blade flat.
        GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
        drawModelScaled(1.2F);
    }

    /**
     * Draws the model, converting Blockbench's 0..16 unit space to a 1-unit object
     * centred on the current origin, then applying the given extra scale.
     */
    private void drawModelScaled(float scale) {
        GL11.glScalef(scale, scale, scale);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glPushMatrix();
        GL11.glScalef(0.0625F, 0.0625F, 0.0625F);
        GL11.glTranslatef(-MODEL_CENTRE[0], -MODEL_CENTRE[1], -MODEL_CENTRE[2]);
        drawModel();
        GL11.glPopMatrix();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
    }

    /** Lazily parses the model; returns false if the parse failed. */
    private boolean ensureModel() {
        if (this.modelData == null) {
            this.modelData = parseModel();
            if (this.modelData == null) {
                this.modelData = new float[0];
            }
        }
        return this.modelData.length > 0;
    }

    /** Emits every quad of the parsed model in immediate mode. */
    private void drawModel() {
        float[] d = this.modelData;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < d.length; i += 8) {
            GL11.glNormal3f(d[i + 5], d[i + 6], d[i + 7]);
            GL11.glTexCoord2f(d[i + 3], d[i + 4]);
            GL11.glVertex3f(d[i], d[i + 1], d[i + 2]);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /** Simple textured icon quad for inventory slots and item frames. */
    private void drawIcon2D(ItemStack item, float w, float h) {
        IIcon icon = item.getItem()
            .getIconFromDamage(item.getItemDamage());
        if (icon == null) {
            return;
        }
        Tessellator t = Tessellator.instance;
        float u1 = icon.getMinU();
        float u2 = icon.getMaxU();
        float v1 = icon.getMinV();
        float v2 = icon.getMaxV();
        // Same vertex order as RenderItem#renderIcon (counter-clockwise front face,
        // otherwise the quad is culled).
        t.startDrawingQuads();
        t.setNormal(0.0F, 0.0F, 1.0F);
        t.addVertexWithUV(0.0D, h, 0.0D, u1, v2);
        t.addVertexWithUV(w, h, 0.0D, u2, v2);
        t.addVertexWithUV(w, 0.0D, 0.0D, u2, v1);
        t.addVertexWithUV(0.0D, 0.0D, 0.0D, u1, v1);
        t.draw();
    }

    /**
     * Parses the Blockbench-style element model into a flat vertex array.
     * Only axis-aligned elements are supported; elements with non-zero rotation are skipped.
     * Texture coordinates are translated through the item's atlas sprite because Forge
     * binds the items TextureMap before invoking this renderer.
     *
     * @return flat array (x, y, z, u, v, nx, ny, nz) * 4 per quad, or null on failure
     */
    private static float[] parseModel() {
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(MODEL)
            .getInputStream(); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            JsonObject root = new JsonParser().parse(reader)
                .getAsJsonObject();
            JsonArray texSize = root.has("texture_size") ? root.getAsJsonArray("texture_size") : null;
            float texW = texSize != null ? texSize.get(0)
                .getAsFloat() : 16.0F;
            float texH = texSize != null ? texSize.get(1)
                .getAsFloat() : 16.0F;

            IIcon icon = ModItems.mistsplitterReforged.getIconFromDamage(0);
            if (icon == null) {
                Libertas.LOG.error("Mistsplitter Reforged: item icon is not registered yet");
                return null;
            }

            float[] bounds = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
                -Float.MAX_VALUE };

            List<Float> out = new ArrayList<Float>();
            for (JsonElement elementEl : root.getAsJsonArray("elements")) {
                JsonObject element = elementEl.getAsJsonObject();
                if (element.has("rotation") && element.get("rotation")
                    .isJsonObject()) {
                    JsonObject rotation = element.getAsJsonObject("rotation");
                    if (rotation.has("angle") && rotation.get("angle")
                        .getAsFloat() != 0.0F) {
                        // Only axis-aligned elements are supported.
                        continue;
                    }
                }
                float[] from = vec3(element.getAsJsonArray("from"));
                float[] to = vec3(element.getAsJsonArray("to"));
                for (int a = 0; a < 3; a++) {
                    bounds[a] = Math.min(bounds[a], from[a]);
                    bounds[a + 3] = Math.max(bounds[a + 3], to[a]);
                }
                JsonObject faces = element.getAsJsonObject("faces");
                if (faces == null) {
                    continue;
                }
                addFaces(out, from, to, faces, icon, texW, texH);
            }
            for (int a = 0; a < 3; a++) {
                MODEL_CENTRE[a] = (bounds[a] + bounds[a + 3]) * 0.5F;
            }

            float[] data = new float[out.size()];
            for (int i = 0; i < data.length; i++) {
                data[i] = out.get(i);
            }
            Libertas.LOG.info(
                "Mistsplitter Reforged: parsed {} quads, bbox centre ({}, {}, {})",
                data.length / 32,
                MODEL_CENTRE[0],
                MODEL_CENTRE[1],
                MODEL_CENTRE[2]);
            return data;
        } catch (Exception e) {
            Libertas.LOG.error("Failed to load Mistsplitter Reforged model", e);
            return null;
        }
    }

    private static float[] vec3(JsonArray array) {
        return new float[] { array.get(0)
            .getAsFloat(),
            array.get(1)
                .getAsFloat(),
            array.get(2)
                .getAsFloat() };
    }

    private static void addFaces(List<Float> out, float[] from, float[] to, JsonObject faces, IIcon icon, float texW,
        float texH) {
        float x1 = from[0];
        float y1 = from[1];
        float z1 = from[2];
        float x2 = to[0];
        float y2 = to[1];
        float z2 = to[2];

        // [corners] are listed counter-clockwise as seen from outside the face.
        // [cornerIndices] assigns each vertex a corner of the face's uv rectangle:
        // 0 = (u1,v1) top-left, 1 = (u2,v1) top-right, 2 = (u2,v2) bottom-right, 3 = (u1,v2)
        // bottom-left, matching the corner order TL -> TR -> BR -> BL.
        // north (-Z)
        emitFace(
            out,
            faces,
            "north",
            icon,
            texW,
            texH,
            0.0F,
            0.0F,
            -1.0F,
            new float[][] { { x1, y2, z1 }, { x1, y1, z1 }, { x2, y1, z1 }, { x2, y2, z1 } },
            new int[] { 0, 3, 2, 1 });
        // south (+Z)
        emitFace(
            out,
            faces,
            "south",
            icon,
            texW,
            texH,
            0.0F,
            0.0F,
            1.0F,
            new float[][] { { x1, y2, z2 }, { x2, y2, z2 }, { x2, y1, z2 }, { x1, y1, z2 } },
            new int[] { 0, 1, 2, 3 });
        // east (+X)
        emitFace(
            out,
            faces,
            "east",
            icon,
            texW,
            texH,
            1.0F,
            0.0F,
            0.0F,
            new float[][] { { x2, y2, z2 }, { x2, y2, z1 }, { x2, y1, z1 }, { x2, y1, z2 } },
            new int[] { 0, 1, 2, 3 });
        // west (-X)
        emitFace(
            out,
            faces,
            "west",
            icon,
            texW,
            texH,
            -1.0F,
            0.0F,
            0.0F,
            new float[][] { { x1, y2, z1 }, { x1, y2, z2 }, { x1, y1, z2 }, { x1, y1, z1 } },
            new int[] { 0, 1, 2, 3 });
        // up (+Y)
        emitFace(
            out,
            faces,
            "up",
            icon,
            texW,
            texH,
            0.0F,
            1.0F,
            0.0F,
            new float[][] { { x1, y2, z2 }, { x1, y2, z1 }, { x2, y2, z1 }, { x2, y2, z2 } },
            new int[] { 0, 1, 2, 3 });
        // down (-Y)
        emitFace(
            out,
            faces,
            "down",
            icon,
            texW,
            texH,
            0.0F,
            -1.0F,
            0.0F,
            new float[][] { { x1, y1, z1 }, { x1, y1, z2 }, { x2, y1, z2 }, { x2, y1, z1 } },
            new int[] { 0, 1, 2, 3 });
    }

    /**
     * @param cornerIndices for each vertex (same order as {@code corners}) the index of the uv
     *                      rectangle corner it should use (0 = TL, 1 = TR, 2 = BR, 3 = BL), before the
     *                      Blockbench clockwise uv rotation is applied.
     */
    private static void emitFace(List<Float> out, JsonObject faces, String name, IIcon icon, float texW, float texH,
        float nx, float ny, float nz, float[][] corners, int[] cornerIndices) {
        if (!faces.has(name)) {
            return;
        }
        JsonObject face = faces.getAsJsonObject(name);
        JsonArray uv = face.getAsJsonArray("uv");
        // Blockbench uvs are pixels in texture_size space; the sprite covers the whole
        // texture so texW/16 is the number of pixels per sprite "pixel unit".
        float u1 = icon.getInterpolatedU(
            uv.get(0)
                .getAsFloat() / texW
                * 16.0F);
        float v1 = icon.getInterpolatedV(
            uv.get(1)
                .getAsFloat() / texH
                * 16.0F);
        float u2 = icon.getInterpolatedU(
            uv.get(2)
                .getAsFloat() / texW
                * 16.0F);
        float v2 = icon.getInterpolatedV(
            uv.get(3)
                .getAsFloat() / texH
                * 16.0F);
        if (u1 == u2 || v1 == v2) {
            // Degenerate (zero-area) uv region, skip it.
            return;
        }
        float[][] uvCorners = { { u1, v1 }, { u2, v1 }, { u2, v2 }, { u1, v2 } };

        int rotationSteps = 0;
        if (face.has("rotation")) {
            rotationSteps = ((int) (face.get("rotation")
                .getAsFloat() / 90.0F)) & 3;
        }

        for (int i = 0; i < 4; i++) {
            float[] corner = corners[i];
            float[] uvCorner = uvCorners[(cornerIndices[i] + rotationSteps) & 3];
            out.add(corner[0]);
            out.add(corner[1]);
            out.add(corner[2]);
            out.add(uvCorner[0]);
            out.add(uvCorner[1]);
            out.add(nx);
            out.add(ny);
            out.add(nz);
        }
    }
}
