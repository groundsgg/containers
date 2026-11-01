package gg.grounds.sidebar;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/** Reads only face-animation pixels from client data already verified by Geyser. */
final class AnimatedFace {
    record Frame(byte[] pixels, int width, int height, int[] canvas, double[] uv) { }

    static Optional<Frame> read(String verifiedClientJwt, byte[] patch, byte[] geometry) {
        if (verifiedClientJwt == null || verifiedClientJwt.length() > 4_000_000) return Optional.empty();
        try {
            int start = verifiedClientJwt.indexOf('.'), end = verifiedClientJwt.indexOf('.', start + 1);
            if (start < 0 || end < 0) return Optional.empty();
            byte[] payload = Base64.getUrlDecoder().decode(verifiedClientJwt.substring(start + 1, end));
            var reader = new JsonReader(new StringReader(new String(payload, StandardCharsets.UTF_8)));
            reader.setNestingLimit(32);
            reader.beginObject();
            while (reader.hasNext()) {
                if (!reader.nextName().equals("AnimatedImageData")) { reader.skipValue(); continue; }
                var images = JsonParser.parseReader(reader).getAsJsonArray();
                for (int n = 0; n < Math.min(images.size(), 16); n++) {
                    var image = images.get(n).getAsJsonObject();
                    if (!image.has("Type") || image.get("Type").getAsInt() != 1) continue;
                    int w = image.get("ImageWidth").getAsInt(), h = image.get("ImageHeight").getAsInt();
                    double frames = image.get("Frames").getAsDouble();
                    if (w < 8 || w > 512 || h < 8 || h > 8192 || (long) w * h > 1_048_576
                            || !Double.isFinite(frames) || frames < 1 || frames > 256 || frames != Math.floor(frames) || h % (int) frames != 0) continue;
                    int frameHeight = h / (int) frames;
                    if (frameHeight < 8 || frameHeight > 512) continue;
                    String encoded = image.get("Image").getAsString();
                    if (encoded.length() > 5_600_000) continue;
                    byte[] pixels = Base64.getDecoder().decode(encoded);
                    if (pixels.length != w * h * 4) continue;
                    int[] canvas = SkinCanvas.resolve(patch, geometry, w, frameHeight, "animated_face");
                    double[] uv = SkinCanvas.rectangles(patch, geometry, "animated_face");
                    return Optional.of(new Frame(Arrays.copyOf(pixels, w * frameHeight * 4), w, frameHeight, canvas, uv));
                }
                return Optional.empty();
            }
        } catch (IOException | RuntimeException ignored) {
            // Never persist/log the JWT or its unrelated claims; malformed face
            // data simply keeps the optional portrait fallback.
        }
        return Optional.empty();
    }
}
