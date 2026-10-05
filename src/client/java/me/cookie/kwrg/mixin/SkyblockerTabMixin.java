package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.tabhud.screenbuilder.LayerBuilder", remap = false)
public abstract class SkyblockerTabMixin {

    @Shadow
    public abstract Collection<?> getRendered();

    @Unique private static boolean kwrg$scissored = false;
    @Unique private static boolean kwrg$shifted = false;
    @Unique private static Field kwrg$widgetField;
    @Unique private static Field kwrg$visibleField;
    @Unique private static Field kwrg$fromTabField;
    @Unique private static Method kwrg$getXMethod;
    @Unique private static Method kwrg$getYMethod;
    @Unique private static Method kwrg$getWidthMethod;
    @Unique private static Method kwrg$getHeightMethod;
    @Unique private static Method kwrg$getScaleFactorMethod;

    @Unique
    private boolean kwrg$isTabLayer() {
        return this.getClass().getName().endsWith("TabLayerBuilder");
    }

    @Unique
    private static boolean kwrg$isSkyblockerTabEnabled() {
        try {
            Class<?> cfgClass = Class.forName("de.hysky.skyblocker.config.SkyblockerConfigManager");
            Object cfg = cfgClass.getMethod("get").invoke(null);
            Object ui = cfg.getClass().getField("uiAndVisuals").get(cfg);
            Object tabHud = ui.getClass().getField("tabHud").get(ui);
            return tabHud.getClass().getField("tabHudEnabled").getBoolean(tabHud);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private float kwrg$getScale() {
        try {
            if (kwrg$getScaleFactorMethod == null) {
                Class<?> tabHudClass = Class.forName("de.hysky.skyblocker.skyblock.tabhud.TabHud");
                kwrg$getScaleFactorMethod = tabHudClass.getMethod("getScaleFactor");
            }
            return ((Number) kwrg$getScaleFactorMethod.invoke(null)).floatValue();
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    @Inject(method = "extractRenderStates", at = @At("HEAD"))
    private void kwrg$onExtractStatesHead(@Coerce Object graphicsObj, int screenWidth, int screenHeight, boolean config, CallbackInfo ci) {
        kwrg$scissored = false;
        kwrg$shifted = false;
        if (!IrcConfig.get().dynamicIsland || !kwrg$isTabLayer() || config || !kwrg$isSkyblockerTabEnabled() || !kwrg$hasValidTabWidgets()) {
            return;
        }

        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) graphicsObj;
        DynamicIslandRenderer.setCurrentExtractor(graphics);
        DynamicIslandRenderer.markSkyblockerTabRendered();
        DynamicIslandRenderer.updateAnimationState();

        Minecraft mc = Minecraft.getInstance();
        boolean isTabActive = DynamicIslandRenderer.getTabProgress() > 0.001f
                || (mc.options.keyPlayerList.isDown() && mc.player != null);

        if (isTabActive) {
            float scale = kwrg$getScale();

            graphics.pose().pushMatrix();
            graphics.pose().scale(1.0f / scale, 1.0f / scale);

            DynamicIslandRenderer.renderTabBackgroundDirect();
            DynamicIslandRenderer.beginTabScissorDirect();

            graphics.pose().popMatrix();

            kwrg$shifted = DynamicIslandRenderer.pushTabContentShift(scale);

            kwrg$scissored = true;
        }
    }

    @Inject(method = "extractRenderStates", at = @At("RETURN"))
    private void kwrg$onExtractStatesReturn(@Coerce Object graphicsObj, int screenWidth, int screenHeight, boolean config, CallbackInfo ci) {
        if (kwrg$shifted) {
            kwrg$shifted = false;
            DynamicIslandRenderer.popTabContentShift();
        }

        if (!IrcConfig.get().dynamicIsland || !kwrg$isTabLayer() || config || !kwrg$isSkyblockerTabEnabled()) {
            return;
        }

        if (kwrg$scissored) {
            DynamicIslandRenderer.endTabScissorDirect();
            kwrg$scissored = false;
        }

        kwrg$updateIslandBounds();
    }

    @Unique
    private boolean kwrg$hasValidTabWidgets() {
        Collection<?> rendered = getRendered();
        if (rendered == null || rendered.isEmpty()) return false;
        for (Object pw : rendered) {
            if (pw == null) continue;
            try {
                if (kwrg$fromTabField == null) {
                    kwrg$fromTabField = pw.getClass().getDeclaredField("fromTab");
                    kwrg$fromTabField.setAccessible(true);
                }
                if (kwrg$fromTabField.getBoolean(pw)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    @Unique
    private void kwrg$updateIslandBounds() {
        Collection<?> rendered = getRendered();
        if (rendered == null || rendered.isEmpty()) return;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean hasVisibleTabWidgets = false;

        for (Object pw : rendered) {
            if (pw == null) continue;
            try {
                if (kwrg$widgetField == null) {
                    Class<?> pwClass = pw.getClass();
                    kwrg$widgetField = pwClass.getDeclaredField("widget");
                    kwrg$widgetField.setAccessible(true);
                    kwrg$visibleField = pwClass.getDeclaredField("visible");
                    kwrg$visibleField.setAccessible(true);
                    kwrg$fromTabField = pwClass.getDeclaredField("fromTab");
                    kwrg$fromTabField.setAccessible(true);
                }

                if (!kwrg$fromTabField.getBoolean(pw)) continue;
                if (!kwrg$visibleField.getBoolean(pw)) continue;

                Object widget = kwrg$widgetField.get(pw);
                if (widget == null) continue;

                if (kwrg$getXMethod == null) {
                    Class<?> wc = widget.getClass();
                    kwrg$getXMethod = wc.getMethod("getX");
                    kwrg$getYMethod = wc.getMethod("getY");
                    kwrg$getWidthMethod = wc.getMethod("getWidth");
                    kwrg$getHeightMethod = wc.getMethod("getHeight");
                }

                int x = (int) kwrg$getXMethod.invoke(widget);
                int y = (int) kwrg$getYMethod.invoke(widget);
                int w = (int) kwrg$getWidthMethod.invoke(widget);
                int h = (int) kwrg$getHeightMethod.invoke(widget);

                if (w > 0 && h > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x + w);
                    maxY = Math.max(maxY, y + h);
                    hasVisibleTabWidgets = true;
                }
            } catch (Throwable ignored) {}
        }

        if (hasVisibleTabWidgets && maxX > minX && maxY > minY) {
            float scale = kwrg$getScale();
            float padH = 16.0f;
            float padV = 14.0f;

            float realW = (maxX - minX) * scale + padH;
            float realH = (maxY - minY) * scale + padV;
            float realY = minY * scale - (padV / 2.0f);

            DynamicIslandRenderer.updateTargetTabBounds(realW, realH, realY);
        }
    }
}