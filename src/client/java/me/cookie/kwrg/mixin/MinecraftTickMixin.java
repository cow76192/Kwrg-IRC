package me.cookie.kwrg.mixin;

import me.cookie.kwrg.cheat.DanceRoomManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftTickMixin {

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void onClientTick(CallbackInfo ci) {
        DanceRoomManager.onClientTick();
    }
}