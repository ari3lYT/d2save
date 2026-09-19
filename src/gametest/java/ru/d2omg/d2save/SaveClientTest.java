package ru.d2omg.d2save;

import java.nio.file.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.nbt.*;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.util.WorldSavePath;
import ru.d2omg.d2save.testmixin.TestServerAccess;

public final class SaveClientTest implements FabricClientGameTest {
    private int checks;
    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
        D2Save.LOGGER.info("D2SAVE_QA_PASS {}", message);
    }
    private static SaveQueue queue() {
        try {
            var field = D2Save.class.getDeclaredField("queue");
            field.setAccessible(true);
            return (SaveQueue) field.get(null);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static NbtCompound read(Path file) {
        try { return NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes()); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        try (var game = context.worldBuilder().create()) {
            save = game.getWorldSave();
            var server = game.getServer();
            game.getClientWorld().waitForChunksDownload();
            context.waitTicks(40);
            server.runOnServer(s -> s.saveAll(true, true, true));
            CountDownLatch gate = new CountDownLatch(1);
            queue().submit(() -> {
                try { if (!gate.await(10, TimeUnit.SECONDS)) throw new AssertionError("background gate timed out"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            });
            // Test just the snapshot-and-enqueue section: autosave entry intentionally drains earlier batches.
            try {
                server.runOnServer(s -> {
                    var player = s.getPlayerManager().getPlayerList().getFirst();
                    player.experienceLevel = 17;
                    try {
                        var field = D2Save.class.getDeclaredField("AUTOSAVE");
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked") var flag = (ThreadLocal<Boolean>) field.get(null);
                        flag.set(true);
                        try { s.saveAll(true, false, false); }
                        finally { flag.set(false); }
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    check(gate.getCount() == 1, "save returns while disk worker is blocked");
                    player.experienceLevel = 42;
                });
            } finally { gate.countDown(); }
            D2Save.drain();
            server.runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                var file = s.getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuidAsString()+".dat");
                check(read(file).getInt("XpLevel", -1) == 17, "background snapshot detached from later player mutations");
                check(D2Save.completed() >= 2, "player and level metadata written by background queue");
                check(Files.exists(file.resolveSibling(file.getFileName()+"_old")), "previous player save retained");
                s.saveAll(true, true, true);
                check(read(file).getInt("XpLevel", -1) == 42, "manual flush persists latest state before returning");
                player.experienceLevel = 19;
                ((TestServerAccess)s).d2save$runAutosave();
                player.experienceLevel = 20;
                ((TestServerAccess)s).d2save$runAutosave();
                var loaded = ((TestServerAccess)s).d2save$saveHandler().loadPlayerData(new PlayerConfigEntry(player.getGameProfile()));
                check(loaded.orElseThrow().getInt("XpLevel", -1) == 20, "load barrier sees newest ordered autosave");
                check(read(file.resolveSibling(file.getFileName()+"_old")).getInt("XpLevel", -1) == 19,
                        "consecutive autosaves preserve correct backup ordering");
                check(Files.exists(s.getSavePath(WorldSavePath.ROOT).resolve("level.dat_old")), "level metadata backup retained");
                player.experienceLevel = 55;
                ((TestServerAccess)s).d2save$runAutosave();
            });
        }
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                check(player.experienceLevel == 55, "disconnect and shutdown preserve player state across reopen");
                var missing = s.getSavePath(WorldSavePath.ROOT).resolve("nonexistent-test-directory");
                D2Save.autosave(() -> D2Save.enqueue(missing, "test", missing.resolve("test.dat"),
                        missing.resolve("test.dat_old"), new NbtCompound()));
                D2Save.drain();
                check(queue().failed(), "disk failure disables background saving and is reported");
                player.experienceLevel = 66;
                ((TestServerAccess)s).d2save$runAutosave();
                var file = s.getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuidAsString()+".dat");
                check(read(file).getInt("XpLevel", -1) == 66, "autosave falls back to durable vanilla writes after queue failure");
            });
        }
        D2Save.LOGGER.info("D2SAVE_HEADLESS_PASS checks={}", checks);
    }
}
