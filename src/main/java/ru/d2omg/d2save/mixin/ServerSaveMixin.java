package ru.d2omg.d2save.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.d2omg.d2save.D2Save;

@Mixin(MinecraftServer.class)
public abstract class ServerSaveMixin {
    @WrapMethod(method = "runAutosave")
    private void d2save$autosave(Operation<Void> original) {
        D2Save.autosave(() -> original.call());
    }
    @Inject(method = {"save", "saveAll"}, at = @At("HEAD"))
    private void d2save$manualSaveBarrier(boolean suppressLogs, boolean flush, boolean force,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!D2Save.isAutosave()) D2Save.drain();
    }
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void d2save$stopBarrier(CallbackInfo ci) { D2Save.drain(); }
}
