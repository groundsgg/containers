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
        if (rgba == null || width < 64 || width > 512 || width % 64 != 0
                || height < 32 || height > 512 || rgba.length != width * height * 4) return Optional.empty();
        int scale = width / 64;
        if (height < 16 * scale) return Optional.empty();
        var face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        boolean visible = false;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double alpha = 0, red = 0, green = 0, blue = 0;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        int py = (8 + y) * scale + dy;
                        int base = (py * width + (8 + x) * scale + dx) * 4;
                        int hat = (py * width + (40 + x) * scale + dx) * 4;
                        double ha = (rgba[hat + 3] & 255) / 255.0;
                        double ba = (rgba[base + 3] & 255) / 255.0 * (1 - ha);
                        alpha += ha + ba;
                        red += (rgba[hat] & 255) * ha + (rgba[base] & 255) * ba;
                        green += (rgba[hat + 1] & 255) * ha + (rgba[base + 1] & 255) * ba;
                        blue += (rgba[hat + 2] & 255) * ha + (rgba[base + 2] & 255) * ba;
                    }
                }
                int a = (int) Math.round(255 * alpha / (scale * scale));
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

    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        var entry = new ZipEntry(name);
        entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }
}
