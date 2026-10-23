import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Restores the buildserver's working copy from the map registry before Paper opens a world.
 *
 * <p>Runs from the start wrapper, so a failure here (non-zero exit) keeps Paper from starting with
 * empty worlds. The marker {@value #MARKER} is written last, and only after every file was verified
 * and moved into place: GroundsMaps refuses to sync without it, which is what keeps a pod that skipped
 * or failed the restore from pushing empty worlds over a good snapshot.
 *
 * <p>Design: service-maps docs/superpowers/specs/2026-10-23-buildserver-working-copy-design.md.
 */
public final class WorkspaceRestore {

    private static final String MARKER = ".workspace-head";
    private static final String STAGING = ".workspace-restore";
    private static final Pattern SERVER = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");
    private static final Pattern PATH = Pattern.compile("[A-Za-z0-9._/-]{1,512}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final int MAX_FILES = 100_000;
    private static final int PARALLEL_DOWNLOADS = 8;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public static void main(String[] args) throws Exception {
        String server = env("GROUNDS_WORKSPACE_SERVER", "");
        if (server.isEmpty()) {
            log("GROUNDS_WORKSPACE_SERVER is not set; no restore, and GroundsMaps will not sync");
            return;
        }
        if (!SERVER.matcher(server).matches()) {
            throw new IllegalArgumentException("GROUNDS_WORKSPACE_SERVER is not a server id: " + server);
        }
        String secret = env("GROUNDS_MAPS_CLIENT_SECRET", "");
        if (secret.isEmpty()) {
            throw new IllegalStateException("GROUNDS_WORKSPACE_SERVER is set but GROUNDS_MAPS_CLIENT_SECRET is not");
        }
        Path root = Path.of(env("GROUNDS_WORKSPACE_ROOT", "/app")).toAbsolutePath().normalize();
        Path marker = root.resolve(MARKER);
        Path staging = root.resolve(STAGING);

        // A marker from an earlier boot must not outlive a restore that fails now.
        Files.deleteIfExists(marker);
        deleteTree(staging);

        String token = token(
                env("GROUNDS_MAPS_TOKEN_URL", "https://account.grounds.gg/realms/grounds/protocol/openid-connect/token"),
                env("GROUNDS_MAPS_CLIENT_ID", "buildserver-maps"),
                secret);
        String registry = stripTrailingSlash(env("GROUNDS_MAPS_REGISTRY_URL", "https://api.grounds.gg"));

        HttpResponse<String> head = HTTP.send(
                HttpRequest.newBuilder(URI.create(registry + "/v1/workspaces/" + server + "/head"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (head.statusCode() == 404) {
            writeMarker(marker, "none");
            log("no working copy for " + server + " yet; starting fresh");
            return;
        }
        if (head.statusCode() != 200) {
            throw new IOException("registry answered " + head.statusCode() + " for the head of " + server);
        }

        Map<?, ?> body = (Map<?, ?>) Json.parse(head.body());
        long snapshot = ((Number) body.get("snapshot")).longValue();
        List<Entry> entries = entries(root, (List<?>) body.get("files"));

        Files.createDirectories(staging);
        download(entries, staging);
        for (Entry entry : entries) {
            moveIntoPlace(staging.resolve(entry.path()), root.resolve(entry.path()));
        }
        deleteTree(staging);
        writeMarker(marker, Long.toString(snapshot));
        log("restored snapshot " + snapshot + " of " + server + " (" + entries.size() + " files)");
    }

    private record Entry(String path, String sha256, long size, String url) {}

    /** Every entry validated up front, so nothing is downloaded for a manifest that cannot be restored. */
    private static List<Entry> entries(Path root, List<?> files) {
        if (files == null) {
            throw new IllegalArgumentException("head has no files");
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("head lists " + files.size() + " files, more than " + MAX_FILES);
        }
        List<Entry> entries = new ArrayList<>(files.size());
        var seen = new HashSet<String>();
        for (Object raw : files) {
            Map<?, ?> file = (Map<?, ?>) raw;
            String path = (String) file.get("path");
            String sha256 = (String) file.get("sha256");
            long size = ((Number) file.get("size")).longValue();
            String url = (String) file.get("url");
            requireSafePath(root, path);
            if (!seen.add(path)) {
                throw new IllegalArgumentException("duplicate path in head: " + path);
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("not a sha256 for " + path + ": " + sha256);
            }
            if (size < 0) {
                throw new IllegalArgumentException("negative size for " + path);
            }
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                throw new IllegalArgumentException("no download url for " + path);
            }
            entries.add(new Entry(path, sha256, size, url));
        }
        return entries;
    }

    /**
     * The contract's path rules, plus: nothing that lands on the restore's own bookkeeping. The lexical
     * check is deliberate — {@code /app/world} may be a symlink onto a volume, and following it is fine.
     */
    private static void requireSafePath(Path root, String path) {
        if (path == null || !PATH.matcher(path).matches() || path.startsWith("/")) {
            throw new IllegalArgumentException("not a workspace path: " + path);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("not a workspace path: " + path);
            }
        }
        String first = path.split("/", 2)[0];
        if (first.startsWith(MARKER) || first.equals(STAGING)) {
            throw new IllegalArgumentException("path collides with the restore's own files: " + path);
        }
        if (!root.resolve(path).normalize().startsWith(root)) {
            throw new IllegalArgumentException("path escapes the workspace root: " + path);
        }
    }

    private static void download(List<Entry> entries, Path staging) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_DOWNLOADS)) {
            List<Future<?>> futures = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                futures.add(pool.submit(() -> {
                    downloadOne(entry, staging.resolve(entry.path()));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
    }

    /** Presigned URLs carry their own authorization, so the bearer token is never sent to the bucket. */
    private static void downloadOne(Entry entry, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        HttpResponse<InputStream> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(entry.url()))
                        .timeout(Duration.ofMinutes(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("download of " + entry.path() + " answered " + response.statusCode());
        }
        MessageDigest digest = sha256();
        long written = 0;
        try (InputStream in = response.body();
                var out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) {
                written += n;
                if (written > entry.size()) {
                    throw new IOException(entry.path() + " is larger than the " + entry.size() + " bytes announced");
                }
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        if (written != entry.size()) {
            throw new IOException(entry.path() + " has " + written + " bytes, expected " + entry.size());
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(entry.sha256())) {
            throw new IOException(entry.path() + " has sha256 " + actual + ", expected " + entry.sha256());
        }
    }

    /**
     * A rename where possible. Durable paths may be symlinks onto another filesystem, where a rename
     * cannot cross, so the fallback copies beside the target and renames there — the target is still
     * never seen half-written.
     */
    private static void moveIntoPlace(Path staged, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException crossDevice) {
            Path temp = target.resolveSibling(target.getFileName() + ".restore-tmp");
            Files.copy(staged, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.delete(staged);
        }
    }

    private static void writeMarker(Path marker, String content) throws IOException {
        Path temp = marker.resolveSibling(MARKER + ".tmp");
        Files.writeString(temp, content + "\n");
        Files.move(temp, marker, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String token(String tokenUrl, String clientId, String secret)
            throws IOException, InterruptedException {
        String form = "grant_type=client_credentials&client_id=" + encode(clientId) + "&client_secret=" + encode(secret);
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(tokenUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("token endpoint answered " + response.statusCode());
        }
        Object token = ((Map<?, ?>) Json.parse(response.body())).get("access_token");
        if (!(token instanceof String value) || value.isEmpty()) {
            throw new IOException("token endpoint returned no access_token");
        }
        return value;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void log(String message) {
        System.out.println("workspace-restore: " + message);
    }

    /** Just enough JSON for two responses; the JRE has no parser and the image should not grow a library for this. */
    private static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            Object value = json.value();
            json.skipWhitespace();
            if (json.i != text.length()) {
                throw json.error("trailing data");
            }
            return value;
        }

        private Object value() {
            skipWhitespace();
            if (i >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                map.put(key, value());
                skipWhitespace();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return map;
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++;
            skipWhitespace();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = s.charAt(i++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("bad escape");
                }
            }
        }

        private Number number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String text = s.substring(start, i);
            if (text.isEmpty()) {
                throw error("unexpected character");
            }
            return text.contains(".") || text.contains("e") || text.contains("E")
                    ? Double.parseDouble(text)
                    : Long.parseLong(text);
        }

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) {
                throw error("unexpected literal");
            }
            i += word.length();
            return value;
        }

        private char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        private void expect(char c) {
            if (peek() != c) {
                throw error("expected '" + c + "'");
            }
            i++;
        }

        private void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException("invalid JSON at " + i + ": " + what);
        }
    }
}
