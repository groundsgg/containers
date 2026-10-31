package gg.grounds.runtime.transfer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.client.InitialLoginSessionHandler;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.*;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** Early cookie gate only. The original online authentication still runs after admission. */
public final class TransferAdmission {
    private static final boolean ENABLED = "true".equals(System.getenv("GROUNDS_TRANSFER_ADMISSION_ENABLED"));
    private static final Key COOKIE = Key.key("grounds:player-session-handoff");
    // Bounds all waiting sockets, HTTP checks, AND admitted authentications, not just active HTTP.
    private static final Semaphore BUDGET = new Semaphore(32);
    private static final Semaphore ABORT_BUDGET = new Semaphore(32);
    private static final ConcurrentHashMap<MinecraftConnection, Pending> PENDING = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Component FAILED = Component.text("This transfer could not be verified. Please reconnect.");

    private static final class Pending {
        final String connectionId = UUID.randomUUID().toString();
        final String proxyId = System.getenv("PROXY_ID");
        ServerLoginPacket login;
        InitialLoginSessionHandler handler;
        UUID playerId;
        String token;
        boolean checking;
        boolean admitted;
    }

    private TransferAdmission() {}

    public static boolean attempt(Ratelimiter<Object> limiter, Object address,
                                  MinecraftConnection connection, HandshakePacket handshake) {
        if (!ENABLED || handshake.getIntent() != HandshakeIntent.TRANSFER) return limiter.attempt(address);
        if (!BUDGET.tryAcquire()) return false;
        Pending pending = new Pending();
        if (PENDING.putIfAbsent(connection, pending) != null) { BUDGET.release(); return false; }
        connection.getChannel().closeFuture().addListener(f -> { if (remove(connection, pending)) abort(pending); });
        connection.eventLoop().schedule(() -> {
            if (PENDING.get(connection) == pending && !pending.admitted) reject(connection, pending);
        }, 3, TimeUnit.SECONDS);
        // Even admitted sockets cannot keep the finite admission budget forever during auth.
        connection.eventLoop().schedule(() -> {
            if (PENDING.get(connection) == pending) reject(connection, pending);
        }, 15, TimeUnit.SECONDS);
        return true;
    }

    /** Called before ANY original LoginStart state change, plugin event or encryption request. */
    public static boolean begin(MinecraftConnection connection, InitialLoginSessionHandler handler,
                                ServerLoginPacket login) {
        Pending pending = PENDING.get(connection);
        if (pending == null) return false;
        if (pending.admitted) return false; // replay by our verified continuation
        if (pending.login != null) { reject(connection, pending); return true; }
        pending.login = login;
        pending.handler = handler;
        connection.write(new ClientboundCookieRequestPacket(COOKIE));
        return true;
    }

    public static boolean cookie(MinecraftConnection connection, ServerboundCookieResponsePacket packet) {
        Pending pending = PENDING.get(connection);
        if (pending == null) { connection.close(); return true; }
        if (pending.checking || pending.admitted || pending.login == null || !COOKIE.equals(packet.getKey())) {
            reject(connection, pending); return true;
        }
        byte[] payload = packet.getPayload();
        if (payload == null || payload.length > 4096) { reject(connection, pending); return true; }
        try {
            JsonObject cookie = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            pending.playerId = UUID.fromString(cookie.get("playerId").getAsString());
            pending.token = cookie.get("token").getAsString();
            if (!pending.token.matches("[A-Za-z0-9_-]{43}") || !pending.playerId.equals(pending.login.getHolderUuid())) {
                reject(connection, pending); return true;
            }
            pending.checking = true;
            HttpRequest request = request(pending, "");
            HTTP.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(), 4096))
                .orTimeout(2500, TimeUnit.MILLISECONDS).whenComplete((response, error) -> connection.eventLoop().execute(() -> {
                    if (connection.isClosed() || PENDING.get(connection) != pending) { abort(pending); return; }
                    try {
                        if (error != null || response.statusCode() != 201) throw new IllegalStateException();
                        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (!pending.playerId.toString().equals(result.get("playerId").getAsString())
                            || !pending.connectionId.equals(result.get("connectionId").getAsString())
                            || !pending.proxyId.equals(result.get("destinationProxyId").getAsString())
                            || !Instant.parse(result.get("expiresAt").getAsString()).isAfter(Instant.now())) throw new IllegalStateException();
                        pending.admitted = true;
                        handlerReplay(connection, pending);
                    } catch (Exception failure) { reject(connection, pending); }
                }));
        } catch (Exception failure) { reject(connection, pending); }
        return true;
    }

    private static HttpRequest request(Pending pending, String suffix) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("playerId", pending.playerId.toString());
        body.addProperty("handoffToken", pending.token);
        body.addProperty("connectionId", pending.connectionId);
        body.addProperty("destinationProxyId", pending.proxyId);
        String service = System.getenv("PLAYER_SERVICE_URL");
        if (service == null || pending.proxyId == null || pending.proxyId.isBlank()) throw new IllegalStateException();
        String path = System.getenv().getOrDefault("GROUNDS_TOKEN_FILE", "/var/run/secrets/grounds/token");
        // The rotating Workload token is read on each request; never log it or the cookie.
        String bearer = Files.readString(Path.of(path)).trim();
        if (bearer.isEmpty() || bearer.length() > 16384) throw new IllegalStateException();
        return HttpRequest.newBuilder(URI.create(service.replaceAll("/$", "") + "/v2/players/sessions/transfer/admission" + suffix))
            .timeout(Duration.ofSeconds(2)).header("Authorization", "Bearer " + bearer)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    private static void abort(Pending pending) {
        if (pending.playerId == null || pending.token == null || !pending.checking || !ABORT_BUDGET.tryAcquire()) return;
        try {
            HTTP.sendAsync(request(pending, "/abort"), HttpResponse.BodyHandlers.discarding())
                .orTimeout(2500, TimeUnit.MILLISECONDS).whenComplete((response, error) -> ABORT_BUDGET.release());
        } catch (Exception failure) { ABORT_BUDGET.release(); }
        // Failure leaves the immutable reservation closed to replacement connections until
        // its original short TTL. No exception authorizes a login or extends that TTL.
    }

    private static void handlerReplay(MinecraftConnection connection, Pending pending) {
        if (connection.getActiveSessionHandler() != pending.handler) { reject(connection, pending); return; }
        pending.handler.handle(pending.login);
    }

    /** Narrow bridge for plugin-player: final authenticated UUID must match this admission. */
    public static Map<String, String> admission(Player player) {
        if (!(player instanceof ConnectedPlayer connected)) return Map.of();
        Pending pending = PENDING.get(connected.getConnection());
        if (pending == null || !pending.admitted || !player.getUniqueId().equals(pending.playerId)) return Map.of();
        return Map.of("playerId", pending.playerId.toString(), "handoffToken", pending.token,
            "connectionId", pending.connectionId, "destinationProxyId", pending.proxyId);
    }

    public static void complete(Player player) {
        if (player instanceof ConnectedPlayer connected) {
            Pending pending = PENDING.get(connected.getConnection());
            if (pending != null) remove(connected.getConnection(), pending);
        }
    }

    private static void reject(MinecraftConnection connection, Pending pending) {
        if (!remove(connection, pending)) return;
        abort(pending);
        if (connection.isClosed()) return;
        if (connection.getState() == StateRegistry.LOGIN) {
            connection.closeWith(DisconnectPacket.create(FAILED, connection.getProtocolVersion(), StateRegistry.LOGIN));
        } else connection.close();
    }

    private static boolean remove(MinecraftConnection connection, Pending pending) {
        if (!PENDING.remove(connection, pending)) return false;
        BUDGET.release();
        return true;
    }
}
