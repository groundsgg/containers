package gg.grounds.sidebar;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** The authenticated Bedrock client's face only; no network skin lookup. */
final class HeadPack {
    static Optional<byte[]> create(byte[] rgba, int width, int height) throws IOException {
        return create(rgba, width, height, 64, height * 2 == width ? 32 : 64);
    }

    static Optional<byte[]> create(byte[] rgba, int width, int height, int canvasWidth, int canvasHeight) throws IOException {
        return create(rgba, width, height, canvasWidth, canvasHeight, new double[]{8, 8, 8, 8, 40, 8, 8, 8});
    }

    static Optional<byte[]> create(byte[] rgba, int width, int height, int canvasWidth, int canvasHeight, double[] uv) throws IOException {
        if (rgba == null || width < 64 || width > 512 || width % 64 != 0
                || height < 32 || height > 512 || rgba.length != width * height * 4) return Optional.empty();
        if (canvasWidth < 64 || canvasWidth > 512 || (canvasWidth & (canvasWidth - 1)) != 0
                || canvasHeight < 32 || canvasHeight > 512 || (canvasHeight & (canvasHeight - 1)) != 0) return Optional.empty();
        double scaleX = (double) width / canvasWidth, scaleY = (double) height / canvasHeight;
        if (uv == null || uv.length != 8 || !valid(uv, 0, canvasWidth, canvasHeight)) return Optional.empty();
        boolean hasHat = valid(uv, 4, canvasWidth, canvasHeight);
        int samplesX = Math.max(1, Math.min(64, (int) Math.ceil(Math.max(Math.abs(uv[2]), hasHat ? Math.abs(uv[6]) : 0) * scaleX / 8)));
        int samplesY = Math.max(1, Math.min(64, (int) Math.ceil(Math.max(Math.abs(uv[3]), hasHat ? Math.abs(uv[7]) : 0) * scaleY / 8)));
        var face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        boolean visible = false;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double alpha = 0, red = 0, green = 0, blue = 0;
                for (int dy = 0; dy < samplesY; dy++) {
                    for (int dx = 0; dx < samplesX; dx++) {
                        double u = (x + (dx + .5) / samplesX) / 8, v = (y + (dy + .5) / samplesY) / 8;
                        int px = (int) Math.floor((uv[0] + u * uv[2]) * scaleX);
                        int py = (int) Math.floor((uv[1] + v * uv[3]) * scaleY);
                        int base = (py * width + px) * 4;
                        int hat = hasHat ? ((int) Math.floor((uv[5] + v * uv[7]) * scaleY) * width + (int) Math.floor((uv[4] + u * uv[6]) * scaleX)) * 4 : 0;
                        double ha = hasHat ? (rgba[hat + 3] & 255) / 255.0 : 0;
                        double ba = (rgba[base + 3] & 255) / 255.0 * (1 - ha);
                        alpha += ha + ba;
                        red += (rgba[hat] & 255) * ha + (rgba[base] & 255) * ba;
                        green += (rgba[hat + 1] & 255) * ha + (rgba[base + 1] & 255) * ba;
                        blue += (rgba[hat + 2] & 255) * ha + (rgba[base + 2] & 255) * ba;
                    }
                }
                int a = (int) Math.round(255 * alpha / (samplesX * samplesY));
                visible |= a > 0;
                int r = alpha == 0 ? 0 : (int) Math.round(red / alpha);
                int g = alpha == 0 ? 0 : (int) Math.round(green / alpha);
                int b = alpha == 0 ? 0 : (int) Math.round(blue / alpha);
                face.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        if (!visible) return Optional.empty();
        var png = new ByteArrayOutputStream();
        if (!ImageIO.write(face, "png", png)) throw new IOException("PNG encoder unavailable");
        byte[] texture = png.toByteArray();
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texture));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        UUID header = UUID.nameUUIDFromBytes(("grounds-sidebar-head:v1:" + digest).getBytes(StandardCharsets.UTF_8));
        UUID module = UUID.nameUUIDFromBytes(("grounds-sidebar-head-module:v1:" + digest).getBytes(StandardCharsets.UTF_8));
        String manifest = """
            {"format_version":2,"header":{"name":"Grounds Sidebar Head","description":"Personal sidebar portrait","uuid":"%s","version":[1,0,0],"min_engine_version":[1,21,0]},"modules":[{"type":"resources","uuid":"%s","version":[1,0,0]}]}
            """.formatted(header, module);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            entry(zip, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            entry(zip, "textures/grounds/sidebar/player_head.png", texture);
        }
        return Optional.of(bytes.toByteArray());
    }

    private static boolean valid(double[] uv, int i, int width, int height) {
        for (int n = i; n < i + 4; n++) if (!Double.isFinite(uv[n])) return false;
        return uv[i + 2] != 0 && uv[i + 3] != 0
            && Math.min(uv[i], uv[i] + uv[i + 2]) >= 0 && Math.max(uv[i], uv[i] + uv[i + 2]) <= width
            && Math.min(uv[i + 1], uv[i + 1] + uv[i + 3]) >= 0 && Math.max(uv[i + 1], uv[i + 1] + uv[i + 3]) <= height;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        var entry = new ZipEntry(name);
        entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }
}
