package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(Gui.class)
public abstract class GuiScoreboardMixin {

    @Unique private static int kwrg$minX;
    @Unique private static int kwrg$minY;
    @Unique private static int kwrg$maxX;
    @Unique private static int kwrg$maxY;
    @Unique private static boolean kwrg$captured;
    @Unique private static boolean kwrg$skyblockSidebar;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void kwrg$onSidebarHead(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        kwrg$skyblockSidebar = kwrg$isSkyblockSidebar(objective);
        kwrg$captured = false;
        kwrg$minX = Integer.MAX_VALUE;
        kwrg$minY = Integer.MAX_VALUE;
        kwrg$maxX = Integer.MIN_VALUE;
        kwrg$maxY = Integer.MIN_VALUE;
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void kwrg$onSidebarReturn(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        ScoreboardIslandRenderer.endIsland();

        if (kwrg$captured) {
            ScoreboardIslandRenderer.updateTargetBounds(kwrg$maxX - kwrg$minX, kwrg$maxY - kwrg$minY);
        }
    }

    @Redirect(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void kwrg$captureAndSuppressBackground(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY, int color) {
        if (!kwrg$skyblockSidebar) {
            graphics.fill(minX, minY, maxX, maxY, color);
            return;
        }

        kwrg$minX = Math.min(kwrg$minX, Math.min(minX, maxX));
        kwrg$minY = Math.min(kwrg$minY, Math.min(minY, maxY));
        kwrg$maxX = Math.max(kwrg$maxX, Math.max(minX, maxX));
        kwrg$maxY = Math.max(kwrg$maxY, Math.max(minY, maxY));
        kwrg$captured = true;

        if (!ScoreboardIslandRenderer.isIslandEnabled()) {
            graphics.fill(minX, minY, maxX, maxY, color);
            return;
        }

        if (!ScoreboardIslandRenderer.hasActiveIsland()) {
            ScoreboardIslandRenderer.beginIsland(graphics, kwrg$minX, kwrg$minY, kwrg$maxX, kwrg$maxY);
        }
    }

    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
                    ordinal = 2
            )
    )
    private void kwrg$wrapScoreText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Void> original) {
        if (IrcConfig.get().scoreboardHideScores) return;
        original.call(graphics, font, text, x, y, color, shadow);
    }

    @Unique
    private static boolean kwrg$isSkyblockSidebar(Objective objective) {
        if (objective == null) return false;

        Component title = objective.getDisplayName();
        return title != null && title.getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }
}
