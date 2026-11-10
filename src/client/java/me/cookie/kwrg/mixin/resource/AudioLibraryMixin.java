package me.cookie.kwrg.mixin.resource;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.DeviceList;
import com.mojang.blaze3d.audio.Library;
import me.cookie.kwrg.duck.KwrgAudioLibrary;
import me.cookie.kwrg.render.utils.LoadingAudio;
import org.lwjgl.openal.ALC10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Mixin(Library.class)
public abstract class AudioLibraryMixin implements KwrgAudioLibrary {
    @Shadow private long currentDevice;
    @Shadow private long context;

    @Unique private boolean kwrg$preparing;
    @Unique private LoadingAudio.PreparedDevice kwrg$prepared;
    @Unique private LoadingAudio.PreparedDevice kwrg$reuse;

    @Override
    public LoadingAudio.PreparedDevice kwrg$prepare(LoadingAudio.DeviceRequest request) {
        kwrg$preparing = true;

        try {
            ((Library) (Object) this).init(request.preferred(), request.devices(), request.directional());
            if (currentDevice == 0 || context == 0) {
                throw new IllegalStateException("OpenAL did not create an audio context");
            }

            var result = new LoadingAudio.PreparedDevice(request, currentDevice, context);
            currentDevice = 0;
            context = 0;
            return result;
        } finally {
            LoadingAudio.closeDetached(currentDevice, context);
            kwrg$preparing = false;
        }
    }

    @Override
    public void kwrg$setPrepared(LoadingAudio.PreparedDevice prepared) {
        kwrg$prepared = prepared;
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcMakeContextCurrent(J)Z"),
            cancellable = true)
    private void kwrg$leaveContextUnbound(String preferred, DeviceList devices, boolean directional, CallbackInfo ci) {
        if (kwrg$preparing) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "init")
    private void kwrg$adoptPreparedDevice(String preferred, DeviceList devices, boolean directional, Operation<Void> original) {
        var prepared = kwrg$prepared;
        if (prepared == null || !prepared.matches(preferred, devices, directional)) {
            original.call(preferred, devices, directional);
            return;
        }

        kwrg$reuse = prepared;
        try {
            original.call(preferred, devices, directional);
            prepared.transferOwnership();
        } catch (RuntimeException | Error failure) {
            if (ALC10.alcGetCurrentContext() == prepared.context()) {
                ALC10.alcMakeContextCurrent(0);
            }
            currentDevice = 0;
            context = 0;
            throw failure;
        } finally {
            kwrg$reuse = null;
        }
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;openDeviceOrFallback(Ljava/lang/String;Ljava/lang/String;)J"))
    private long kwrg$reuseDevice(String preferred, String fallback, Operation<Long> original) {
        return kwrg$reuse == null ? original.call(preferred, fallback) : kwrg$reuse.device();
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"))
    private long kwrg$reuseContext(long device, IntBuffer attributes, Operation<Long> original) {
        return kwrg$reuse == null ? original.call(device, attributes) : kwrg$reuse.context();
    }
}
