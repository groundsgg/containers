package gg.grounds.sidebar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/** A Bedrock texture's resolution does not determine its geometry's UV scale. */
final class SkinCanvas {
    static int[] resolve(byte[] patch, byte[] geometry, int width, int height) {
        int[] fallback = {64, height * 2 == width ? 32 : 64};
        JsonObject selected = selected(patch, geometry);
        if (selected == null) return fallback;
        try {
            if (selected.has("description")) return dimensions(selected.getAsJsonObject("description"), "texture_width", "texture_height", fallback);
            return dimensions(selected, "texturewidth", "textureheight", fallback);
        } catch (RuntimeException ignored) { return fallback; }
    }

    static double[] rectangles(byte[] patch, byte[] geometry) {
        double[] standard = {8, 8, 8, 8, 40, 8, 8, 8};
        JsonObject selected = selected(patch, geometry);
        if (selected == null || !selected.has("bones")) return standard;
        try {
            double[] face = null, hat = null;
            for (var entry : selected.getAsJsonArray("bones")) {
                var bone = entry.getAsJsonObject();
                if (!bone.has("name") || !bone.has("cubes")) continue;
                String name = bone.get("name").getAsString().toLowerCase(java.util.Locale.ROOT);
                if (!name.equals("head") && !name.equals("hat") && !name.equals("headwear")) continue;
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

    private static JsonObject selected(byte[] patch, byte[] geometry) {
        if (geometry == null || geometry.length == 0 || geometry.length > 262_144) return null;
        try {
            String selected = "";
            if (patch != null && patch.length > 0 && patch.length <= 16_384) {
                var names = parse(patch).getAsJsonObject("geometry");
                if (names != null && names.has("default")) selected = names.get("default").getAsString();
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
        if (width < 64 || width > 512 || (width & (width - 1)) != 0
                || height < 32 || height > 512 || (height & (height - 1)) != 0) return fallback;
        return new int[]{width, height};
    }
}
