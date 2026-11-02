package me.cookie.kwrg.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;

public final class SkyHanniTabAdapter {

    private static Object skyHanniInstance = null;
    private static Method skyHanniDrawMethod = null;

    private static Method skyblockerExtractMethod = null;
    private static Method skyblockerGetScaleMethod = null;

    private SkyHanniTabAdapter() {
    }

    public static void setSkyHanniInstance(Object instance) {
        skyHanniInstance = instance;
    }

    public static void renderSkyblockerTab(GuiGraphicsExtractor graphics) {
        if (!DynamicIslandRenderer.isTabFeatureOn() || graphics == null) return;
        DynamicIslandRenderer.setCurrentExtractor(graphics);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.player == null) return;

        try {
            Class<?> wmClass = Class.forName("de.hysky.skyblocker.skyblock.tabhud.screenbuilder.WidgetManager");
            Object screenBuilder = wmClass.getField("SCREEN_BUILDER").get(null);
            Method tabMethod = screenBuilder.getClass().getMethod("tab");
            Object tabLayer = tabMethod.invoke(screenBuilder);

            if (skyblockerGetScaleMethod == null) {
                Class<?> tabHudClass = Class.forName("de.hysky.skyblocker.skyblock.tabhud.TabHud");
                skyblockerGetScaleMethod = tabHudClass.getMethod("getScaleFactor");
            }
            float scale = ((Number) skyblockerGetScaleMethod.invoke(null)).floatValue();

            int screenW = (int) (mc.getWindow().getGuiScaledWidth() / scale);
            int screenH = (int) (mc.getWindow().getGuiScaledHeight() / scale);

            if (skyblockerExtractMethod == null) {
                for (Method m : tabLayer.getClass().getMethods()) {
                    if (m.getName().equals("extractRenderStates") && m.getParameterCount() == 4) {
                        skyblockerExtractMethod = m;
                        break;
                    }
                }
            }

            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            try {
                skyblockerExtractMethod.invoke(tabLayer, graphics, screenW, screenH, false);
            } finally {
                graphics.pose().popMatrix();
            }
        } catch (Throwable ignored) {}
    }

    public static void renderSkyHanniTab(GuiGraphicsExtractor graphics) {
        if (!DynamicIslandRenderer.isTabFeatureOn()) return;
        if (skyHanniInstance != null && graphics != null) {
            DynamicIslandRenderer.setCurrentExtractor(graphics);
            try {
                Class<?> dcuClass = Class.forName("at.hannibal2.skyhanni.utils.compat.DrawContextUtils");
                Object dcuInstance = dcuClass.getField("INSTANCE").get(null);
                Method setContextMethod = dcuClass.getMethod("setContext", GuiGraphicsExtractor.class);
                Method clearContextMethod = dcuClass.getMethod("clearContext");

                setContextMethod.invoke(dcuInstance, graphics);
                try {
                    if (skyHanniDrawMethod == null) {
                        skyHanniDrawMethod = skyHanniInstance.getClass().getDeclaredMethod("drawTabList");
                        skyHanniDrawMethod.setAccessible(true);
                    }
                    skyHanniDrawMethod.invoke(skyHanniInstance);
                } finally {
                    clearContextMethod.invoke(dcuInstance);
                }
            } catch (Throwable ignored) {}
        }
    }
}
