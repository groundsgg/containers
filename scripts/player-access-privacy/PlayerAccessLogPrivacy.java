package gg.grounds.runtime.logging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Included in the core runtime, before any logger/filter/appender can inspect the secret. */
public final class PlayerAccessLogPrivacy {
    private static final String MARKER = "Bedrock player access:";
    private static final String OMITTED = "[player portal credentials omitted]";

    private PlayerAccessLogPrivacy() {}

    public static Component forLog(Component message) {
        try {
            String plain = PlainTextComponentSerializer.plainText().serialize(message);
            int marker = plain.indexOf(MARKER);
            if (marker < 0) return message;
            return Component.text(safePrefix(plain, marker));
        } catch (RuntimeException failure) {
            return Component.text(OMITTED);
        }
    }

    public static String forLog(String message) {
        if (message == null) return OMITTED;
        int marker = message.indexOf(MARKER);
        return marker < 0 ? message : safePrefix(message, marker);
    }

    private static String safePrefix(String message, int marker) {
        // The controlled admission message puts all proof material after MARKER.
        return (marker <= 1024 ? message.substring(0, marker).stripTrailing() + " " : "") + OMITTED;
    }
}
