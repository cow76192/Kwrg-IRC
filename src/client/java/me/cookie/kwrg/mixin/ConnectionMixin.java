package me.cookie.kwrg.mixin;

import me.cookie.kwrg.gui.util.ServerLatencyTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void kwrg$onReceivePacket(io.netty.channel.ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }

        if (packet instanceof ClientboundPlayerInfoUpdatePacket updatePacket) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (updatePacket.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY)) {
                for (ClientboundPlayerInfoUpdatePacket.Entry entry : updatePacket.entries()) {
                    if (mc.player.getUUID().equals(entry.profileId())) {
                        ServerLatencyTracker.onLatencySample(entry.latency());
                        break;
                    }
                }
            }
        }
    }

    @Inject(method = "handleDisconnection", at = @At("HEAD"), require = 0)
    private void kwrg$onDisconnect(CallbackInfo ci) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            ServerLatencyTracker.reset();
        }
    }
}