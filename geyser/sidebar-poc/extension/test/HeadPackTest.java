package gg.grounds.sidebar;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

public final class HeadPackTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] skin(int scale) {
        byte[] data = new byte[64 * scale * 64 * scale * 4];
        for (int y = 8 * scale; y < 16 * scale; y++)
            for (int x = 8 * scale; x < 16 * scale; x++) pixel(data, 64 * scale, x, y, 40, 80, 120, 255);
        return data;
    }

    private static void pixel(byte[] data, int width, int x, int y, int r, int g, int b, int a) {
        int i = (y * width + x) * 4;
        data[i] = (byte) r; data[i + 1] = (byte) g; data[i + 2] = (byte) b; data[i + 3] = (byte) a;
    }

    private static HashMap<String, byte[]> entries(byte[] zip) throws Exception {
        var result = new HashMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) result.put(e.getName(), in.readAllBytes());
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        byte[] original = skin(1);
        byte[] before = original.clone();
        byte[] pack = HeadPack.create(original, 64, 64).orElseThrow();
        check(Arrays.equals(original, before), "must not mutate the authenticated skin");
        var files = entries(pack);
        check(files.keySet().equals(java.util.Set.of("manifest.json", "textures/grounds/sidebar/player_head.png")), "pack must contain only the face and manifest");
        var face = ImageIO.read(new ByteArrayInputStream(files.get("textures/grounds/sidebar/player_head.png")));
        check(face.getWidth() == 8 && face.getHeight() == 8, "face dimensions");
        check(face.getRGB(0, 0) == 0xff285078, "RGBA face crop");
        check(!new String(files.get("manifest.json"), StandardCharsets.UTF_8).contains("lusu"), "no identity in pack");
        check(Arrays.equals(pack, HeadPack.create(original, 64, 64).orElseThrow()), "same face must produce the same pack and UUID");

        // Overlay the hat front. A translucent pixel must composite, not replace.
        pixel(original, 64, 40, 8, 200, 0, 0, 128);
        var changed = entries(HeadPack.create(original, 64, 64).orElseThrow());
        var hat = ImageIO.read(new ByteArrayInputStream(changed.get("textures/grounds/sidebar/player_head.png")));
        check(hat.getRGB(0, 0) == 0xff78283c, "hat alpha compositing");
        check(hat.getRGB(1, 0) == 0xff285078, "transparent hat leaves face intact");
        check(!Arrays.equals(files.get("manifest.json"), changed.get("manifest.json")), "skin changes must invalidate the cached pack UUID");

        byte[] hires = skin(2);
        pixel(hires, 128, 16, 16, 200, 80, 120, 255);
        var hi = ImageIO.read(new ByteArrayInputStream(entries(HeadPack.create(hires, 128, 128).orElseThrow()).get("textures/grounds/sidebar/player_head.png")));
        check(hi.getRGB(0, 0) == 0xff505078, "high-resolution face must average its pixels");
        // 128px Bedrock textures can use a 128px UV canvas. They are not
        // necessarily a 2x Java skin; scaling its UVs by two crops the torso.
        byte[] bedrock = new byte[128 * 128 * 4];
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) pixel(bedrock, 128, x, y, 10, 100, 200, 255);
        byte[] patch = "{\"geometry\":{\"default\":\"geometry.selected\"}}".getBytes(StandardCharsets.UTF_8);
        byte[] geometry = "{\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.other\",\"texture_width\":64,\"texture_height\":64}},{\"description\":{\"identifier\":\"geometry.selected\",\"texture_width\":128,\"texture_height\":128}}]}".getBytes(StandardCharsets.UTF_8);
        int[] canvas = SkinCanvas.resolve(patch, geometry, 128, 128);
        check(canvas[0] == 128 && canvas[1] == 128, "respect selected geometry's UV canvas");
        var unscaled = ImageIO.read(new ByteArrayInputStream(entries(HeadPack.create(bedrock, 128, 128, canvas[0], canvas[1]).orElseThrow()).get("textures/grounds/sidebar/player_head.png")));
        check(unscaled.getRGB(0, 0) == 0xff0a64c8, "128px UV geometry must sample face, not torso");
        geometry = "{\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.selected\",\"texture_width\":128,\"texture_height\":128},\"bones\":[{\"name\":\"head\",\"cubes\":[{\"size\":[8,8,8],\"uv\":[16,32]}]}]}]}".getBytes(StandardCharsets.UTF_8);
        double[] uv = SkinCanvas.rectangles(patch, geometry);
        check(uv[0] == 24 && uv[1] == 40 && uv[2] == 8 && uv[3] == 8, "box UV front coordinates from selected head cube");
        byte[] custom = new byte[128 * 128 * 4];
        for (int y = 40; y < 48; y++) for (int x = 24; x < 32; x++) pixel(custom, 128, x, y, 20, 30, 40, 255);
        var mapped = ImageIO.read(new ByteArrayInputStream(entries(HeadPack.create(custom, 128, 128, 128, 128, uv).orElseThrow()).get("textures/grounds/sidebar/player_head.png")));
        check(mapped.getRGB(0, 0) == 0xff141e28, "custom head UV must not use Java's fixed face offset");
        geometry = "{\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.selected\",\"texture_width\":128,\"texture_height\":128},\"bones\":[{\"name\":\"head\",\"cubes\":[{\"size\":[1,1,1],\"uv\":[0,0]},{\"size\":[8,8,8],\"uv\":{\"north\":{\"uv\":[32,40],\"uv_size\":[-8,8]}}}]}]}]}".getBytes(StandardCharsets.UTF_8);
        uv = SkinCanvas.rectangles(patch, geometry);
        check(uv[0] == 32 && uv[2] == -8, "select main head face and preserve mirrored per-face UVs");
        pixel(custom, 128, 24, 40, 255, 0, 0, 255);
        var mirrored = ImageIO.read(new ByteArrayInputStream(entries(HeadPack.create(custom, 128, 128, 128, 128, uv).orElseThrow()).get("textures/grounds/sidebar/player_head.png")));
        check(mirrored.getRGB(7, 0) == 0xffff0000 && mirrored.getRGB(0, 0) == 0xff141e28, "mirrored front samples in the declared direction");
        canvas = SkinCanvas.resolve(patch, new byte[]{123}, 64, 32);
        check(canvas[0] == 64 && canvas[1] == 32, "legacy skin fallback");
        canvas = SkinCanvas.resolve(patch, new byte[262_145], 64, 64);
        check(canvas[0] == 64 && canvas[1] == 64, "oversized geometry must fall back before parsing");
        canvas = SkinCanvas.resolve(patch, ("{\"deep\":" + "[".repeat(100) + "0" + "]".repeat(100) + "}").getBytes(StandardCharsets.UTF_8), 64, 64);
        check(canvas[0] == 64 && canvas[1] == 64, "deeply nested geometry must fall back");
        check(HeadPack.create(null, 64, 64).isEmpty(), "missing skin fallback");
        check(HeadPack.create(new byte[4], 64, 64).isEmpty(), "truncated skin fallback");
        check(HeadPack.create(before, Integer.MAX_VALUE, 64).isEmpty(), "oversized dimensions bounded before multiplication");
        check(HeadPack.create(before, 63, 64).isEmpty(), "unsupported geometry fallback");
        check(HeadPack.create(new byte[64 * 64 * 4], 64, 64).isEmpty(), "empty portrait fallback");

        // Persona's visible face is a separate animation atlas, not SkinData.
        byte[] personaPatch = "{\"geometry\":{\"default\":\"geometry.poc_persona\",\"animated_face\":\"geometry.poc_face\"}}".getBytes(StandardCharsets.UTF_8);
        byte[] personaGeometry;
        try (var resource = HeadPackTest.class.getResourceAsStream("/persona-geometry.json")) { personaGeometry = resource.readAllBytes(); }
        byte[] animation = new byte[32 * 128 * 4];
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) pixel(animation, 32, x, y, 20, 80, 150, 255);
        pixel(animation, 32, 8, 24, 200, 0, 0, 128);
        for (int y = 64; y < 128; y++) for (int x = 0; x < 32; x++) pixel(animation, 32, x, y, 0, 255, 0, 255);
        String payload = "{\"UnrelatedClaim\":\"do-not-store\",\"AnimatedImageData\":[{\"Type\":1,\"ImageWidth\":32,\"ImageHeight\":128,\"Frames\":2,\"Image\":\"" + Base64.getEncoder().encodeToString(animation) + "\"}]}";
        String jwt = "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".already-verified";
        var animated = AnimatedFace.read(jwt, personaPatch, personaGeometry).orElseThrow();
        check(animated.width() == 32 && animated.height() == 64, "use the first face frame only");
        check(animated.canvas()[0] == 32 && animated.canvas()[1] == 64, "animated-face geometry instead of default body geometry");
        check(Arrays.equals(animated.uv(), new double[]{8, 8, 8, 8, 8, 24, 8, 8}), "normalized front mesh UVs and hat");
        var persona = entries(HeadPack.create(animated.pixels(), animated.width(), animated.height(), animated.canvas()[0], animated.canvas()[1], animated.uv()).orElseThrow());
        var personaFace = ImageIO.read(new ByteArrayInputStream(persona.get("textures/grounds/sidebar/player_head.png")));
        check(personaFace.getRGB(1, 0) == 0xff145096, "persona eyes/face come from animation, not body atlas");
        check(personaFace.getRGB(0, 0) == 0xff6e284b, "persona hat is composited over face");
        check(!new String(persona.get("manifest.json"), StandardCharsets.UTF_8).contains("do-not-store"), "login claims never enter the face pack");
        check(AnimatedFace.read("invalid", personaPatch, personaGeometry).isEmpty(), "invalid optional face data fallback");

        // Two clients, including identical skins, must retain independent files.
        Path directory = Files.createTempDirectory("sidebar-head-test-");
        try {
            var sessions = new HeadPacks(directory);
            Object a = new Object(), b = new Object();
            var pa = new AtomicReference<Path>(); var pb = new AtomicReference<Path>();
            check(sessions.offer(a, before, 64, 64, pa::set), "first session registration");
            check(sessions.offer(b, before, 64, 64, pb::set), "second session registration");
            check(!pa.get().equals(pb.get()), "never share a temporary file between clients");
            sessions.remove(a);
            check(!Files.exists(pa.get()) && Files.exists(pb.get()), "disconnect must remove only its own file");
            check(Arrays.equals(Files.readAllBytes(pb.get()), pack), "second client pack survives first disconnect");
            try {
                sessions.offer(a, before, 64, 64, path -> { throw new IllegalStateException("registration failed"); });
                throw new AssertionError("registration failure not propagated");
            } catch (IllegalStateException expected) { }
            try (var paths = Files.list(directory)) { check(paths.count() == 1, "failed registration must not leak a file"); }
            sessions.close();
            check(!Files.exists(pb.get()), "shutdown cleanup");
        } finally {
            try (var paths = Files.list(directory)) { for (Path path : paths.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(directory);
        }
        System.out.println("Personal head: crop, hat, hires, cache identity, invalid input and session isolation checks passed.");
    }
}
