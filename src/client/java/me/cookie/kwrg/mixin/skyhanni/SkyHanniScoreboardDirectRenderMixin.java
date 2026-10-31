package me.cookie.kwrg.mixin.skyhanni;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
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
    @Unique private static Object kwrg$guiEditManagerInstance;
    @Unique private static Method kwrg$isInGuiMethod;
    @Unique private static Method kwrg$gemAddMethod;

    @Unique private static Field kwrg$displayField;
    @Unique private static Field kwrg$cacheField;
    @Unique private static Method kwrg$getWidthMethod;
    @Unique private static Method kwrg$getHeightMethod;
    @Unique private static Method kwrg$renderMethod;

    @Unique private static Method kwrg$getConfigMethod;
    @Unique private static Method kwrg$getPositionMethod;

    @Unique private static Object kwrg$dcuInstance;
    @Unique private static Method kwrg$dcuGetContextMethod;

    @Inject(method = "onGuiRenderOverlay", at = @At("HEAD"), cancellable = true)
    private void kwrg$onDirectRenderOverlay(CallbackInfo ci) {
        if (!ScoreboardIslandRenderer.isEnabled()) return;

        try {
            Class<?> csClass = this.getClass();

            if (kwrg$isInGuiMethod == null) {
                try {
                    Class<?> gemClass = Class.forName("at.hannibal2.skyhanni.data.GuiEditManager");
                    kwrg$guiEditManagerInstance = gemClass.getField("INSTANCE").get(null);
                    kwrg$isInGuiMethod = gemClass.getMethod("isInGui");
                } catch (Throwable ignored) {}
            }
            if (kwrg$isInGuiMethod != null && kwrg$guiEditManagerInstance != null) {
                if ((boolean) kwrg$isInGuiMethod.invoke(kwrg$guiEditManagerInstance)) {
                    return;
                }
            }

            if (kwrg$isEnabledMethod == null) {
                kwrg$isEnabledMethod = csClass.getDeclaredMethod("isEnabled");
                kwrg$isEnabledMethod.setAccessible(true);
            }
            if (!((boolean) kwrg$isEnabledMethod.invoke(this))) {
                return;
            }

            if (kwrg$dcuGetContextMethod == null) {
                Class<?> dcuClass = Class.forName("at.hannibal2.skyhanni.utils.compat.DrawContextUtils");
                kwrg$dcuInstance = dcuClass.getField("INSTANCE").get(null);
                kwrg$dcuGetContextMethod = dcuClass.getMethod("getDrawContext");
            }
            GuiGraphicsExtractor actualExtractor = (GuiGraphicsExtractor) kwrg$dcuGetContextMethod.invoke(kwrg$dcuInstance);
            if (actualExtractor == null) return;

            if (kwrg$displayField == null) {
                kwrg$displayField = csClass.getDeclaredField("display");
                kwrg$displayField.setAccessible(true);
                kwrg$cacheField = csClass.getDeclaredField("cache");
                kwrg$cacheField.setAccessible(true);
            }

            Object renderable = kwrg$displayField.get(this);
            if (renderable == null) {
                renderable = kwrg$cacheField.get(this);
            }
            if (renderable == null) {
                return;
            }

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
            if (kwrg$renderMethod == null) return;

            Object config = null;
            if (kwrg$getConfigMethod == null) {
                for (Method m : csClass.getDeclaredMethods()) {
                    if (m.getName().startsWith("getConfig") && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        kwrg$getConfigMethod = m;
                        break;
                    }
                }
            }
            if (kwrg$getConfigMethod != null) {
                config = kwrg$getConfigMethod.invoke(this);
            }

            if (config == null) {
                try {
                    Class<?> modClass = Class.forName("at.hannibal2.skyhanni.SkyHanniMod");
                    Object feature = modClass.getMethod("getFeature").invoke(null);
                    Object gui = feature.getClass().getMethod("getGui").invoke(feature);
                    config = gui.getClass().getMethod("getCustomScoreboard").invoke(gui);
                } catch (Throwable ignored) {}
            }

            Object position = null;
            if (config != null) {
                if (kwrg$getPositionMethod == null) {
                    for (Method m : config.getClass().getMethods()) {
                        if (m.getName().startsWith("getPosition") && m.getParameterCount() == 0) {
                            m.setAccessible(true);
                            kwrg$getPositionMethod = m;
                            break;
                        }
                    }
                }
                if (kwrg$getPositionMethod != null) {
                    position = kwrg$getPositionMethod.invoke(config);
                }
            }

            int rawW = ((Number) kwrg$getWidthMethod.invoke(renderable)).intValue();
            int rawH = ((Number) kwrg$getHeightMethod.invoke(renderable)).intValue();
            if (rawW <= 0 || rawH <= 0) return;

            if (position != null) {
                try {
                    if (kwrg$gemAddMethod == null) {
                        Class<?> gemClass = Class.forName("at.hannibal2.skyhanni.data.GuiEditManager");
                        Class<?> posClass = Class.forName("at.hannibal2.skyhanni.config.core.config.Position");
                        kwrg$gemAddMethod = gemClass.getMethod("add", posClass, String.class, int.class, int.class);
                    }
                    kwrg$gemAddMethod.invoke(null, position, "Custom Scoreboard", rawW, rawH);
                } catch (Throwable ignored) {}
            }

            ScoreboardIslandRenderer.updateTargetBounds(rawW, rawH);
            ScoreboardIslandRenderer.updateAnimation();

            ScoreboardIslandRenderer.renderScoreboardBackground(OwoUIGraphics.of(actualExtractor));

            HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
            int pad = state.padding;
            float scale = ScoreboardIslandRenderer.getScale();

            boolean scissored = ScoreboardIslandRenderer.beginScissor(actualExtractor);

            actualExtractor.pose().pushMatrix();
            try {
                actualExtractor.pose().translate(ScoreboardIslandRenderer.getSmoothX(), ScoreboardIslandRenderer.getSmoothY());
                if (Math.abs(scale - 1.0f) > 0.001f) {
                    actualExtractor.pose().scale(scale, scale);
                }
                actualExtractor.pose().translate(pad, pad);
                kwrg$renderMethod.invoke(renderable, 0, 0);
            } finally {
                actualExtractor.pose().popMatrix();
                if (scissored) {
                    ScoreboardIslandRenderer.endScissor(actualExtractor);
                }
            }

            ScoreboardIslandRenderer.markSkyHanniActive();
            ScoreboardIslandRenderer.markScoreboardRendered();
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}