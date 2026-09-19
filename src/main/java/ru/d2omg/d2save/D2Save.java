package ru.d2omg.d2save;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.*;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class D2Save implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("d2save");
    private static final ThreadLocal<Boolean> AUTOSAVE = ThreadLocal.withInitial(() -> false);
    private static volatile SaveQueue queue;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            queue = new SaveQueue(128, error -> LOGGER.error(
                    "D2Save background write failed; reverting subsequent saves to synchronous writes", error));
            LOGGER.info("D2Save enabled: ordered autosave writes, synchronous manual/exit saves, disk sync preserved");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SaveQueue current = queue;
            if (current != null) current.close();
            queue = null;
        });
    }

    public static boolean isAutosave() { return AUTOSAVE.get(); }
    public static void autosave(Runnable operation) {
        drain();
        long started = System.nanoTime();
        boolean previous = AUTOSAVE.get();
        AUTOSAVE.set(true);
        try { operation.run(); }
        finally { AUTOSAVE.set(previous); }
        LOGGER.info("D2Save autosave snapshot prepared in {} ms", (System.nanoTime() - started) / 1_000_000L);
        SaveQueue current = queue;
        if (current != null) current.submit(() -> {
            if (!current.failed()) LOGGER.info("D2Save autosave disk writes completed in {} ms",
                    (System.nanoTime() - started) / 1_000_000L);
        });
    }

    public static void drain() {
        SaveQueue current = queue;
        if (current != null) current.drain();
    }

    public static long completed() { return queue == null ? 0 : queue.completed(); }

    public static boolean enqueue(Path directory, String prefix, Path target, Path backup, NbtCompound data) {
        SaveQueue current = queue;
        if (!isAutosave() || current == null || current.failed()) return false;
        NbtCompound snapshot = data.copy();
        return current.submit(() -> {
            try {
                Path temporary = Files.createTempFile(directory, prefix, ".dat");
                // Vanilla NbtIo retains StandardOpenOption.SYNC, gzip format, and close semantics.
                NbtIo.writeCompressed(snapshot, temporary);
                Util.backupAndReplace(target, temporary, backup);
                if (Files.exists(temporary)) throw new IOException("Could not replace " + target
                        + "; unsaved snapshot retained at " + temporary);
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }
}
