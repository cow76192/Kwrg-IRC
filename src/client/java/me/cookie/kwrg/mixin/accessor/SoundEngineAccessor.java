package me.cookie.kwrg.mixin.accessor;

import com.mojang.blaze3d.audio.DeviceTracker;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("library")
    Library kwrg$getLibrary();

    @Accessor("deviceTracker")
    DeviceTracker kwrg$getDeviceTracker();

    @Accessor("loaded")
    boolean kwrg$isLoaded();
}
