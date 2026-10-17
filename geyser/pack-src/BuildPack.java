import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            {"format_version":2,"header":{"name":"Grounds Nameplates","description":"Bedrock edition icon for Grounds player nameplates","uuid":"32f559d7-e76a-470e-ac36-8b458b99c84c","version":[0,1,0],"min_engine_version":[1,21,0]},"modules":[{"type":"resources","uuid":"fc7bf96d-c4c7-4212-8b90-211b1d2fa1f0","version":[0,1,0]}]}
            """;
        try (var zip = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            add(zip, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            add(zip, "font/glyph_E5.png", image.toByteArray());
            add(zip, "pack_icon.png", Files.readAllBytes(Path.of(args[0])));
        }
    }

    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        var entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
