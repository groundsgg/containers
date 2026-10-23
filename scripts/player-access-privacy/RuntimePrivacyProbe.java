package gg.grounds.build.privacy;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import sun.misc.Unsafe;

/** Exercises the real pinned runtime method; Unsafe only replaces unrelated live network setup. */
public final class RuntimePrivacyProbe {
    private static final String CODE = "0123456789ABCDEF";
    private static final String REASON = "Access restricted (M-000001). Review or appeal: https://platform.grounds.gg/account/measures\n"
        + "Bedrock player access: https://platform.grounds.gg/player#code=" + CODE + "\nOne-time code: " + CODE;
    private static final Unsafe UNSAFE = unsafe();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("runtime mode required");
        if (args[1].equals("verify")) {
            verifyCoreCapability(Class.forName(args[0].equals("velocity")
                ? "com.velocitypowered.proxy.connection.client.ConnectedPlayer"
                : "org.geysermc.geyser.platform.standalone.GeyserStandaloneLogger"));
            System.out.println(args[0] + " native capability verified in core artifact");
            return;
        }
        boolean expectPrivate = args[1].equals("private");
        if (args[0].equals("velocity")) velocity(expectPrivate);
        else if (args[0].equals("geyser")) geyser(expectPrivate);
        else throw new IllegalArgumentException("unknown runtime");
        System.out.println(args[0] + " actual-runtime privacy probe passed (" + args[1] + ")");
    }

    private static void velocity(boolean expectPrivate) throws Exception {
        VelocityConfiguration config = (VelocityConfiguration) UNSAFE.allocateInstance(VelocityConfiguration.class);
        Class<?> advancedClass = Class.forName("com.velocitypowered.proxy.config.VelocityConfiguration$Advanced");
        Object advanced = UNSAFE.allocateInstance(advancedClass);
        set(advanced, "logPlayerConnections", true);
        set(config, "advanced", advanced);
        VelocityServer server = (VelocityServer) UNSAFE.allocateInstance(VelocityServer.class);
        set(server, "configuration", config);
        CapturingConnection connection = (CapturingConnection) UNSAFE.allocateInstance(CapturingConnection.class);
        ConnectedPlayer player = (ConnectedPlayer) UNSAFE.allocateInstance(ConnectedPlayer.class);
        set(player, "server", server);
        set(player, "connection", connection);
        set(player, "profile", new GameProfile(UUID.randomUUID(), "Fixture", List.of()));
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);

        for (boolean accept : List.of(false, true)) {
            List<String> captured = configure(logging, accept);
            Component reason = Component.text(REASON);
            player.disconnect0(reason, true);
            awaitLog(captured);
            check(!captured.isEmpty(), "safe admission log missing");
            check(captured.stream().anyMatch(s -> s.contains("M-000001")), "safe measure metadata missing");
            check(captured.stream().noneMatch(s -> s.contains(CODE)) == expectPrivate, "credential log privacy mismatch");
            DisconnectPacket packet = (DisconnectPacket) connection.packet;
            check(PlainTextComponentSerializer.plainText().serialize(packet.getReason().getComponent()).equals(REASON), "network reason changed");

            // Already prepared denial is delivered after replacing the entire logger configuration.
            List<String> afterReplacement = configure(logging, accept);
            player.disconnect0(reason, true);
            awaitLog(afterReplacement);
            check(afterReplacement.stream().noneMatch(s -> s.contains(CODE)) == expectPrivate, "reconfiguration exposed credential");
            check(PlainTextComponentSerializer.plainText().serialize(((DisconnectPacket) connection.packet).getReason().getComponent()).equals(REASON), "replacement changed packet");

            afterReplacement.clear();
            player.disconnect0(Component.text("Ordinary fixture disconnect"), true);
            awaitLog(afterReplacement);
            check(afterReplacement.stream().anyMatch(s -> s.contains("Ordinary fixture disconnect")), "ordinary logging changed");
        }
    }

    private static void geyser(boolean expectPrivate) throws Exception {
        Class<?> loggerClass = Class.forName("org.geysermc.geyser.platform.standalone.GeyserStandaloneLogger");
        Object logger = loggerClass.getConstructor().newInstance();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        for (boolean accept : List.of(false, true)) {
            List<String> captured = configure(context, accept);
            loggerClass.getMethod("info", String.class).invoke(logger, REASON);
            awaitLog(captured);
            check(!captured.isEmpty(), "Geyser log missing");
            check(captured.stream().anyMatch(s -> s.contains("M-000001")), "Geyser safe metadata missing");
            check(captured.stream().noneMatch(s -> s.contains(CODE)) == expectPrivate, "Geyser credential log privacy mismatch");
            List<String> afterReplacement = configure(context, accept);
            loggerClass.getMethod("info", String.class).invoke(logger, REASON);
            awaitLog(afterReplacement);
            check(afterReplacement.stream().noneMatch(s -> s.contains(CODE)) == expectPrivate, "Geyser reconfiguration exposed credential");
            afterReplacement.clear();
            loggerClass.getMethod("info", String.class).invoke(logger, "Ordinary fixture disconnect");
            awaitLog(afterReplacement);
            check(afterReplacement.stream().anyMatch(s -> s.contains("Ordinary fixture disconnect")), "ordinary Geyser logging changed");
        }
    }

    private static List<String> configure(LoggerContext context, boolean acceptingFilter) {
        DefaultConfiguration config = new DefaultConfiguration();
        for (String name : new ArrayList<>(config.getRootLogger().getAppenders().keySet())) config.getRootLogger().removeAppender(name);
        config.getRootLogger().setLevel(Level.INFO);
        List<String> captured = new CopyOnWriteArrayList<>();
        AbstractAppender capture = new AbstractAppender("capture", null, null, false, new org.apache.logging.log4j.core.config.Property[0]) {
            @Override public void append(LogEvent event) { captured.add(event.getMessage().getFormattedMessage()); }
        };
        capture.start();
        config.addAppender(capture);
        config.getRootLogger().addAppender(capture, Level.ALL, null);
        if (acceptingFilter) config.getRootLogger().addFilter(new AbstractFilter() {
            @Override public Filter.Result filter(LogEvent event) { return Filter.Result.ACCEPT; }
        });
        context.setConfiguration(config);
        return captured;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static Unsafe unsafe() {
        try { Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return (Unsafe) field.get(null); }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    private static void check(boolean assertion, String message) { if (!assertion) throw new AssertionError(message); }
    private static void verifyCoreCapability(Class<?> runtime) throws Exception {
        check(runtime.getDeclaredMethod("groundsPlayerAccessLogPrivacyVersion").invoke(null).equals(1), "core version marker missing");
        Class<?> helper = Class.forName("gg.grounds.runtime.logging.PlayerAccessLogPrivacy", false, runtime.getClassLoader());
        check(helper.getClassLoader() == runtime.getClassLoader(), "helper comes from another loader");
        check(helper.getProtectionDomain().getCodeSource().getLocation().equals(runtime.getProtectionDomain().getCodeSource().getLocation()), "helper comes from another runtime artifact");
    }
    private static void awaitLog(List<String> messages) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (messages.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
    }

    public static final class CapturingConnection extends MinecraftConnection {
        Object packet;
        private CapturingConnection() { super(null, null); }
        @Override public void closeWith(Object value) { packet = value; }
        @Override public ProtocolVersion getProtocolVersion() { return ProtocolVersion.MINECRAFT_1_21; }
        @Override public StateRegistry getState() { return StateRegistry.LOGIN; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 25565); }
    }
}
