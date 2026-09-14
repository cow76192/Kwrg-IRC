package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @Unique private static int kwrg$minX;
    @Unique private static int kwrg$minY;
    @Unique private static int kwrg$maxX;
    @Unique private static int kwrg$maxY;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void kwrg$onTabRenderHead(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective displayObjective, CallbackInfo ci) {

        if (!IrcConfig.get().dynamicIsland) {
            return;
        }
        kwrg$minX = Integer.MAX_VALUE;
        kwrg$minY = Integer.MAX_VALUE;
        kwrg$maxX = Integer.MIN_VALUE;
        kwrg$maxY = Integer.MIN_VALUE;

        if (DynamicIslandRenderer.getTabProgress() > 0.005f) {
            DynamicIslandRenderer.renderTabBackground(graphics);
            DynamicIslandRenderer.beginTabScissor(graphics);
        }
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void kwrg$captureAndSuppressBackground(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY, int color) {

        if (!IrcConfig.get().dynamicIsland) {
            graphics.fill(minX, minY, maxX, maxY, color);
            return;
        }

        kwrg$minX = Math.min(kwrg$minX, Math.min(minX, maxX));
        kwrg$minY = Math.min(kwrg$minY, Math.min(minY, maxY));
        kwrg$maxX = Math.max(kwrg$maxX, Math.max(minX, maxX));
        kwrg$maxY = Math.max(kwrg$maxY, Math.max(minY, maxY));

        if (DynamicIslandRenderer.getTabProgress() > 0.02f) {
            return;
        }

        graphics.fill(minX, minY, maxX, maxY, color);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kwrg$onTabRenderTail(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective displayObjective, CallbackInfo ci) {

        if (!IrcConfig.get().dynamicIsland) {
            return;
        }

        DynamicIslandRenderer.endTabScissor(graphics);

        if (kwrg$maxX > kwrg$minX && kwrg$maxY > kwrg$minY) {
            float realW = (kwrg$maxX - kwrg$minX) + 14.0f;
            float realH = (kwrg$maxY - kwrg$minY) + 12.0f;
            float realY = kwrg$minY - 6.0f;
            DynamicIslandRenderer.updateTargetTabBounds(realW, realH, realY);
        }
    }
}