package me.cookie.kwrg.render.utils;

import com.mojang.blaze3d.audio.DeviceList;
import com.mojang.blaze3d.audio.Library;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.duck.KwrgAudioLibrary;
import me.cookie.kwrg.mixin.accessor.SoundEngineAccessor;
import me.cookie.kwrg.mixin.accessor.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.util.Util;
import org.lwjgl.openal.ALC10;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class LoadingAudio {
    private static CompletableFuture<PreparedDevice> pending;

    private LoadingAudio() {
    }

    public static void preload(Minecraft client) {
        if (pending != null || !LoadingAtlasUpload.isLoadingWithoutWorld(client) || client.isGameLoadFinished()) {
            return;
        }

        SoundEngineAccessor engine = engine(client.getSoundManager());
        if (engine.kwrg$isLoaded()) {
            return;
        }

        String preferred = client.options.soundDevice().get();
        DeviceRequest request = new DeviceRequest(Options.isSoundDeviceDefault(preferred) ? null : preferred,
                engine.kwrg$getDeviceTracker().currentDevices(), client.options.directionalAudio().get());
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                return ((KwrgAudioLibrary) new Library()).kwrg$prepare(request);
            } catch (RuntimeException exception) {
                CookieMain.LOGGER.warn("OpenAL device preparation failed. Retrying during sound reload.", exception);
                return null;
            }
        }, Util.ioPool());
    }

    public static CompletableFuture<Void> stage(SoundManager manager, Runnable apply, Executor renderExecutor) {
        Minecraft client = Minecraft.getInstance();
        if (!LoadingAtlasUpload.isLoadingWithoutWorld(client) || client.isGameLoadFinished() || manager != client.getSoundManager()) {
            apply.run();
            return CompletableFuture.completedFuture(null);
        }

        preload(client);
        var preparation = pending;
        if (preparation == null) {
            apply.run();
            return CompletableFuture.completedFuture(null);
        }
        SoundEngineAccessor engine = engine(manager);
        return preparation.handleAsync((prepared, failure) -> {
            if (pending != preparation) {
                return null;
            }

            pending = null;
            if (failure != null) {
                throw new CompletionException(failure);
            }

            var library = (KwrgAudioLibrary) engine.kwrg$getLibrary();
            try (prepared) {
                if (!engine.kwrg$isLoaded()) {
                    library.kwrg$setPrepared(prepared);
                }
                apply.run();
            } finally {
                library.kwrg$setPrepared(null);
            }
            return null;
        }, renderExecutor);
    }

    private static SoundEngineAccessor engine(SoundManager manager) {
        return (SoundEngineAccessor) ((SoundManagerAccessor) manager).kwrg$getSoundEngine();
    }

    public static void close() {
        var preparation = pending;
        pending = null;
        if (preparation != null) {
            preparation.thenAccept(prepared -> {
                if (prepared != null) {
                    prepared.close();
                }
            });
        }
    }

    public record DeviceRequest(String preferred, DeviceList devices, boolean directional) {
    }

    public static final class PreparedDevice implements AutoCloseable {
        private final DeviceRequest request;
        private long device;
        private long context;

        public PreparedDevice(DeviceRequest request, long device, long context) {
            this.request = request;
            this.device = device;
            this.context = context;
        }

        public boolean matches(String preferred, DeviceList devices, boolean directional) {
            return device != 0 && Objects.equals(preferred, request.preferred())
                    && Objects.equals(devices.defaultDevice(), request.devices().defaultDevice())
                    && directional == request.directional();
        }

        public long device() {
            return device;
        }

        public long context() {
            return context;
        }

        public void transferOwnership() {
            device = 0;
            context = 0;
        }

        @Override
        public void close() {
            closeDetached(device, context);
            transferOwnership();
        }
    }

    public static void closeDetached(long device, long context) {
        if (context != 0) {
            ALC10.alcDestroyContext(context);
        }
        if (device != 0) {
            ALC10.alcCloseDevice(device);
        }
    }
}
