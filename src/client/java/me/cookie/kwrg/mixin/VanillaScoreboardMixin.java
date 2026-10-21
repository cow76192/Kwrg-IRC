package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudEditorScreen;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.util.Collection;

@Mixin(Gui.class)
public abstract class VanillaScoreboardMixin {

    @Unique private static float kwrg$currentMinX = Float.MAX_VALUE;
    @Unique private static float kwrg$currentMinY = Float.MAX_VALUE;
    @Unique private static float kwrg$currentMaxX = -Float.MAX_VALUE;
    @Unique private static float kwrg$currentMaxY = -Float.MAX_VALUE;

    @Unique private static float kwrg$lastRawMinX = -1.0f;
    @Unique private static float kwrg$lastRawMinY = -1.0f;

    @Unique private static Method kwrg$shHideScoreboardMethod = null;

    @Unique
    private static boolean kwrg$isSkyHanniHandlingScoreboard() {
        if (ScoreboardIslandRenderer.isSkyHanniActive()) return true;
        try {
            if (kwrg$shHideScoreboardMethod == null) {
                Class<?> cs = Class.forName("at.hannibal2.skyhanni.features.gui.customscoreboard.CustomScoreboard");
                kwrg$shHideScoreboardMethod = cs.getMethod("isHideVanillaScoreboardEnabled");
            }
            return (boolean) kwrg$shHideScoreboardMethod.invoke(null);
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
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof HudEditorScreen || kwrg$isSkyHanniHandlingScoreboard() || !ScoreboardIslandRenderer.isEnabled()) {
            original.call(instance, graphics, objective);
            return;
        }

        Scoreboard scoreboard = objective.getScoreboard();
        Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(objective);
        if (scores.isEmpty()) {
            original.call(instance, graphics, objective);
            return;
        }

        kwrg$currentMinX = Float.MAX_VALUE;
        kwrg$currentMinY = Float.MAX_VALUE;
        kwrg$currentMaxX = -Float.MAX_VALUE;
        kwrg$currentMaxY = -Float.MAX_VALUE;

        ScoreboardIslandRenderer.updateAnimation();
        ScoreboardIslandRenderer.renderScoreboardBackground(OwoUIGraphics.of(graphics));

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
        int pad = state.padding;

        float targetTextX = ScoreboardIslandRenderer.getSmoothX() + pad;
        float targetTextY = ScoreboardIslandRenderer.getSmoothY() + pad;

        float shiftX;
        float shiftY;

        if (kwrg$lastRawMinX > 0.0f && kwrg$lastRawMinY > 0.0f) {
            shiftX = targetTextX - kwrg$lastRawMinX;
            shiftY = targetTextY - kwrg$lastRawMinY;
        } else {
            shiftX = targetTextX - (graphics.guiWidth() - 120);
            shiftY = targetTextY - (graphics.guiHeight() / 2 - 40);
        }

        boolean scissored = ScoreboardIslandRenderer.beginScissor(graphics);
        graphics.pose().pushMatrix();
        graphics.pose().translate(shiftX, shiftY);

        try {
            original.call(instance, graphics, objective);

            if (kwrg$currentMaxX > kwrg$currentMinX && kwrg$currentMaxY > kwrg$currentMinY) {
                float actualTextW = kwrg$currentMaxX - kwrg$currentMinX;
                float actualTextH = kwrg$currentMaxY - kwrg$currentMinY;

                ScoreboardIslandRenderer.updateTargetBounds(actualTextW, actualTextH);

                kwrg$lastRawMinX = kwrg$currentMinX;
                kwrg$lastRawMinY = kwrg$currentMinY;
            }
        } finally {
            graphics.pose().popMatrix();
            if (scissored) {
                ScoreboardIslandRenderer.endScissor(graphics);
            }
        }
    }

    @Redirect(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void kwrg$suppressVanillaLineBackground(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY, int color) {
        if (!ScoreboardIslandRenderer.isEnabled()) {
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
        if (ScoreboardIslandRenderer.isEnabled() && !ScoreboardIslandRenderer.isSkyHanniActive()) {
            String raw = text.getString().trim();

            if (IrcConfig.get().scoreboardHideScores && raw.matches("^[0-9,.-]+$")) {
                return;
            }

            int tw = font.width(text);
            int th = 9;

            kwrg$currentMinX = Math.min(kwrg$currentMinX, x);
            kwrg$currentMinY = Math.min(kwrg$currentMinY, y);
            kwrg$currentMaxX = Math.max(kwrg$currentMaxX, x + tw);
            kwrg$currentMaxY = Math.max(kwrg$currentMaxY, y + th);
        }

        original.call(graphics, font, text, x, y, color, shadow);
    }
}