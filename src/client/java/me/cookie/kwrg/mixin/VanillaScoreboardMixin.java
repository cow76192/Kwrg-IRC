package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Mixin(Gui.class)
public abstract class VanillaScoreboardMixin {

    @Unique private static float kwrg$currentMinX = Float.MAX_VALUE;
    @Unique private static float kwrg$currentMinY = Float.MAX_VALUE;
    @Unique private static float kwrg$currentMaxX = -Float.MAX_VALUE;
    @Unique private static float kwrg$currentMaxY = -Float.MAX_VALUE;

    @Unique private final List<Runnable> kwrg$scoreboardText = new ArrayList<>();
    @Unique private boolean kwrg$collectingScoreboard = false;
    @Unique private static final Pattern kwrg$scoreNumber = Pattern.compile("^[0-9,.-]+$");

    @Unique private static Method kwrg$shHideScoreboardMethod = null;
    @Unique private static boolean kwrg$shMethodResolved = false;

    @Unique
    private static boolean kwrg$isSkyHanniHandlingScoreboard() {
        if (ScoreboardIslandRenderer.isSkyHanniActive()) return true;
        try {
            if (!kwrg$shMethodResolved) {
                kwrg$shMethodResolved = true;
                Class<?> cs = Class.forName("at.hannibal2.skyhanni.features.gui.customscoreboard.CustomScoreboard");
                kwrg$shHideScoreboardMethod = cs.getMethod("isHideVanillaScoreboardEnabled");
            }
            return kwrg$shHideScoreboardMethod != null && (boolean) kwrg$shHideScoreboardMethod.invoke(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @WrapOperation(
            method = "extractScoreboardSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V"
            )
    )
    private void kwrg$safelyWrapScoreboard(Gui instance, GuiGraphicsExtractor graphics, Objective objective, Operation<Void> original) {
        if (!ScoreboardIslandRenderer.isEnabled() || kwrg$isSkyHanniHandlingScoreboard()) {
            original.call(instance, graphics, objective);
            return;
        }

        kwrg$currentMinX = Float.MAX_VALUE;
        kwrg$currentMinY = Float.MAX_VALUE;
        kwrg$currentMaxX = -Float.MAX_VALUE;
        kwrg$currentMaxY = -Float.MAX_VALUE;

        kwrg$scoreboardText.clear();
        kwrg$collectingScoreboard = true;
        try {
            // Capture vanilla's current layout before moving it. Previous-frame bounds
            // jump whenever a line changes width, the row count changes, or the window resizes.
            original.call(instance, graphics, objective);
            kwrg$collectingScoreboard = false;
            if (kwrg$scoreboardText.isEmpty()) return;

            ScoreboardIslandRenderer.updateTargetBounds(kwrg$currentMaxX - kwrg$currentMinX, kwrg$currentMaxY - kwrg$currentMinY);
            ScoreboardIslandRenderer.updateAnimation();
            ScoreboardIslandRenderer.renderScoreboardBackground(OwoUIGraphics.of(graphics));

            HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
            float shiftX = ScoreboardIslandRenderer.getSmoothX() + state.padding - kwrg$currentMinX;
            float shiftY = ScoreboardIslandRenderer.getSmoothY() + state.padding - kwrg$currentMinY;

            boolean scissored = ScoreboardIslandRenderer.beginScissor(graphics);
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(shiftX, shiftY);
                for (Runnable drawText : kwrg$scoreboardText) drawText.run();
            } finally {
                graphics.pose().popMatrix();
                if (scissored) ScoreboardIslandRenderer.endScissor(graphics);
            }
            ScoreboardIslandRenderer.markScoreboardRendered();
        } finally {
            kwrg$collectingScoreboard = false;
            kwrg$scoreboardText.clear();
        }
    }

    @Redirect(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void kwrg$suppressVanillaLineBackground(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY, int color) {
        if (!kwrg$collectingScoreboard) {
            graphics.fill(minX, minY, maxX, maxY, color);
        }
    }

    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
            )
    )
    private void kwrg$measureAndDrawText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Void> original) {
        if (kwrg$collectingScoreboard) {
            String raw = text.getString().trim();

            if (IrcConfig.get().scoreboardHideScores && kwrg$scoreNumber.matcher(raw).matches()) {
                return;
            }

            int tw = font.width(text);
            int th = 9;

            kwrg$currentMinX = Math.min(kwrg$currentMinX, x);
            kwrg$currentMinY = Math.min(kwrg$currentMinY, y);
            kwrg$currentMaxX = Math.max(kwrg$currentMaxX, x + tw);
            kwrg$currentMaxY = Math.max(kwrg$currentMaxY, y + th);
            kwrg$scoreboardText.add(() -> original.call(graphics, font, text, x, y, color, shadow));
            return;
        }

        original.call(graphics, font, text, x, y, color, shadow);
    }
}
