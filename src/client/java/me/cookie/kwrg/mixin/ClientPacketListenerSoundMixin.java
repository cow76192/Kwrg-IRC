package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.cheatmanager.DanceRoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerSoundMixin {

    @Inject(method = "handleSoundEvent", at = @At("RETURN"))
    private void onHandleSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        String soundPath = packet.getSound().value().location().getPath();
        DanceRoomManager.onSoundReceived(soundPath, packet.getPitch(), packet.getVolume());
    }
}