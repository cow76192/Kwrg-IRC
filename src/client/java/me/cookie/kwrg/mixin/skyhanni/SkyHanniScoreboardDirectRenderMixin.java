package me.cookie.kwrg.mixin.skyhanni;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.gui.customscoreboard.CustomScoreboard", remap = false)
public abstract class SkyHanniScoreboardDirectRenderMixin {

    @Unique private static Method kwrg$isEnabledMethod;
    @Unique private static Field kwrg$displayField;
    @Unique private static Field kwrg$cacheField;
    @Unique private static Method kwrg$getWidthMethod;
    @Unique private static Method kwrg$getHeightMethod;
    @Unique private static Method kwrg$renderMethod;
    @Unique private static Method kwrg$getConfigMethod;
    @Unique private static Method kwrg$getPositionMethod;
    @Unique private static Method kwrg$getEffectiveScaleMethod;

    @Inject(method = "onGuiRenderOverlay", at = @At("HEAD"), cancellable = true)
    private void kwrg$onDirectRenderOverlay(CallbackInfo ci) {
        if (!ScoreboardIslandRenderer.isEnabled()) return;

        try {
            Class<?> csClass = this.getClass();

            if (kwrg$isEnabledMethod == null) {
                kwrg$isEnabledMethod = csClass.getDeclaredMethod("isEnabled");
                kwrg$isEnabledMethod.setAccessible(true);
            }
            boolean skyHanniScoreboardEnabled = (boolean) kwrg$isEnabledMethod.invoke(this);
            if (!skyHanniScoreboardEnabled) {
                return;
            }

            GuiGraphicsExtractor extractor = DynamicIslandRenderer.getCurrentExtractor();
            if (extractor == null) return;

            if (kwrg$displayField == null) {
                kwrg$displayField = csClass.getDeclaredField("display");
                kwrg$displayField.setAccessible(true);
                kwrg$cacheField = csClass.getDeclaredField("cache");
                kwrg$cacheField.setAccessible(true);
                kwrg$getConfigMethod = csClass.getMethod("getConfig");
            }

            Object renderable = kwrg$displayField.get(this);
            if (renderable == null) {
                renderable = kwrg$cacheField.get(this);
            }
            if (renderable == null) return;

            if (kwrg$getWidthMethod == null) {
                Class<?> rClass = renderable.getClass();
                kwrg$getWidthMethod = rClass.getMethod("getWidth");
                kwrg$getHeightMethod = rClass.getMethod("getHeight");
                for (Method m : rClass.getMethods()) {
                    if (m.getName().equals("render") && m.getParameterCount() == 2) {
                        kwrg$renderMethod = m;
                        break;
                    }
                }
            }

            float scale = 1.0f;
            try {
                Object config = kwrg$getConfigMethod.invoke(this);
                if (kwrg$getPositionMethod == null) {
                    kwrg$getPositionMethod = config.getClass().getMethod("getPosition");
                }
                Object position = kwrg$getPositionMethod.invoke(config);
                if (kwrg$getEffectiveScaleMethod == null) {
                    kwrg$getEffectiveScaleMethod = position.getClass().getMethod("getEffectiveScale");
                }
                scale = ((Number) kwrg$getEffectiveScaleMethod.invoke(position)).floatValue();
            } catch (Throwable ignored) {}

            int rawW = ((Number) kwrg$getWidthMethod.invoke(renderable)).intValue();
            int rawH = ((Number) kwrg$getHeightMethod.invoke(renderable)).intValue();
            if (rawW <= 0 || rawH <= 0) return;

            float scaledW = rawW * scale;
            float scaledH = rawH * scale;

            ScoreboardIslandRenderer.updateTargetBounds(scaledW, scaledH);
            ScoreboardIslandRenderer.markSkyHanniActive();
            ScoreboardIslandRenderer.updateAnimation();

            ScoreboardIslandRenderer.renderScoreboardBackground(OwoUIGraphics.of(extractor));

            HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
            int pad = state.padding;

            boolean scissored = ScoreboardIslandRenderer.beginScissor(extractor);

            extractor.pose().pushMatrix();
            try {
                extractor.pose().translate(ScoreboardIslandRenderer.getSmoothX() + pad, ScoreboardIslandRenderer.getSmoothY() + pad);
                if (Math.abs(scale - 1.0f) > 0.001f) {
                    extractor.pose().scale(scale, scale);
                }
                if (kwrg$renderMethod != null) {
                    kwrg$renderMethod.invoke(renderable, 0, 0);
                }
            } finally {
                extractor.pose().popMatrix();
                if (scissored) {
                    ScoreboardIslandRenderer.endScissor(extractor);
                }
            }

            ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}