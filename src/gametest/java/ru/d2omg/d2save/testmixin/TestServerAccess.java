package ru.d2omg.d2save.testmixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PlayerSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

@Mixin(MinecraftServer.class)
public interface TestServerAccess {
    @Invoker("runAutosave") void d2save$runAutosave();
    @Accessor("saveHandler") PlayerSaveHandler d2save$saveHandler();
}
