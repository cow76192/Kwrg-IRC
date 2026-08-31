package me.cookie.kwrg.mixin;

import me.cookie.kwrg.gui.notification.NotificationManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HudNotificationMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kwrg$onExtractHudNotification(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        float delta = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(true) : 0.016f;

        int mouseX = -1;
        int mouseY = -1;
        if (this.minecraft.screen != null) {
            var window = this.minecraft.getWindow();
            mouseX = (int) (this.minecraft.mouseHandler.xpos() * ((double) window.getGuiScaledWidth() / (double) window.getScreenWidth()));
            mouseY = (int) (this.minecraft.mouseHandler.ypos() * ((double) window.getGuiScaledHeight() / (double) window.getScreenHeight()));
        }

        NotificationManager.render(graphics, mouseX, mouseY, delta);
    }
}