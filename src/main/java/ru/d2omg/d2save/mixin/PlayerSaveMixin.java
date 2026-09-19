package ru.d2omg.d2save.mixin;

import java.io.File;
import java.util.Optional;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.world.PlayerSaveHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.d2omg.d2save.D2Save;

@Mixin(PlayerSaveHandler.class)
public abstract class PlayerSaveMixin {
    @Shadow @Final private File playerDataDir;

    @WrapMethod(method = "savePlayerData")
    private void d2save$save(PlayerEntity player, Operation<Void> original) {
        if (D2Save.isAutosave()) {
            try (var errors = new ErrorReporter.Logging(player.getErrorReporterContext(), D2Save.LOGGER)) {
                NbtWriteView view = NbtWriteView.create(errors, player.getRegistryManager());
                player.writeData(view);
                var directory = playerDataDir.toPath();
                String uuid = player.getUuidAsString();
                if (D2Save.enqueue(directory, uuid + "-", directory.resolve(uuid + ".dat"),
                        directory.resolve(uuid + ".dat_old"), view.getNbt())) return;
            } catch (RuntimeException error) {
                D2Save.LOGGER.error("D2Save snapshot failed; using vanilla synchronous player save", error);
            }
        }
        D2Save.drain();
        original.call(player);
    }

    @Inject(method = "loadPlayerData(Lnet/minecraft/server/PlayerConfigEntry;)Ljava/util/Optional;", at = @At("HEAD"))
    private void d2save$loadBarrier(PlayerConfigEntry player, CallbackInfoReturnable<Optional<NbtCompound>> cir) {
        D2Save.drain();
    }
}
