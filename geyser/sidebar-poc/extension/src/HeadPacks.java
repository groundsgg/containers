package gg.grounds.sidebar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Each connection owns its file until disconnect, even when pack UUIDs match. */
final class HeadPacks implements AutoCloseable {
    private final Path directory;
    private final Map<Object, Path> files = new IdentityHashMap<>();

    HeadPacks(Path directory) throws IOException {
        this.directory = Files.createDirectories(directory);
    }

    synchronized boolean offer(Object connection, byte[] rgba, int width, int height,
                               Consumer<Path> register) throws IOException {
        var pack = HeadPack.create(rgba, width, height);
        if (pack.isEmpty()) return false;
        Path file = Files.createTempFile(directory, "portrait-", ".mcpack");
        boolean retained = false;
        try {
            Files.write(file, pack.get());
            register.accept(file);
            Path previous = files.put(connection, file);
            retained = true;
            if (previous != null) Files.deleteIfExists(previous);
            return true;
        } finally {
            if (!retained) Files.deleteIfExists(file);
        }
    }

    synchronized void remove(Object connection) throws IOException {
        Path file = files.remove(connection);
        if (file != null) Files.deleteIfExists(file);
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        for (Path file : files.values()) {
            try { Files.deleteIfExists(file); }
            catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        files.clear();
        if (failure != null) throw failure;
    }
}
