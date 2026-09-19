# D2Save

D2Save is a server-side Fabric mod that moves vanilla player-data and level-metadata disk writes off the Minecraft tick thread during autosaves.

It was built after profiling multi-second autosave stalls on storage with high `fsync` latency. Clients do not need the mod, and it does not change chunks, autosave frequency, gameplay, commands, permissions, recipes, or the on-disk NBT format.

## Compatibility

- Minecraft `1.21.11`
- Fabric Loader `0.18.4+`
- Fabric API
- Java 21

## How it works

During `MinecraftServer.runAutosave`, D2Save serializes player and level metadata on the server thread, makes a detached NBT copy, and sends the resulting disk work to one bounded FIFO writer.

Safety barriers drain pending writes before:

- a new autosave batch;
- manual saves and `/save-all flush`;
- player-data loads and disconnect saves;
- level metadata reads, edits, backups, and restores;
- server shutdown.

Vanilla gzip NBT, synchronous file writes, temporary-file replacement, and `.dat_old` backups are retained. If the background writer fails, the error is logged and later writes fall back to vanilla synchronous saving until restart. The bounded queue applies backpressure instead of consuming unbounded memory.

## Trade-off

There is a short window between snapshot creation and completion of its queued disk write. A hard process or OS crash during that window can lose the latest pending autosave. Normal shutdown and explicit save operations drain the queue first. D2Save is a latency tool, not a backup system.

## Build and test

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test build
```

The JUnit suite checks FIFO ordering, barriers, backpressure, failure handling, shutdown, and interrupt preservation.

The repository also includes an isolated Fabric client game test:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null \
xvfb-run -a -s '-screen 0 1280x720x24 -nolisten tcp' \
./gradlew runClientGameTest
```

It verifies detached snapshots, non-blocking autosaves, ordered backups, manual-save barriers, shutdown/reopen durability, injected disk failure, and synchronous fallback.

The distributable JAR is written to `build/libs/`.

## Installation

1. Back up the world.
2. Install Fabric Loader and Fabric API on the server.
3. Put the D2Save JAR in `mods/`.
4. Restart the server normally.

Uninstalling D2Save requires no data conversion because saves remain vanilla NBT.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
