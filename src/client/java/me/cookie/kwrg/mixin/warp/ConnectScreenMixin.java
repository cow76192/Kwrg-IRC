package me.cookie.kwrg.mixin.warp;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.gui.title.WarpCancelButton;
import me.cookie.kwrg.gui.title.ServerRetry;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
    @Shadow @Final private Screen parent;
    @Unique private volatile Component kwrg$connectionStatus;
    @Unique private volatile boolean kwrg$sawSecurity;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void kwrg$rememberEndpoint(Screen parent, Minecraft game, ServerAddress address, ServerData server,
                                              boolean quickPlay, TransferState transfer, CallbackInfo ci) {
        if (!(game.screen instanceof ConnectScreen)) ServerRetry.remember(parent, address, server, transfer != null);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void kwrg$startWarp(CallbackInfo ci) {
        ServerPortalRenderer.connect(this, parent);
    }

    @Inject(method = "updateStatus", at = @At("TAIL"))
    private void kwrg$observeConnection(Component status, CallbackInfo ci) {
        if (ServerPortalRenderer.isSecurityStatus(status)) kwrg$sawSecurity = true;
        kwrg$connectionStatus = status;
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/ConnectScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener kwrg$replaceCancel(GuiEventListener widget) {
        if (!ServerPortalRenderer.connecting(this) || !(widget instanceof Button vanilla)) return widget;
        return new WarpCancelButton(width, height, button -> vanilla.onPress(null));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        if (!ServerPortalRenderer.connecting(this)) {
            super.extractBackground(graphics, mouseX, mouseY, tick);
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void kwrg$drawStatus(GuiGraphicsExtractor graphics, Font font, Component component, int x, int y, int color,
                                 Operation<Void> original) {
        if (!ServerPortalRenderer.connecting(this)) {
            original.call(graphics, font, component, x, y, color);
            return;
        }
        Component current = kwrg$connectionStatus;
        if (current == null) current = component;
        ServerPortalRenderer.connectionFrame(current, kwrg$sawSecurity);
        WarpCancelButton.status(graphics, ServerPortalRenderer.status(current), x, height);
    }
}
