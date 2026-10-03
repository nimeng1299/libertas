package neuvillette.libertas.client.render;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/// Parses a Blockbench/Minecraft-style model JSON (elements + faces + 0..16 uv space) into pre-baked quads in
/// 0..1 block space. Shared by {@link JsonItemRenderer} (item form) and {@link JsonBlockRenderer} (in-world).
///
/// - Elements are baked to quads in the 0..1 block-space cube (from/to divided by 16).
/// - Element `rotation` uses the correct T(origin)·R·T(-origin) transform.
/// - Face UVs map into the referenced texture (normalized by 16); textures are standalone files under
/// `assets/<domain>/textures/` (any size, e.g. 64x64).
@SideOnly(Side.CLIENT)
public final class JsonBakedModel {

    public final List<Quad> quads = new ArrayList<>();

    private JsonBakedModel() {}

    public static JsonBakedModel fromJson(InputStream is) throws Exception {
        final JsonObject root = new JsonParser().parse(new InputStreamReader(is))
            .getAsJsonObject();
        final JsonBakedModel model = new JsonBakedModel();
        model.bake(root);
        return model;
    }

    /// Resolves a model texture reference to the actual png ResourceLocation, e.g. "domain:path" ->
    /// "domain:textures/path.png" (paths without a namespace use the fallback domain).
    public static ResourceLocation textureLocation(String texPath, String fallbackDomain) {
        final int colon = texPath.indexOf(':');
        return colon >= 0
            ? new ResourceLocation(texPath.substring(0, colon), "textures/" + texPath.substring(colon + 1) + ".png")
            : new ResourceLocation(fallbackDomain, "textures/" + texPath + ".png");
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

                quads.add(new Quad(faceName, from, to, rot, uv, texPath));
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

    /// A single pre-baked quad: 4 vertices + uvs + texture + face name.
    public static final class Quad {

        public final String face;
        public final float[] x = new float[4];
        public final float[] y = new float[4];
        public final float[] z = new float[4];
        public final float[] u = new float[4];
        public final float[] v = new float[4];
        public final String texture;

        Quad(String face, Vector3f from, Vector3f to, Matrix4f rot, Vector4f uv, String texture) {
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
                case "up": // +Y - (from.x, from.z) -> (from.x, to.z) -> (to.x, to.z) -> (to.x, from.z)
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
