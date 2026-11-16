package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.gui.TokenLoginScreen;
import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @WrapOperation(method = "join", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/ConnectScreen;startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V"))
    private void kwrg$launchFromBeacon(Screen parent, Minecraft game, ServerAddress address, ServerData server,
                                       boolean quickPlay, TransferState transfer, Operation<Void> original) {
        TitleBrowser browser = TitleBrowser.owner(parent);
        Runnable connect = () -> original.call(parent, game, address, server, quickPlay, transfer);
        if (browser == null || !browser.launch(parent, server, connect)) connect.run();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void kwrg$addTokenLoginButton(CallbackInfo ci) {

        int x = 10;
        int y = 6;

        Button tokenBtn = Button.builder(
                Component.literal("Token 登录"),
                button -> this.minecraft.setScreen(new TokenLoginScreen(this))
        ).bounds(x, y, 80, 20).build();

        this.addRenderableWidget(tokenBtn);
    }
}
