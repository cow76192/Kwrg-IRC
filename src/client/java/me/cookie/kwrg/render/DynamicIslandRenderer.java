package me.cookie.kwrg.render;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.HardwareMetricsTracker;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.gui.util.ServerLatencyTracker;
import me.cookie.kwrg.gui.util.TpsTracker;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.config.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DynamicIslandRenderer {

    private static final FontDescription ISLAND_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("kwrg", "island"));
    private static final Style ISLAND_STYLE = Style.EMPTY.withFont(ISLAND_FONT);

    private static final float BASE_HEIGHT = 20.0f;
    private static final float EXTRA_ROW_HEIGHT = 16.0f; // 每一行额外内容的高度
    private static final float CORNER_RADIUS = 10.0f;

    private static final float WIDTH_ANIM_DURATION = 0.85f;
    private static final float HEIGHT_ANIM_DURATION = 0.45f;
    private static final float TAB_ANIM_DURATION = 0.38f;
    private static final float ALPHA_ANIM_DURATION = 0.35f;

    private static final float ITEM_GAP = 5.0f;
    private static final float SIDE_PAD = 12.0f;

    private static final float BASE_TOP_Y = 7.0f;
    private static final float SLIDE_IN_DISTANCE = 7.0f;

    private static final Component WARN_COMPONENT = Component.literal("⚠ Server Lagging (Low TPS)").setStyle(ISLAND_STYLE);

    private static final Map<String, String> CUSTOM_NOTICES = Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * 添加或更新自定义消息
     * @param key 消息标识符
     * @param text 消息内容
     */
    public static void setCustomNotice(String key, String text) {
        if (key == null) return;
        if (text == null || text.isBlank()) {
            CUSTOM_NOTICES.remove(key);
        } else {
            CUSTOM_NOTICES.put(key, text);
        }
    }

    /**
     * 添加自定义消息
     */
    public static void addCustomNotice(String key, String text) {
        setCustomNotice(key, text);
    }

    /**
     * 根据 key 移除指定的自定义消息
     */
    public static void removeCustomNotice(String key) {
        if (key != null) {
            CUSTOM_NOTICES.remove(key);
        }
    }

    public static void clearCustomNotices() {
        CUSTOM_NOTICES.clear();
    }

    public static Map<String, String> getCustomNotices() {
        synchronized (CUSTOM_NOTICES) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(CUSTOM_NOTICES));
        }
    }

    public static boolean hasCustomNotice(String key) {
        return key != null && CUSTOM_NOTICES.containsKey(key);
    }

    private static class IslandNoticeEntry {
        final Component component;
        final int color;
        final boolean isWarning;

        IslandNoticeEntry(Component component, int color, boolean isWarning) {
            this.component = component;
            this.color = color;
            this.isWarning = isWarning;
        }
    }

    private static List<IslandNoticeEntry> getActiveExtraLines(boolean isLowTps) {
        List<IslandNoticeEntry> lines = new ArrayList<>();

        synchronized (CUSTOM_NOTICES) {
            for (Map.Entry<String, String> entry : CUSTOM_NOTICES.entrySet()) {
                String text = entry.getValue();
                if (text != null && !text.isEmpty()) {
                    Component comp = Component.literal(text).setStyle(ISLAND_STYLE);
                    lines.add(new IslandNoticeEntry(comp, 0xFFECEFF1, false));
                }
            }
        }

        if (isLowTps) {
            lines.add(new IslandNoticeEntry(WARN_COMPONENT, 0xFFFF5F3C, true));
        }

        return lines;
    }

    private static boolean checkIsLowTps(Minecraft mc, IrcConfig config) {
        float tps = TpsTracker.getTps();
        if (tps < 15.5f) isLaggingState = true;
        else if (tps > 16.5f) isLaggingState = false;
        return config.islandShowTps && isLaggingState && mc.level != null && tabProgress <= 0.01f;
    }

    private static float currentAlpha = 0.0f;
    private static float startAlpha = 0.0f;
    private static float targetAlpha = 0.0f;
    private static float alphaAnimTimer = 1.0f;

    private static float shadowBlur = 4.0f;
    private static float shadowOffsetY = 1.2f;
    private static int shadowBaseAlpha = 50;

    private static float tabProgress = 0.0f;

    private static float targetTabW = 340.0f;
    private static float targetTabH = 150.0f;
    private static float targetTabY = 6.0f;

    private static float smoothTabW = 340.0f;
    private static float startTabW = 340.0f;
    private static float tabWidthTimer = 1.0f;

    private static float smoothTabH = 150.0f;
    private static float startTabH = 150.0f;
    private static float tabHeightTimer = 1.0f;

    private static float smoothTabY = 6.0f;

    private static float curRenderX = 0;
    private static float curRenderY = 0;
    private static float curRenderW = 0;
    private static float curRenderH = 0;
    private static float curCornerRadius = 10.0f;
    private static Color curBgColor = Color.ofRgb(0x11161F);
    private static Color curBorderColor = Color.ofRgb(0x5498B2);

    private static boolean isScissorActive = false;
    private static GuiGraphicsExtractor currentFrameExtractor = null;

    private static boolean wasSkyHanniInCurrentSession = false;
    private static Object skyHanniInstance = null;
    private static Method skyHanniDrawMethod = null;

    private static long lastIslandThrottleTime = 0;

    private static float tabShiftX = 0.0f;
    private static float tabShiftY = 0.0f;
    private static float tabTargetShiftX = 0.0f;
    private static float tabTargetShiftY = 0.0f;
    private static boolean editorPreview = false;

    public static GuiGraphicsExtractor getCurrentExtractor() { return currentFrameExtractor; }

    public static float resolveIslandCenterX(float width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return width / 2.0f;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.ISLAND);
        return HudElement.Anchor.resolveCenterX(state.anchorX, state.marginX,
                HudElementRegistry.resolveOffsetX(HudElementRegistry.ISLAND), width,
                mc.getWindow().getGuiScaledWidth());
    }

    public static float resolveIslandTopY(float height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return 0.0f;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.ISLAND);
        return HudElement.Anchor.resolveY(state.anchorY, state.marginY,
                HudElementRegistry.resolveOffsetY(HudElementRegistry.ISLAND), height,
                mc.getWindow().getGuiScaledHeight());
    }

    public static boolean isIslandVisible() {
        return currentAlpha > 0.005f;
    }

    public static float[] getIslandBounds() {
        if (isIslandVisible() && curRenderW > 0.0f && curRenderH > 0.0f) {
            return new float[]{curRenderX, curRenderY, curRenderW, curRenderH};
        }

        float width = Math.max(38.0f, computeContentWidth() + SIDE_PAD * 2.0f);
        float height = currentIslandHeight;
        return new float[]{resolveIslandCenterX(width) - width / 2.0f, resolveIslandTopY(height), width, height};
    }

    public static float getTabShiftX() { return tabShiftX; }
    public static float getTabShiftY() { return tabShiftY; }

    private static boolean tabShiftActive = false;
    private static GuiGraphicsExtractor tabShiftExtractor = null;
    private static float currentTabEase = 0.0f;

    public static boolean isTabFeatureOn() {
        return IrcConfig.get().dynamicTab;
    }

    public static boolean isEnabled() {
        return HudElementRegistry.state(HudElementRegistry.ISLAND).enabled;
    }

    public static boolean pushTabContentShift(float scale) {
        if (currentFrameExtractor == null || tabShiftActive) return false;
        if (Math.abs(tabShiftX) < 0.01f && Math.abs(tabShiftY) < 0.01f) return false;

        float safeScale = Math.abs(scale) < 0.0001f ? 1.0f : scale;

        tabShiftActive = true;
        tabShiftExtractor = currentFrameExtractor;
        currentFrameExtractor.pose().pushMatrix();
        currentFrameExtractor.pose().translate(tabShiftX / safeScale, tabShiftY / safeScale);
        return true;
    }

    public static void popTabContentShift() {
        if (!tabShiftActive || tabShiftExtractor == null) return;

        tabShiftActive = false;
        tabShiftExtractor.pose().popMatrix();
        tabShiftExtractor = null;
    }

    public static void beginTabShiftFrame() {
        if (!tabShiftActive) {
            tabShiftExtractor = null;
            return;
        }

        if (tabShiftExtractor == currentFrameExtractor) {
            tabShiftExtractor.pose().popMatrix();
        }
        tabShiftActive = false;
        tabShiftExtractor = null;
    }

    public static void setEditorPreview(boolean enabled) {
        editorPreview = enabled;
    }

    public static float easeOutQuint(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - p, 5.0f);
    }

    private static void updateAlphaAnimation(float target, float dt) {
        if (Math.abs(target - targetAlpha) > 0.001f) {
            startAlpha = currentAlpha;
            targetAlpha = target;
            alphaAnimTimer = 0.0f;
        }

        if (alphaAnimTimer < 1.0f) {
            alphaAnimTimer = Math.min(1.0f, alphaAnimTimer + dt / ALPHA_ANIM_DURATION);
            float ease = easeOutQuint(alphaAnimTimer);
            currentAlpha = Mth.lerp(ease, startAlpha, targetAlpha);
        } else {
            currentAlpha = targetAlpha;
        }
    }

    public static boolean isShadowEnabled() {
        return HudElementRegistry.state(HudElementRegistry.ISLAND).shadow;
    }

    public static float getShadowBlur() {
        return shadowBlur;
    }

    public static float getShadowOffsetY() {
        return shadowOffsetY;
    }

    public static int getShadowBaseAlpha() {
        return shadowBaseAlpha;
    }

    public static void setSkyHanniInstance(Object instance) {
        skyHanniInstance = instance;
    }

    public static void markSkyHanniTabRendered() {
        wasSkyHanniInCurrentSession = true;
    }

    public static boolean wasSkyHanniActive() {
        return wasSkyHanniInCurrentSession;
    }

    private static boolean wasSkyblockerInCurrentSession = false;
    private static Method skyblockerExtractMethod = null;
    private static Method skyblockerGetScaleMethod = null;

    private static long lastUpdateFrameTime = -1;

    public static void updateAnimationState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.font == null) return;

        long nowTime = System.currentTimeMillis();
        if (nowTime == lastUpdateFrameTime) return;

        float dt = (lastUpdateFrameTime < 0) ? 0.016f : (nowTime - lastUpdateFrameTime) / 1000.0f;
        lastUpdateFrameTime = nowTime;
        dt = Mth.clamp(dt, 0.001f, 0.04f);

        IrcConfig config = IrcConfig.get();
        currentTabEase = updateTabAnimation(dt, config);
        updateRenderRect(currentTabEase, dt, config);
    }

    private static float updateTabAnimation(float dt, IrcConfig config) {
        Minecraft mc = Minecraft.getInstance();

        if (tabWidthTimer < 1.0f) {
            tabWidthTimer = Math.min(1.0f, tabWidthTimer + dt / 0.35f);
            smoothTabW = Mth.lerp(AnimationUtil.easeSmoothDecel(tabWidthTimer), startTabW, targetTabW);
        } else {
            smoothTabW = targetTabW;
        }

        if (tabHeightTimer < 1.0f) {
            tabHeightTimer = Math.min(1.0f, tabHeightTimer + dt / 0.35f);
            smoothTabH = Mth.lerp(AnimationUtil.easeSmoothDecel(tabHeightTimer), startTabH, targetTabH);
        } else {
            smoothTabH = targetTabH;
        }

        smoothTabY = Mth.lerp(dt * 10.0f, smoothTabY, targetTabY);

        boolean isTabKeyDown = !editorPreview && IrcConfig.get().dynamicTab && mc.options.keyPlayerList.isDown() && mc.player != null;
        float targetTab = isTabKeyDown ? 1.0f : 0.0f;
        float tabSpeed = isTabKeyDown ? (1.0f / TAB_ANIM_DURATION) : (1.0f / (TAB_ANIM_DURATION * 0.85f));

        if (tabProgress < targetTab) {
            tabProgress = Math.min(targetTab, tabProgress + dt * tabSpeed);
        } else if (tabProgress > targetTab) {
            tabProgress = Math.max(targetTab, tabProgress - dt * tabSpeed);
        }

        if (tabProgress <= 0.005f) {
            wasSkyHanniInCurrentSession = false;
            wasSkyblockerInCurrentSession = false;
        }

        return AnimationUtil.easeSmoothDecel(tabProgress);
    }

    private static float computeContentWidth() {
        float total = 0.0f;
        SlotPhysics lastVisible = null;
        for (SlotPhysics slot : ALL_SLOTS) {
            float w = slot.getWidth();
            if (w > 0.001f) {
                if (lastVisible != null) {
                    total += ITEM_GAP * Math.min(1.0f, lastVisible.getRatio()) * Math.min(1.0f, slot.getRatio());
                }
                total += w;
                lastVisible = slot;
            }
        }
        return total;
    }

    private static boolean shouldIslandShow(IrcConfig config) {
        boolean hasAnyOption = (config.islandShowFps || config.islandShowPing || config.islandShowServerPing
                || config.islandShowTps || config.islandShowRam || config.islandShowGpu || config.islandShowCpu);
        boolean hasCustomNotices = !CUSTOM_NOTICES.isEmpty();
        boolean islandWanted = (DynamicIslandRenderer.isEnabled() || editorPreview) && (hasAnyOption || hasCustomNotices || editorPreview);
        boolean tabWanted = config.dynamicTab && tabProgress > 0.005f;

        return islandWanted || tabWanted;
    }

    private static void updateTabShift(float screenW, float screenH) {
        if (!IrcConfig.get().islandPosAffectsTab || !DynamicIslandRenderer.isEnabled()) {
            tabTargetShiftX = 0.0f;
            tabTargetShiftY = 0.0f;
            return;
        }

        float targetW = smoothTabW;
        float targetH = smoothTabH;

        float shiftX = resolveIslandCenterX(targetW) - screenW / 2.0f;
        if (screenW > targetW) {
            shiftX = Mth.clamp(shiftX, targetW / 2.0f - screenW / 2.0f, screenW / 2.0f - targetW / 2.0f);
        } else {
            shiftX = 0.0f;
        }

        float shiftY = resolveIslandTopY(targetH) - BASE_TOP_Y;
        if (screenH > targetH) {
            shiftY = Mth.clamp(shiftY, -smoothTabY, screenH - targetH - smoothTabY);
        } else {
            shiftY = 0.0f;
        }

        tabTargetShiftX = shiftX;
        tabTargetShiftY = shiftY;
    }

    private static void updateRenderRect(float tabEase, float dt, IrcConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        boolean shouldShow = shouldIslandShow(config);
        float targetOpen = shouldShow ? 1.0f : 0.0f;
        islandOpenProgress = Mth.clamp(islandOpenProgress + (targetOpen - islandOpenProgress) * dt * 6.0f, 0.0f, 1.0f);
        float openEase = AnimationUtil.easeSmoothDecel(islandOpenProgress);

        updateTabShift(screenW, screenH);

        // 动态计算多行文本所需的最大宽度
        boolean isLowTps = checkIsLowTps(mc, config);
        List<IslandNoticeEntry> activeLines = getActiveExtraLines(isLowTps);

        float maxExtraTextW = 0.0f;
        if (mc.font != null) {
            for (IslandNoticeEntry entry : activeLines) {
                float w = mc.font.width(entry.component);
                if (w > maxExtraTextW) {
                    maxExtraTextW = w;
                }
            }
        }

        float normalTargetWidth = computeContentWidth() + SIDE_PAD * 2.0f;
        float expandedTargetWidth = maxExtraTextW > 0.0f ? Math.max(normalTargetWidth, maxExtraTextW + 18.0f) : normalTargetWidth;

        float extraHeight = Math.max(0.0f, currentIslandHeight - BASE_HEIGHT);
        float expandRatio = targetIslandHeight > BASE_HEIGHT
                ? Mth.clamp(extraHeight / (targetIslandHeight - BASE_HEIGHT), 0.0f, 1.0f)
                : (extraHeight > 0.0f ? Mth.clamp(extraHeight / EXTRA_ROW_HEIGHT, 0.0f, 1.0f) : 0.0f);

        float pillW = Math.max(38.0f, Mth.lerp(expandRatio, normalTargetWidth, expandedTargetWidth));
        float pillH = currentIslandHeight;

        boolean islandOn = DynamicIslandRenderer.isEnabled();
        float pillCx = islandOn ? resolveIslandCenterX(pillW) : screenW / 2.0f;
        float pillTop = islandOn
                ? Mth.clamp(resolveIslandTopY(pillH) - SLIDE_IN_DISTANCE * (1.0f - openEase),
                0.0f, Math.max(0.0f, screenH - pillH))
                : smoothTabY;

        float tabCx = screenW / 2.0f + tabTargetShiftX;
        float tabTop = smoothTabY + tabTargetShiftY;

        curRenderW = Mth.lerp(tabEase, pillW, smoothTabW) * (0.85f + 0.15f * openEase);
        curRenderH = Mth.lerp(tabEase, pillH, smoothTabH);
        float centerX = Mth.lerp(tabEase, pillCx, tabCx);
        curRenderX = centerX - curRenderW / 2.0f;
        curRenderY = Mth.lerp(tabEase, pillTop, tabTop);
        curCornerRadius = Mth.lerp(tabEase, CORNER_RADIUS, 14.0f);

        tabShiftX = centerX - screenW / 2.0f;
        tabShiftY = curRenderY - smoothTabY;

        float effectiveOpacity = DynamicIslandRenderer.isEnabled()
                ? Mth.lerp(tabEase, HudElementRegistry.state(HudElementRegistry.ISLAND).opacity, config.tabOpacity)
                : config.tabOpacity;
        float expectedTargetAlpha = shouldShow ? (openEase * effectiveOpacity) : 0.0f;
        updateAlphaAnimation(expectedTargetAlpha, dt);

        // 仅在存在 TPS 告警时使边框变红警戒，普通自定义通知保持科技蓝色
        float lowTpsRatio = isLowTps ? expandRatio : 0.0f;
        int borderR = (int) Mth.lerp(lowTpsRatio, 84f, 255f);
        int borderG = (int) Mth.lerp(lowTpsRatio, 152f, 100f);
        int borderB = (int) Mth.lerp(lowTpsRatio, 178f, 90f);

        int bgR = 17;
        int bgG = 22;
        int bgB = 31;

        if (tabEase > 0.01f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 450.0);
            float accentMix = Mth.clamp(tabEase * (0.55f + 0.45f * pulse), 0.0f, 1.0f);

            borderR = (int) Mth.lerp(accentMix, borderR, 79f);
            borderG = (int) Mth.lerp(accentMix, borderG, 195f);
            borderB = (int) Mth.lerp(accentMix, borderB, 247f);

            bgR = (int) Mth.lerp(accentMix, bgR, 20f);
            bgG = (int) Mth.lerp(accentMix, bgG, 30f);
            bgB = (int) Mth.lerp(accentMix, bgB, 42f);
        }

        int bgAlpha = (int) (Mth.lerp(tabEase, 230f, 245f) * currentAlpha);
        int borderAlpha = (int) ((210.0f + tabEase * 35.0f) * currentAlpha);
        curBgColor = Color.ofArgb(ARGB.color(bgAlpha, bgR, bgG, bgB));
        curBorderColor = Color.ofArgb(ARGB.color(borderAlpha, borderR, borderG, borderB));
    }

    public static void markSkyblockerTabRendered() {
        wasSkyblockerInCurrentSession = true;
    }

    public static boolean wasSkyblockerActive() {
        return wasSkyblockerInCurrentSession;
    }

    public static void renderSkyblockerTab(GuiGraphicsExtractor graphics) {
        if (!isTabFeatureOn() || graphics == null) return;
        currentFrameExtractor = graphics;
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
        if (!isTabFeatureOn()) return;
        if (skyHanniInstance != null && graphics != null) {
            currentFrameExtractor = graphics;
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

    public static void updateTargetTabBounds(float w, float h, float y) {
        if (!isTabFeatureOn()) return;
        float newTargetW = Math.max(140.0f, w);
        float newTargetH = Math.max(40.0f, h);
        targetTabY = Math.max(4.0f, y);

        if (Math.abs(newTargetW - targetTabW) > 2.0f) {
            startTabW = smoothTabW;
            targetTabW = newTargetW;
            tabWidthTimer = 0.0f;
        }

        if (Math.abs(newTargetH - targetTabH) > 2.0f) {
            startTabH = smoothTabH;
            targetTabH = newTargetH;
            tabHeightTimer = 0.0f;
        }
    }

    public static float getTabProgress() {
        if (!isTabFeatureOn()) return 0.0f;
        return tabProgress;
    }

    public static boolean isTabAnimating() {
        if (!isTabFeatureOn()) return false;
        return tabProgress > 0.005f;
    }

    public static void setCurrentExtractor(GuiGraphicsExtractor extractor) {
        currentFrameExtractor = extractor;
    }

    public static void renderTabBackgroundDirect() {
        if (!isTabFeatureOn()) return;
        if (currentFrameExtractor != null && tabProgress > 0.005f && curRenderW > 0) {
            OwoUIGraphics graphics = OwoUIGraphics.of(currentFrameExtractor);
            if (isShadowEnabled()) {
                RenderUtil.drawSoftDropShadow(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius,
                        shadowBlur * 1.5f, shadowOffsetY * 1.5f, shadowBaseAlpha, currentAlpha);
            }
            RenderUtil.drawSubpixelRoundedPanel(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius, 1.2f, curBgColor, curBorderColor);
        }
    }

    public static void renderTabBackground(GuiGraphicsExtractor extractor) {
        if (!isTabFeatureOn()) return;
        setCurrentExtractor(extractor);
        renderTabBackgroundDirect();
    }

    public static void beginTabScissor(GuiGraphicsExtractor extractor) {
        if (!isTabFeatureOn()) return;
        setCurrentExtractor(extractor);
        beginTabScissorDirect();
    }

    public static void endTabScissor(GuiGraphicsExtractor extractor) {
        endTabScissorDirect();
    }

    public static void beginTabScissorDirect() {
        if (!isTabFeatureOn()) return;
        if (currentFrameExtractor != null && curRenderW > 0) {
            OwoUIGraphics graphics = OwoUIGraphics.of(currentFrameExtractor);
            int sX1 = (int) Math.floor(curRenderX + 1.0f);
            int sY1 = (int) Math.floor(curRenderY + 1.0f);
            int sX2 = (int) Math.ceil(curRenderX + curRenderW - 1.0f);
            int sY2 = (int) Math.ceil(curRenderY + curRenderH - 1.0f);
            if (sX2 > sX1 && sY2 > sY1) {
                graphics.enableScissor(sX1, sY1, sX2, sY2);
                isScissorActive = true;
            }
        }
    }

    public static void endTabScissorDirect() {
        if (isScissorActive && currentFrameExtractor != null) {
            OwoUIGraphics graphics = OwoUIGraphics.of(currentFrameExtractor);
            graphics.disableScissor();
            isScissorActive = false;
        }
    }

    private static class SlotPhysics {
        float maxSlotWidth;
        float currentWidth = 0.0f;
        float startWidth = 0.0f;
        float targetWidth = 0.0f;
        float animTimer = 1.0f;

        SlotPhysics(float maxSlotWidth) {
            this.maxSlotWidth = maxSlotWidth;
        }

        void update(boolean enabled, float dt) {
            update(enabled, this.maxSlotWidth, dt);
        }

        void update(boolean enabled, float dynamicMaxWidth, float dt) {
            float target = enabled ? dynamicMaxWidth : 0.0f;
            if (Math.abs(target - targetWidth) > 0.01f) {
                startWidth = currentWidth;
                targetWidth = target;
                animTimer = 0.0f;
            }
            if (animTimer < 1.0f) {
                animTimer = Math.min(1.0f, animTimer + dt / WIDTH_ANIM_DURATION);
                float ease = AnimationUtil.easeSmoothDecel(animTimer);
                currentWidth = Mth.lerp(ease, startWidth, targetWidth);
            } else {
                currentWidth = targetWidth;
            }
        }

        float getWidth() { return currentWidth; }
        float getRatio() { return targetWidth > 0 ? Mth.clamp(currentWidth / targetWidth, 0.0f, 1.0f) : (maxSlotWidth > 0 ? Mth.clamp(currentWidth / maxSlotWidth, 0.0f, 1.0f) : 0.0f); }
    }

    private static final SlotPhysics SLOT_FPS  = new SlotPhysics(42.0f);
    private static final SlotPhysics SLOT_PING = new SlotPhysics(44.0f);
    private static final SlotPhysics SLOT_TPS  = new SlotPhysics(48.0f);
    private static final SlotPhysics SLOT_RAM  = new SlotPhysics(82.0f);
    private static final SlotPhysics SLOT_GPU  = new SlotPhysics(42.0f);
    private static final SlotPhysics SLOT_CPU  = new SlotPhysics(42.0f);

    private static final SlotPhysics[] ALL_SLOTS = {
            SLOT_FPS, SLOT_PING, SLOT_TPS, SLOT_RAM, SLOT_GPU, SLOT_CPU
    };

    private static float islandOpenProgress = 0.0f;
    private static float currentIslandHeight = BASE_HEIGHT;
    private static float startIslandHeight = BASE_HEIGHT;
    private static float targetIslandHeight = BASE_HEIGHT;
    private static float heightAnimTimer = 1.0f;

    private static boolean isLaggingState = false;
    private static long lastFrameTime = System.currentTimeMillis();

    public static void render(GuiGraphicsExtractor extractor, float ignoredDelta) {
        currentFrameExtractor = extractor;

        IrcConfig config = IrcConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.font == null) return;

        long now = System.currentTimeMillis();
        int targetFps = IrcConfig.get().guiFpsLimit;
        long minIntervalMs = targetFps > 0 ? (1000L / targetFps) : 0;

        if (now - lastIslandThrottleTime >= minIntervalMs) {
            lastIslandThrottleTime = now;
            updateAnimationState();
            HardwareMetricsTracker.onRenderFrame(mc);
        }

        OwoUIGraphics graphics = OwoUIGraphics.of(extractor);

        long nowTime = System.currentTimeMillis();
        float dt = (nowTime - lastFrameTime) / 1000.0f;
        lastFrameTime = nowTime;
        dt = Mth.clamp(dt, 0.001f, 0.15f);

        if (tabWidthTimer < 1.0f) {
            tabWidthTimer = Math.min(1.0f, tabWidthTimer + dt / 0.35f);
            smoothTabW = Mth.lerp(AnimationUtil.easeSmoothDecel(tabWidthTimer), startTabW, targetTabW);
        } else {
            smoothTabW = targetTabW;
        }

        if (tabHeightTimer < 1.0f) {
            tabHeightTimer = Math.min(1.0f, tabHeightTimer + dt / 0.35f);
            smoothTabH = Mth.lerp(AnimationUtil.easeSmoothDecel(tabHeightTimer), startTabH, targetTabH);
        } else {
            smoothTabH = targetTabH;
        }

        float tabEase = currentTabEase;
        boolean shouldShow = shouldIslandShow(config);

        if (currentAlpha <= 0.005f) {
            return;
        }

        HardwareMetricsTracker.onRenderFrame(mc);

        var font = mc.font;
        float fontH = font.lineHeight;

        boolean showIrcPing = config.islandShowPing;
        boolean showServerPing = config.islandShowServerPing;
        boolean pingSlotEnabled = shouldShow && (showIrcPing || showServerPing);

        long ircLatency = IrcClient.getLastLatency();
        long serverLatency = (shouldShow && showServerPing) ? ServerLatencyTracker.getLatency() : -1L;

        String pingStr;
        int pingColor;

        if (showIrcPing && showServerPing) {
            String ircPart = !IrcClient.isIrcConnect ? "--(irc)" : (ircLatency < 0 ? "..(irc)" : ircLatency + "ms(irc)");
            String serverPart = serverLatency < 0 ? "--(server)" : serverLatency + "ms(server)";
            pingStr = ircPart + " " + serverPart;

            long refLatency = serverLatency >= 0 ? serverLatency : ircLatency;
            if (refLatency < 0) {
                pingColor = 0xFFB0BEC5;
            } else if (refLatency < 80) {
                pingColor = 0xFF81C784;
            } else if (refLatency < 160) {
                pingColor = 0xFFFFD54F;
            } else {
                pingColor = 0xFFE57373;
            }
        } else if (showServerPing) {
            pingStr = serverLatency < 0 ? "-- ms" : serverLatency + "ms";
            if (serverLatency < 0) {
                pingColor = 0xFFB0BEC5;
            } else if (serverLatency < 80) {
                pingColor = 0xFF81C784;
            } else if (serverLatency < 160) {
                pingColor = 0xFFFFD54F;
            } else {
                pingColor = 0xFFE57373;
            }
        } else {
            pingStr = !IrcClient.isIrcConnect ? "IRC --" : (ircLatency < 0 ? "IRC .." : ircLatency + "ms");
            pingColor = !IrcClient.isIrcConnect ? 0xFFB0BEC5 : (ircLatency >= 0 && ircLatency < 80 ? 0xFF81C784 : (ircLatency < 160 ? 0xFFFFD54F : 0xFFE57373));
        }

        Component pingComp = Component.literal(pingStr).setStyle(ISLAND_STYLE);
        float pingTargetW = font.width(pingComp) + 6.0f;

        SLOT_FPS.update(shouldShow && config.islandShowFps, dt);
        SLOT_PING.update(pingSlotEnabled, pingTargetW, dt);
        SLOT_TPS.update(shouldShow && config.islandShowTps, dt);
        SLOT_RAM.update(shouldShow && config.islandShowRam, dt);
        SLOT_GPU.update(shouldShow && config.islandShowGpu, dt);
        SLOT_CPU.update(shouldShow && config.islandShowCpu, dt);

        int fps = mc.getFps();
        float tps = TpsTracker.getTps();

        String ramText = HardwareMetricsTracker.getRamText();
        float ramRatio = HardwareMetricsTracker.getRamRatio();
        int gpuLoad = HardwareMetricsTracker.getGpuLoad();
        int cpuLoad = HardwareMetricsTracker.getCpuLoad();

        String fpsStr = "FPS " + fps;
        String tpsStr = String.format(Locale.ROOT, "%.1f TPS", tps);
        String gpuStr = "GPU " + gpuLoad + "%";
        String cpuStr = "CPU " + cpuLoad + "%";

        Component fpsComp = Component.literal(fpsStr).setStyle(ISLAND_STYLE);
        Component tpsComp = Component.literal(tpsStr).setStyle(ISLAND_STYLE);
        Component ramComp = Component.literal(ramText).setStyle(ISLAND_STYLE);
        Component gpuComp = Component.literal(gpuStr).setStyle(ISLAND_STYLE);
        Component cpuComp = Component.literal(cpuStr).setStyle(ISLAND_STYLE);

        Component[] comps = { fpsComp, pingComp, tpsComp, ramComp, gpuComp, cpuComp };

        int fpsColor = fps > 160 ? 0xFFBA68C8 : (fps >= 90 ? 0xFF4DD0E1 : (fps >= 60 ? 0xFF81C784 : (fps >= 30 ? 0xFFFFD54F : 0xFFE57373)));
        int tpsColor = tps >= 18.5f ? 0xFF81C784 : (tps >= 15.0f ? 0xFFFFD54F : 0xFFE57373);
        int ramColor = ramRatio > 0.85f ? 0xFFE57373 : (ramRatio > 0.70f ? 0xFFFFD54F : 0xFFB2DFDB);
        int gpuColor = gpuLoad > 85 ? 0xFFE57373 : (gpuLoad > 65 ? 0xFFFFD54F : 0xFF80CBC4);
        int cpuColor = cpuLoad > 85 ? 0xFFE57373 : (cpuLoad > 60 ? 0xFFFFD54F : 0xFF81C784);

        int[] colors = { fpsColor, pingColor, tpsColor, ramColor, gpuColor, cpuColor };

        float dynamicContentW = computeContentWidth();

        // 动态计算目标高度（多行扩展内容）
        boolean isLowTps = checkIsLowTps(mc, config);
        List<IslandNoticeEntry> activeLines = getActiveExtraLines(isLowTps);

        float targetH = BASE_HEIGHT + activeLines.size() * EXTRA_ROW_HEIGHT;
        if (Math.abs(targetH - targetIslandHeight) > 0.1f) {
            startIslandHeight = currentIslandHeight;
            targetIslandHeight = targetH;
            heightAnimTimer = 0.0f;
        }
        if (heightAnimTimer < 1.0f) {
            heightAnimTimer = Math.min(1.0f, heightAnimTimer + dt / HEIGHT_ANIM_DURATION);
            currentIslandHeight = Mth.lerp(AnimationUtil.easeSmoothDecel(heightAnimTimer), startIslandHeight, targetIslandHeight);
        } else {
            currentIslandHeight = targetIslandHeight;
        }

        float extraHeight = Math.max(0.0f, currentIslandHeight - BASE_HEIGHT);
        float expandRatio = targetIslandHeight > BASE_HEIGHT
                ? Mth.clamp(extraHeight / (targetIslandHeight - BASE_HEIGHT), 0.0f, 1.0f)
                : (extraHeight > 0.0f ? Mth.clamp(extraHeight / EXTRA_ROW_HEIGHT, 0.0f, 1.0f) : 0.0f);

        updateRenderRect(tabEase, dt, config);

        float cx = curRenderX + curRenderW / 2.0f;
        boolean islandPillVisible = tabProgress <= 0.005f && (DynamicIslandRenderer.isEnabled() || editorPreview);

        if (islandPillVisible) {
            if (isShadowEnabled()) {
                RenderUtil.drawSoftDropShadow(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius,
                        shadowBlur, shadowOffsetY, shadowBaseAlpha, currentAlpha);
            }
            RenderUtil.drawSubpixelRoundedPanel(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius, curBgColor, curBorderColor);
        }

        int scissorX1 = (int) Math.floor(curRenderX + 1.0f);
        int scissorY1 = (int) Math.floor(curRenderY + 1.0f);
        int scissorX2 = (int) Math.ceil(curRenderX + curRenderW - 1.0f);
        int scissorY2 = (int) Math.ceil(curRenderY + curRenderH - 1.0f);

        if (scissorX2 <= scissorX1 || scissorY2 <= scissorY1) {
            return;
        }

        graphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);

        float islandContentAlpha = currentAlpha * (1.0f - tabEase);
        if (islandPillVisible && islandContentAlpha > 0.02f) {
            // 第一行：渲染主要性能槽位
            float row1Y = curRenderY + (BASE_HEIGHT - fontH) / 2.0f;
            float curX = cx - dynamicContentW / 2.0f;

            if (editorPreview && dynamicContentW <= 0.001f) {
                Component placeholder = Component.literal("KWRG").setStyle(ISLAND_STYLE);
                float placeholderW = font.width(placeholder);
                int placeholderAlpha = (int) (255 * islandContentAlpha);
                RenderUtil.drawFluidSubpixelText(graphics, placeholder, cx - placeholderW / 2.0f, row1Y,
                        (0xFFE0E0E0 & 0x00FFFFFF) | (placeholderAlpha << 24));
            }

            SlotPhysics lastVisible = null;
            for (int i = 0; i < ALL_SLOTS.length; i++) {
                SlotPhysics slot = ALL_SLOTS[i];
                float w = slot.getWidth();

                if (w > 0.001f) {
                    if (lastVisible != null) {
                        float dynamicGap = ITEM_GAP * Math.min(1.0f, lastVisible.getRatio()) * Math.min(1.0f, slot.getRatio());
                        curX += dynamicGap;
                    }

                    Component comp = comps[i];
                    int baseColor = colors[i];
                    float tw = font.width(comp);
                    float tx = curX + (w - tw) / 2.0f;

                    float slotRatio = slot.getRatio();
                    float textFade = Mth.clamp(w / Math.max(1.0f, tw), 0.0f, 1.0f);
                    int itemAlpha = (int) (255 * islandContentAlpha * slotRatio * textFade);

                    if (itemAlpha > 2) {
                        int finalColor = (baseColor & 0x00FFFFFF) | (itemAlpha << 24);
                        RenderUtil.drawFluidSubpixelText(graphics, comp, tx, row1Y, finalColor);
                    }

                    curX += w;
                    lastVisible = slot;
                }
            }

            // 绘制扩展行：自定义文本在前，WARN_COMPONENT（若有）始终在最后一行
            if (!activeLines.isEmpty() && currentIslandHeight > BASE_HEIGHT + 1.0f) {
                float slideY = Mth.lerp(expandRatio, -4.0f, 0.0f);

                for (int i = 0; i < activeLines.size(); i++) {
                    IslandNoticeEntry entry = activeLines.get(i);

                    float rowBaseY = BASE_HEIGHT + i * EXTRA_ROW_HEIGHT;
                    float rowProgress = Mth.clamp((currentIslandHeight - rowBaseY) / EXTRA_ROW_HEIGHT, 0.0f, 1.0f);
                    if (rowProgress <= 0.01f) continue;

                    float lineCenterY = curRenderY + rowBaseY + (EXTRA_ROW_HEIGHT - fontH) / 2.0f;
                    float textW = font.width(entry.component);
                    float lineX = cx - textW / 2.0f;

                    int alpha = (int) (255 * islandContentAlpha * rowProgress);
                    if (alpha <= 2) continue;

                    int color;
                    if (entry.isWarning) {
                        color = ARGB.color(alpha, 255, 95, 60);
                    } else {
                        color = (entry.color & 0x00FFFFFF) | (alpha << 24);
                    }

                    RenderUtil.drawFluidSubpixelText(graphics, entry.component, lineX, lineCenterY + slideY, color);
                }
            }
        }

        graphics.disableScissor();
    }
}