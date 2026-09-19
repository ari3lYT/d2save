package ru.d2omg.d2save.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.level.storage.LevelStorage;
import com.mojang.serialization.Dynamic;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.d2omg.d2save.D2Save;

@Mixin(LevelStorage.Session.class)
public abstract class LevelSaveMixin {
    @Shadow @Final private LevelStorage.LevelSave directory;

    @WrapMethod(method = "save(Lnet/minecraft/nbt/NbtCompound;)V")
    private void d2save$save(NbtCompound data, Operation<Void> original) {
        if (D2Save.enqueue(directory.path(), "level", directory.getLevelDatPath(),
                directory.getLevelDatOldPath(), data)) return;
        D2Save.drain();
        original.call(data);
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void d2save$closeBarrier(CallbackInfo ci) { D2Save.drain(); }
    @Inject(method = "createBackup", at = @At("HEAD"))
    private void d2save$backupBarrier(CallbackInfoReturnable<Long> cir) { D2Save.drain(); }
    @Inject(method = "readLevelProperties(Z)Lcom/mojang/serialization/Dynamic;", at = @At("HEAD"))
    private void d2save$readBarrier(boolean old, CallbackInfoReturnable<Dynamic<?>> cir) { D2Save.drain(); }
    @Inject(method = "save(Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void d2save$editBarrier(Consumer<NbtCompound> edit, CallbackInfo ci) { D2Save.drain(); }
    @Inject(method = "deleteSessionLock", at = @At("HEAD"))
    private void d2save$deleteBarrier(CallbackInfo ci) { D2Save.drain(); }
    @Inject(method = "tryRestoreBackup", at = @At("HEAD"))
    private void d2save$restoreBarrier(CallbackInfoReturnable<Boolean> cir) { D2Save.drain(); }
}
