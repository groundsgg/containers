package gg.grounds.sidebar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/** A Bedrock texture's resolution does not determine its geometry's UV scale. */
final class SkinCanvas {
    static int[] resolve(byte[] patch, byte[] geometry, int width, int height) {
        return resolve(patch, geometry, width, height, "default");
    }

    static int[] resolve(byte[] patch, byte[] geometry, int width, int height, String slot) {
        int[] fallback = {64, height * 2 == width ? 32 : 64};
        JsonObject selected = selected(patch, geometry, slot);
        if (selected == null) return fallback;
        try {
            if (selected.has("description")) return dimensions(selected.getAsJsonObject("description"), "texture_width", "texture_height", fallback);
            return dimensions(selected, "texturewidth", "textureheight", fallback);
        } catch (RuntimeException ignored) { return fallback; }
    }

    static double[] rectangles(byte[] patch, byte[] geometry) {
        return rectangles(patch, geometry, "default");
    }

    static double[] rectangles(byte[] patch, byte[] geometry, String slot) {
        double[] standard = slot.equals("default") ? new double[]{8, 8, 8, 8, 40, 8, 8, 8} : new double[8];
        JsonObject selected = selected(patch, geometry, slot);
        if (selected == null || !selected.has("bones")) return standard;
        try {
            double[] face = null, hat = null;
            for (var entry : selected.getAsJsonArray("bones")) {
                var bone = entry.getAsJsonObject();
                if (!bone.has("name")) continue;
                String name = bone.get("name").getAsString().toLowerCase(java.util.Locale.ROOT);
                if (!name.equals("head") && !name.equals("hat") && !name.equals("headwear")) continue;
                if (bone.has("poly_mesh")) {
                    var desc = selected.getAsJsonObject("description");
                    var rect = meshFront(bone.getAsJsonObject("poly_mesh"), desc.get("texture_width").getAsDouble(), desc.get("texture_height").getAsDouble());
                    if (rect != null) { if (name.equals("head")) face = rect; else hat = rect; }
                }
                if (!bone.has("cubes")) continue;
                for (var cube : bone.getAsJsonArray("cubes")) {
                    double[] rect = front(cube.getAsJsonObject());
                    if (rect == null) continue;
                    boolean overlay = !name.equals("head") || (cube.getAsJsonObject().has("inflate") && cube.getAsJsonObject().get("inflate").getAsDouble() > 0);
                    if (overlay) {
                        if (hat == null || Math.abs(rect[2] * rect[3]) > Math.abs(hat[2] * hat[3])) hat = rect;
                    } else if (face == null || Math.abs(rect[2] * rect[3]) > Math.abs(face[2] * face[3])) face = rect;
                }
            }
            if (face == null) return standard;
            if (hat == null) hat = new double[]{-1, -1, 0, 0};
            return new double[]{face[0], face[1], face[2], face[3], hat[0], hat[1], hat[2], hat[3]};
        } catch (RuntimeException ignored) { return standard; }
    }

    private static double[] meshFront(JsonObject mesh, double width, double height) {
        if (!mesh.has("polys") || !mesh.get("polys").isJsonArray()) return null;
        var positions = mesh.getAsJsonArray("positions"); var normals = mesh.getAsJsonArray("normals"); var uvs = mesh.getAsJsonArray("uvs");
        boolean normalized = mesh.has("normalized_uvs") && mesh.get("normalized_uvs").getAsBoolean();
        double[] best = null;
        for (var poly : mesh.getAsJsonArray("polys")) {
            var vertices = poly.getAsJsonArray();
            if (vertices.size() < 3) continue;
            var normal = normals.get(vertices.get(0).getAsJsonArray().get(1).getAsInt()).getAsJsonArray();
            if (normal.get(2).getAsDouble() > -.5) continue;
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (var vertex : vertices) {
                var p = positions.get(vertex.getAsJsonArray().get(0).getAsInt()).getAsJsonArray();
                minX = Math.min(minX, p.get(0).getAsDouble()); maxX = Math.max(maxX, p.get(0).getAsDouble());
                minY = Math.min(minY, p.get(1).getAsDouble()); maxY = Math.max(maxY, p.get(1).getAsDouble());
            }
            double tlDistance = Double.POSITIVE_INFINITY, brDistance = Double.POSITIVE_INFINITY;
            double u0 = 0, v0 = 0, u1 = 0, v1 = 0;
            for (var vertex : vertices) {
                var indices = vertex.getAsJsonArray(); var p = positions.get(indices.get(0).getAsInt()).getAsJsonArray(); var uv = uvs.get(indices.get(2).getAsInt()).getAsJsonArray();
                double x = p.get(0).getAsDouble(), y = p.get(1).getAsDouble();
                double u = uv.get(0).getAsDouble(), v = uv.get(1).getAsDouble();
                if (normalized) { u *= width; v = (1 - v) * height; }
                double td = (x - minX) * (x - minX) + (y - maxY) * (y - maxY);
                double bd = (x - maxX) * (x - maxX) + (y - minY) * (y - minY);
                if (td < tlDistance) { tlDistance = td; u0 = u; v0 = v; }
                if (bd < brDistance) { brDistance = bd; u1 = u; v1 = v; }
            }
            double[] rect = {u0, v0, u1 - u0, v1 - v0};
            if (rect[2] != 0 && rect[3] != 0 && (best == null || Math.abs(rect[2] * rect[3]) > Math.abs(best[2] * best[3]))) best = rect;
        }
        return best;
    }

    private static double[] front(JsonObject cube) {
        if (!cube.has("uv") || !cube.has("size")) return null;
        var size = cube.getAsJsonArray("size");
        double x = size.get(0).getAsDouble(), y = size.get(1).getAsDouble(), z = size.get(2).getAsDouble();
        if (x <= 0 || y <= 0 || z <= 0) return null;
        var uv = cube.get("uv");
        if (uv.isJsonArray()) return new double[]{uv.getAsJsonArray().get(0).getAsDouble() + z, uv.getAsJsonArray().get(1).getAsDouble() + z, x, y};
        if (!uv.isJsonObject() || !uv.getAsJsonObject().has("north")) return null;
        var north = uv.getAsJsonObject().getAsJsonObject("north");
        var origin = north.getAsJsonArray("uv");
        var extent = north.has("uv_size") ? north.getAsJsonArray("uv_size") : null;
        return new double[]{origin.get(0).getAsDouble(), origin.get(1).getAsDouble(), extent == null ? x : extent.get(0).getAsDouble(), extent == null ? y : extent.get(1).getAsDouble()};
    }

    private static JsonObject selected(byte[] patch, byte[] geometry, String slot) {
        if (geometry == null || geometry.length == 0 || geometry.length > 262_144) return null;
        try {
            String selected = "";
            if (patch != null && patch.length > 0 && patch.length <= 16_384) {
                var names = parse(patch).getAsJsonObject("geometry");
                if (names != null && names.has(slot)) selected = names.get(slot).getAsString();
                else if (!slot.equals("default")) return null;
            }
            JsonObject root = parse(geometry);
            if (root.has("minecraft:geometry")) {
                var definitions = root.getAsJsonArray("minecraft:geometry");
                for (var definition : definitions) {
                    var description = definition.getAsJsonObject().getAsJsonObject("description");
                    if (description == null) continue;
                    if (!selected.isEmpty() && (!description.has("identifier") || !selected.equals(description.get("identifier").getAsString()))) continue;
                    return definition.getAsJsonObject();
                }
            } else if (root.has(selected) && root.get(selected).isJsonObject()) {
                return root.getAsJsonObject(selected);
            }
        } catch (RuntimeException ignored) {
            // Optional malformed/custom geometry must not interrupt login.
        }
        return null;
    }

    private static JsonObject parse(byte[] bytes) {
        var reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        reader.setNestingLimit(32);
        return JsonParser.parseReader(reader).getAsJsonObject();
    }

    private static int[] dimensions(JsonObject description, String x, String y, int[] fallback) {
        if (!description.has(x) || !description.has(y)) return fallback;
        int width = description.get(x).getAsInt(), height = description.get(y).getAsInt();
        if (width < 16 || width > 512 || (width & (width - 1)) != 0
                || height < 16 || height > 512 || (height & (height - 1)) != 0) return fallback;
        return new int[]{width, height};
    }
}
