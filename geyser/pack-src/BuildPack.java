import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Builds the Bedrock edition of the existing nameplate icon without replacing controller glyphs. */
class BuildPack {
    public static void main(String[] args) throws Exception {
        var texture = ImageIO.read(Path.of(args[0]).toFile());
        if (texture == null || texture.getWidth() != 16 || texture.getHeight() != 16) {
            throw new IllegalArgumentException("Expected the pinned 16x16 Bedrock texture");
        }
        var atlas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var graphics = atlas.createGraphics();
        try {
            // U+E500: first cell of Unicode page E5. E0 contains native controller icons.
            graphics.drawImage(texture, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        var image = new ByteArrayOutputStream();
        if (!ImageIO.write(atlas, "png", image)) throw new IllegalStateException("PNG writer unavailable");
        String manifest = """
            {"format_version":2,"header":{"name":"Grounds Nameplates","description":"Rank badges and Bedrock icon for Grounds player names","uuid":"32f559d7-e76a-470e-ac36-8b458b99c84c","version":[0,2,2],"min_engine_version":[1,21,0]},"modules":[{"type":"resources","uuid":"fc7bf96d-c4c7-4212-8b90-211b1d2fa1f0","version":[0,2,2]}]}
            """;
        try (var zip = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            add(zip, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            add(zip, "font/glyph_E5.png", image.toByteArray());
            var source = Path.of(args[0]).getParent();
            add(zip, "font/glyph_E6.png", badges(source));
            add(zip, "grounds/rank-badges.properties", Files.readAllBytes(source.resolve("rank-badges.properties")));
            add(zip, "pack_icon.png", Files.readAllBytes(Path.of(args[0])));
        }
    }

    private static byte[] badges(Path source) throws Exception {
        var catalog = new Properties();
        try (var input = Files.newInputStream(source.resolve("rank-badges.properties"))) { catalog.load(input); }
        var letters = new Properties();
        try (var input = Files.newInputStream(source.resolve("rank-letters.properties"))) { letters.load(input); }
        var left = ImageIO.read(source.resolve("badge_left.png").toFile());
        var middle = ImageIO.read(source.resolve("badge_middle.png").toFile());
        var right = ImageIO.read(source.resolve("badge_right.png").toFile());
        var atlas = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        var used = new java.util.HashSet<Integer>();
        for (var key : catalog.stringPropertyNames()) {
            int separator = key.lastIndexOf('.');
            String label = key.substring(0, separator);
            int colour = Integer.parseInt(key.substring(separator + 2), 16);
            int codepoint = Integer.parseInt(catalog.getProperty(key), 16);
            if (codepoint < 0xE600 || codepoint > 0xE6FF || !used.add(codepoint))
                throw new IllegalArgumentException("Duplicate/out-of-page badge codepoint");
            int textWidth = 0;
            for (char c : label.toCharArray()) {
                String pattern = letters.getProperty(Character.toString(c));
                if (pattern == null) throw new IllegalArgumentException("Missing compact glyph: " + c);
                textWidth += pattern.split(",")[0].length() + 1;
            }
            int width = Math.max(textWidth + 4, 7);
            if (width > 64) throw new IllegalArgumentException("Badge exceeds Bedrock cell");
            int x0 = (codepoint & 15) * 64, y0 = ((codepoint & 255) / 16) * 64;
            for (int x = 0; x < width; x++) {
                var slice = x < 3 ? left : x >= width - 3 ? right : middle;
                int sx = x < 3 ? x : x >= width - 3 ? x - width + 3 : 0;
                for (int y = 0; y < 8; y++) {
                    int pixel = slice.getRGB(sx, y);
                    // Match the Java bitmap tint, including the source border shading.
                    int red = ((pixel >>> 16) & 255) * ((colour >>> 16) & 255) / 255;
                    int green = ((pixel >>> 8) & 255) * ((colour >>> 8) & 255) / 255;
                    int blue = (pixel & 255) * (colour & 255) / 255;
                    atlas.setRGB(x0 + x, y0 + y, (pixel & 0xff000000) | red << 16 | green << 8 | blue);
                }
            }
            int cursor = (width - textWidth) / 2;
            for (char c : label.toCharArray()) {
                String[] rows = letters.getProperty(Character.toString(c)).split(",");
                for (int y = 0; y < rows.length; y++)
                    for (int x = 0; x < rows[y].length(); x++)
                        if (rows[y].charAt(x) == '1') atlas.setRGB(x0 + cursor + x, y0 + 1 + y, 0xffffffff);
                cursor += rows[0].length() + 1;
            }
        }
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(atlas, "png", bytes)) throw new IllegalStateException("PNG writer unavailable");
        return bytes.toByteArray();
    }

    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        var entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
