package me.cookie.kwrg.mixin;

import me.cookie.kwrg.gui.TokenLoginScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
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