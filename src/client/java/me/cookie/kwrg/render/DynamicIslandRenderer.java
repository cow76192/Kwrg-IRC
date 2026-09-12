package me.cookie.kwrg.render;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.HardwareMetricsTracker;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.gui.util.TpsTracker;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.lang.reflect.Method;
import java.util.Locale;

public class DynamicIslandRenderer {

    private static final FontDescription ISLAND_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("kwrg", "island"));
    private static final Style ISLAND_STYLE = Style.EMPTY.withFont(ISLAND_FONT);

    private static final float BASE_HEIGHT = 20.0f;
    private static final float EXPANDED_HEIGHT = 36.0f;
    private static final float CORNER_RADIUS = 10.0f;

    private static final float WIDTH_ANIM_DURATION = 0.85f;
    private static final float HEIGHT_ANIM_DURATION = 0.45f;
    private static final float TAB_ANIM_DURATION = 0.38f;

    private static final float ITEM_GAP = 5.0f;
    private static final float SIDE_PAD = 12.0f;

    private static boolean shadowEnabled = true;
    private static float shadowBlur = 4.0f;
    private static float shadowOffsetY = 1.2f;
    private static int shadowBaseAlpha = 50;

    public static void setShadowEnabled(boolean enabled) {
        shadowEnabled = enabled;
    }

    public static boolean isShadowEnabled() {
        return shadowEnabled;
    }

    public static void setShadowBlur(float blur) {
        shadowBlur = Math.max(0.0f, blur);
    }

    public static float getShadowBlur() {
        return shadowBlur;
    }

    public static void setShadowOffsetY(float offsetY) {
        shadowOffsetY = offsetY;
    }

    public static float getShadowOffsetY() {
        return shadowOffsetY;
    }

    public static void setShadowBaseAlpha(int baseAlpha) {
        shadowBaseAlpha = Mth.clamp(baseAlpha, 0, 255);
    }

    public static int getShadowBaseAlpha() {
        return shadowBaseAlpha;
    }

    public static void resetShadowSettings() {
        shadowEnabled = true;
        shadowBlur = 4.0f;
        shadowOffsetY = 1.2f;
        shadowBaseAlpha = 55;
    }

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

    public static void setSkyHanniInstance(Object instance) {
        skyHanniInstance = instance;
    }

    public static void markSkyHanniTabRendered() {
        wasSkyHanniInCurrentSession = true;
    }

    public static boolean wasSkyHanniActive() {
        return wasSkyHanniInCurrentSession;
    }

    public static void renderSkyHanniTab(GuiGraphicsExtractor graphics) {
        if (!IrcConfig.get().dynamicIsland) return;
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
        if (!IrcConfig.get().dynamicIsland) return;
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
        // 如果未开启灵动岛，Tab 进度始终为 0，防止外部 Mixin 接管
        if (!IrcConfig.get().dynamicIsland) return 0.0f;
        return tabProgress;
    }

    public static boolean isTabAnimating() {
        if (!IrcConfig.get().dynamicIsland) return false;
        return tabProgress > 0.005f;
    }

    public static void setCurrentExtractor(GuiGraphicsExtractor extractor) {
        currentFrameExtractor = extractor;
    }

    public static void renderTabBackgroundDirect() {
        if (!IrcConfig.get().dynamicIsland) return;
        if (currentFrameExtractor != null && tabProgress > 0.005f && curRenderW > 0) {
            OwoUIGraphics graphics = OwoUIGraphics.of(currentFrameExtractor);
            if (shadowEnabled) {
                drawSoftDropShadow(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius, shadowBlur * 1.5f, shadowOffsetY * 1.5f, islandOpenProgress);
            }
            drawSubpixelRoundedPanel(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius, curBgColor, curBorderColor);
        }
    }

    public static void renderTabBackground(GuiGraphicsExtractor extractor) {
        if (!IrcConfig.get().dynamicIsland) return;
        setCurrentExtractor(extractor);
        renderTabBackgroundDirect();
    }

    public static void beginTabScissor(GuiGraphicsExtractor extractor) {
        if (!IrcConfig.get().dynamicIsland) return;
        setCurrentExtractor(extractor);
        beginTabScissorDirect();
    }

    public static void endTabScissor(GuiGraphicsExtractor extractor) {
        endTabScissorDirect();
    }

    public static void beginTabScissorDirect() {
        if (!IrcConfig.get().dynamicIsland) return;
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
        final float maxSlotWidth;
        float currentWidth = 0.0f;
        float startWidth = 0.0f;
        float targetWidth = 0.0f;
        float animTimer = 1.0f;

        SlotPhysics(float maxSlotWidth) {
            this.maxSlotWidth = maxSlotWidth;
        }

        void update(boolean enabled, float dt) {
            float target = enabled ? maxSlotWidth : 0.0f;
            if (Math.abs(target - targetWidth) > 0.01f) {
                startWidth = currentWidth;
                targetWidth = target;
                animTimer = 0.0f;
            }
            if (animTimer < 1.0f) {
                animTimer = Math.min(1.0f, animTimer + dt / WIDTH_ANIM_DURATION);
                float ease = easeSmoothDecel(animTimer);
                currentWidth = Mth.lerp(ease, startWidth, targetWidth);
            } else {
                currentWidth = targetWidth;
            }
        }

        float getWidth() { return currentWidth; }
        float getRatio() { return maxSlotWidth > 0 ? Mth.clamp(currentWidth / maxSlotWidth, 0.0f, 1.0f) : 0.0f; }
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

        OwoUIGraphics graphics = OwoUIGraphics.of(extractor);

        long nowTime = System.currentTimeMillis();
        float dt = (nowTime - lastFrameTime) / 1000.0f;
        lastFrameTime = nowTime;
        dt = Mth.clamp(dt, 0.001f, 0.04f);

        if (tabWidthTimer < 1.0f) {
            tabWidthTimer = Math.min(1.0f, tabWidthTimer + dt / 0.35f);
            smoothTabW = Mth.lerp(easeSmoothDecel(tabWidthTimer), startTabW, targetTabW);
        } else {
            smoothTabW = targetTabW;
        }

        if (tabHeightTimer < 1.0f) {
            tabHeightTimer = Math.min(1.0f, tabHeightTimer + dt / 0.35f);
            smoothTabH = Mth.lerp(easeSmoothDecel(tabHeightTimer), startTabH, targetTabH);
        } else {
            smoothTabH = targetTabH;
        }

        smoothTabY = Mth.lerp(dt * 10.0f, smoothTabY, targetTabY);

        // 1. 只有当开启灵动岛时，按 Tab 键才会让 targetTab = 1.0；关闭时始终为 0（不接管 Tab）
        boolean isTabKeyDown = config.dynamicIsland && mc.options.keyPlayerList.isDown() && mc.player != null;
        float targetTab = isTabKeyDown ? 1.0f : 0.0f;
        float tabSpeed = isTabKeyDown ? (1.0f / TAB_ANIM_DURATION) : (1.0f / (TAB_ANIM_DURATION * 0.85f));

        if (tabProgress < targetTab) {
            tabProgress = Math.min(targetTab, tabProgress + dt * tabSpeed);
        } else if (tabProgress > targetTab) {
            tabProgress = Math.max(targetTab, tabProgress - dt * tabSpeed);
        }
        float tabEase = easeSmoothDecel(tabProgress);

        if (tabProgress <= 0.005f) {
            wasSkyHanniInCurrentSession = false;
        }

        // 2. 只有在 dynamicIsland == true 时，shouldShow 才能为 true；关闭时为 false，驱动收起动画
        boolean hasAnyOption = config.islandShowFps || config.islandShowPing || config.islandShowTps
                || config.islandShowRam || config.islandShowGpu || config.islandShowCpu;
        boolean shouldShow = config.dynamicIsland && (hasAnyOption || tabProgress > 0.005f);

        // 3. 平滑过渡展开 / 收起（开启与关闭时的动画都在这里持续计算）
        float targetOpen = shouldShow ? 1.0f : 0.0f;
        islandOpenProgress = Mth.clamp(islandOpenProgress + (targetOpen - islandOpenProgress) * dt * 6.0f, 0.0f, 1.0f);
        float openEase = easeSmoothDecel(islandOpenProgress);

        // 4. 当收起动画彻底结束时才 return，绝不提前硬截断
        if (islandOpenProgress <= 0.005f) {
            return;
        }

        HardwareMetricsTracker.onRenderFrame(mc);

        SLOT_FPS.update(shouldShow && config.islandShowFps, dt);
        SLOT_PING.update(shouldShow && config.islandShowPing, dt);
        SLOT_TPS.update(shouldShow && config.islandShowTps, dt);
        SLOT_RAM.update(shouldShow && config.islandShowRam, dt);
        SLOT_GPU.update(shouldShow && config.islandShowGpu, dt);
        SLOT_CPU.update(shouldShow && config.islandShowCpu, dt);

        int fps = mc.getFps();
        long latency = IrcClient.getLastLatency();
        float tps = TpsTracker.getTps();

        String ramText = HardwareMetricsTracker.getRamText();
        float ramRatio = HardwareMetricsTracker.getRamRatio();
        int gpuLoad = HardwareMetricsTracker.getGpuLoad();
        int cpuLoad = HardwareMetricsTracker.getCpuLoad();

        var font = mc.font;
        float fontH = font.lineHeight;

        String fpsStr = "FPS " + fps;
        String pingStr = !IrcClient.isIrcConnect ? "IRC --" : (latency < 0 ? "IRC .." : latency + "ms");
        String tpsStr = String.format(Locale.ROOT, "%.1f TPS", tps);
        String gpuStr = "GPU " + gpuLoad + "%";
        String cpuStr = "CPU " + cpuLoad + "%";
        String warnStr = "⚠ Server Lagging (Low TPS)";

        Component fpsComp = Component.literal(fpsStr).setStyle(ISLAND_STYLE);
        Component pingComp = Component.literal(pingStr).setStyle(ISLAND_STYLE);
        Component tpsComp = Component.literal(tpsStr).setStyle(ISLAND_STYLE);
        Component ramComp = Component.literal(ramText).setStyle(ISLAND_STYLE);
        Component gpuComp = Component.literal(gpuStr).setStyle(ISLAND_STYLE);
        Component cpuComp = Component.literal(cpuStr).setStyle(ISLAND_STYLE);
        Component warnComp = Component.literal(warnStr).setStyle(ISLAND_STYLE);

        Component[] comps = { fpsComp, pingComp, tpsComp, ramComp, gpuComp, cpuComp };

        int fpsColor = fps > 160 ? 0xFFBA68C8 : (fps >= 90 ? 0xFF4DD0E1 : (fps >= 60 ? 0xFF81C784 : (fps >= 30 ? 0xFFFFD54F : 0xFFE57373)));
        int pingColor = !IrcClient.isIrcConnect ? 0xFFB0BEC5 : (latency >= 0 && latency < 80 ? 0xFF81C784 : (latency < 160 ? 0xFFFFD54F : 0xFFE57373));
        int tpsColor = tps >= 18.5f ? 0xFF81C784 : (tps >= 15.0f ? 0xFFFFD54F : 0xFFE57373);
        int ramColor = ramRatio > 0.85f ? 0xFFE57373 : (ramRatio > 0.70f ? 0xFFFFD54F : 0xFFB2DFDB);
        int gpuColor = gpuLoad > 85 ? 0xFFE57373 : (gpuLoad > 65 ? 0xFFFFD54F : 0xFF80CBC4);
        int cpuColor = cpuLoad > 85 ? 0xFFE57373 : (cpuLoad > 60 ? 0xFFFFD54F : 0xFF81C784);

        int[] colors = { fpsColor, pingColor, tpsColor, ramColor, gpuColor, cpuColor };

        float dynamicContentW = 0.0f;
        SlotPhysics lastVisible = null;
        for (SlotPhysics slot : ALL_SLOTS) {
            float w = slot.getWidth();
            if (w > 0.001f) {
                if (lastVisible != null) {
                    float dynamicGap = ITEM_GAP * Math.min(1.0f, lastVisible.getRatio()) * Math.min(1.0f, slot.getRatio());
                    dynamicContentW += dynamicGap;
                }
                dynamicContentW += w;
                lastVisible = slot;
            }
        }

        if (tps < 15.5f) isLaggingState = true;
        else if (tps > 16.5f) isLaggingState = false;
        boolean isLowTps = config.islandShowTps && isLaggingState && mc.level != null && tabProgress <= 0.01f;

        float targetH = isLowTps ? EXPANDED_HEIGHT : BASE_HEIGHT;
        if (Math.abs(targetH - targetIslandHeight) > 0.1f) {
            startIslandHeight = currentIslandHeight;
            targetIslandHeight = targetH;
            heightAnimTimer = 0.0f;
        }
        if (heightAnimTimer < 1.0f) {
            heightAnimTimer = Math.min(1.0f, heightAnimTimer + dt / HEIGHT_ANIM_DURATION);
            currentIslandHeight = Mth.lerp(easeSmoothDecel(heightAnimTimer), startIslandHeight, targetIslandHeight);
        } else {
            currentIslandHeight = targetIslandHeight;
        }

        float warnEase = Mth.clamp((currentIslandHeight - BASE_HEIGHT) / (EXPANDED_HEIGHT - BASE_HEIGHT), 0.0f, 1.0f);

        int screenW = mc.getWindow().getGuiScaledWidth();
        float cx = screenW / 2.0f;

        float warnTextW = font.width(warnComp);
        float normalTargetWidth = dynamicContentW + SIDE_PAD * 2;
        float baseIslandW = Math.max(38.0f, Mth.lerp(warnEase, normalTargetWidth, Math.max(normalTargetWidth, warnTextW + 18.0f)));
        float baseIslandH = currentIslandHeight;
        float baseIslandTopY = 7.0f * openEase;

        curRenderW = Mth.lerp(tabEase, baseIslandW, smoothTabW) * (0.85f + 0.15f * openEase);
        curRenderH = Mth.lerp(tabEase, baseIslandH, smoothTabH);
        curRenderY = Mth.lerp(tabEase, baseIslandTopY, smoothTabY);
        curRenderX = cx - curRenderW / 2.0f;
        curCornerRadius = Mth.lerp(tabEase, CORNER_RADIUS, 14.0f);

        float alpha = openEase;

        int borderR = (int) Mth.lerp(warnEase, 84f, 255f);
        int borderG = (int) Mth.lerp(warnEase, 152f, 100f);
        int borderB = (int) Mth.lerp(warnEase, 178f, 90f);

        int bgAlpha = (int) (Mth.lerp(tabEase, 230f, 245f) * alpha);
        curBgColor = Color.ofArgb(ARGB.color(bgAlpha, 17, 22, 31));
        curBorderColor = Color.ofArgb(ARGB.color((int) (210 * alpha), borderR, borderG, borderB));

        if (tabProgress <= 0.005f) {
            if (shadowEnabled) {
                drawSoftDropShadow(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius, shadowBlur, shadowOffsetY, alpha);
            }
            drawSubpixelRoundedPanel(graphics, curRenderX, curRenderY, curRenderW, curRenderH, curCornerRadius, curBgColor, curBorderColor);
        }

        int scissorX1 = (int) Math.floor(curRenderX + 1.0f);
        int scissorY1 = (int) Math.floor(curRenderY + 1.0f);
        int scissorX2 = (int) Math.ceil(curRenderX + curRenderW - 1.0f);
        int scissorY2 = (int) Math.ceil(curRenderY + curRenderH - 1.0f);

        if (scissorX2 <= scissorX1 || scissorY2 <= scissorY1) {
            return;
        }

        graphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);

        float islandContentAlpha = alpha * (1.0f - tabEase);
        if (islandContentAlpha > 0.02f) {
            float row1Y = curRenderY + (BASE_HEIGHT - fontH) / 2.0f;
            float curX = cx - dynamicContentW / 2.0f;

            lastVisible = null;
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
                        drawFluidSubpixelText(graphics, comp, tx, row1Y, finalColor);
                    }

                    curX += w;
                    lastVisible = slot;
                }
            }

            if (warnEase > 0.02f) {
                float secondRowY = curRenderY + BASE_HEIGHT + (EXPANDED_HEIGHT - BASE_HEIGHT - fontH) / 2.0f;
                float slideY = Mth.lerp(warnEase, -5.0f, 0.0f);

                int warnAlpha = (int) (255 * islandContentAlpha * warnEase);
                int warnColor = ARGB.color(warnAlpha, 255, 95, 60);

                float actualW = font.width(warnComp);
                float warnX = cx - actualW / 2.0f;

                drawFluidSubpixelText(graphics, warnComp, warnX, secondRowY + slideY, warnColor);
            }
        }

        graphics.disableScissor();
    }

    private static void drawSoftDropShadow(OwoUIGraphics graphics, float x, float y, float width, float height, float radius, float blur, float offsetY, float alpha) {
        if (alpha <= 0.005f || blur <= 0.1f) return;

        int maxA = (int) (shadowBaseAlpha * alpha);
        if (maxA <= 0) return;

        int innerRgb = ARGB.color(maxA, 0, 0, 0);
        int outerRgb = ARGB.color(0, 0, 0, 0);
        Color innerCol = Color.ofArgb(innerRgb);
        Color outerCol = Color.ofArgb(outerRgb);

        float r = Math.min(radius, Math.min(width / 2.0f, height / 2.0f));
        float sy = y + offsetY;

        drawSubpixelRoundedCore(graphics, x, sy, width, height, r, innerCol);

        drawQuadGradient(graphics, x + r, sy - blur, x + width - r, sy, outerRgb, outerRgb, innerRgb, innerRgb);
        drawQuadGradient(graphics, x + r, sy + height, x + width - r, sy + height + blur, innerRgb, innerRgb, outerRgb, outerRgb);

        if (height > 2.0f * r) {
            drawQuadGradient(graphics, x - blur, sy + r, x, sy + height - r, outerRgb, innerRgb, innerRgb, outerRgb);
            drawQuadGradient(graphics, x + width, sy + r, x + width + blur, sy + height - r, innerRgb, outerRgb, outerRgb, innerRgb);
        }

        drawSubpixelRingGradient(graphics, x + r, sy + r, 0.0, 90.0, 16, r, r + blur, innerCol, outerCol);
        drawSubpixelRingGradient(graphics, x + width - r, sy + r, 90.0, 180.0, 16, r, r + blur, innerCol, outerCol);
        drawSubpixelRingGradient(graphics, x + width - r, sy + height - r, 180.0, 270.0, 16, r, r + blur, innerCol, outerCol);
        drawSubpixelRingGradient(graphics, x + r, sy + height - r, 270.0, 360.0, 16, r, r + blur, innerCol, outerCol);
    }

    private static void drawSubpixelRoundedCore(OwoUIGraphics graphics, float x, float y, float width, float height, float r, Color color) {
        float x2 = x + width;
        float y2 = y + height;

        fillSubpixel(graphics, x + r, y, x2 - r, y2, color.argb());
        fillSubpixel(graphics, x, y + r, x + r, y2 - r, color.argb());
        fillSubpixel(graphics, x2 - r, y + r, x2, y2 - r, color.argb());

        drawSubpixelCircle(graphics, x + r, y + r, 0.0, 90.0, 16, r, color);
        drawSubpixelCircle(graphics, x2 - r, y + r, 90.0, 180.0, 16, r, color);
        drawSubpixelCircle(graphics, x2 - r, y2 - r, 180.0, 270.0, 16, r, color);
        drawSubpixelCircle(graphics, x + r, y2 - r, 270.0, 360.0, 16, r, color);
    }

    private static void drawSubpixelRingGradient(OwoUIGraphics graphics, float cx, float cy, double angleFrom, double angleTo, int segments, double innerRadius, double outerRadius, Color innerColor, Color outerColor) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawRing(0, 0, angleFrom, angleTo, segments, innerRadius, outerRadius, innerColor, outerColor);
        graphics.pose().popMatrix();
    }

    private static void drawQuadGradient(OwoUIGraphics graphics, float x1, float y1, float x2, float y2, int cTL, int cTR, int cBR, int cBL) {
        if (x2 <= x1 || y2 <= y1) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x1, y1);
        graphics.pose().scale(x2 - x1, y2 - y1);
        graphics.drawGradientRect(0, 0, 1, 1, cTL, cTR, cBR, cBL);
        graphics.pose().popMatrix();
    }

    private static void drawSubpixelRoundedPanel(OwoUIGraphics graphics, float x, float y, float width, float height, float radius, Color bgColor, Color outlineColor) {
        float r = Math.min(radius, Math.min(width / 2.0f, height / 2.0f));
        float x2 = x + width;
        float y2 = y + height;

        fillSubpixel(graphics, x + r, y, x2 - r, y2, bgColor.argb());
        fillSubpixel(graphics, x, y + r, x + r, y2 - r, bgColor.argb());
        fillSubpixel(graphics, x2 - r, y + r, x2, y2 - r, bgColor.argb());

        drawSubpixelCircle(graphics, x + r, y + r, 0.0, 90.0, 16, r, bgColor);
        drawSubpixelCircle(graphics, x2 - r, y + r, 90.0, 180.0, 16, r, bgColor);
        drawSubpixelCircle(graphics, x2 - r, y2 - r, 180.0, 270.0, 16, r, bgColor);
        drawSubpixelCircle(graphics, x + r, y2 - r, 270.0, 360.0, 16, r, bgColor);

        fillSubpixel(graphics, x + r, y, x2 - r, y + 1.0f, outlineColor.argb());
        fillSubpixel(graphics, x + r, y2 - 1.0f, x2 - r, y2, outlineColor.argb());
        fillSubpixel(graphics, x, y + r, x + 1.0f, y2 - r, outlineColor.argb());
        fillSubpixel(graphics, x2 - 1.0f, y + r, x2, y2 - r, outlineColor.argb());

        drawSubpixelRing(graphics, x + r, y + r, 0.0, 90.0, 16, r - 1.0f, r, outlineColor, outlineColor);
        drawSubpixelRing(graphics, x2 - r, y + r, 90.0, 180.0, 16, r - 1.0f, r, outlineColor, outlineColor);
        drawSubpixelRing(graphics, x2 - r, y2 - r, 180.0, 270.0, 16, r - 1.0f, r, outlineColor, outlineColor);
        drawSubpixelRing(graphics, x + r, y2 - r, 270.0, 360.0, 16, r - 1.0f, r, outlineColor, outlineColor);
    }

    private static void drawSubpixelCircle(OwoUIGraphics graphics, float cx, float cy, double angleFrom, double angleTo, int segments, double radius, Color color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawCircle(0, 0, angleFrom, angleTo, segments, radius, color);
        graphics.pose().popMatrix();
    }

    private static void drawSubpixelRing(OwoUIGraphics graphics, float cx, float cy, double angleFrom, double angleTo, int segments, double innerRadius, double outerRadius, Color innerColor, Color outerColor) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawRing(0, 0, angleFrom, angleTo, segments, innerRadius, outerRadius, innerColor, outerColor);
        graphics.pose().popMatrix();
    }

    private static void fillSubpixel(OwoUIGraphics graphics, float minX, float minY, float maxX, float maxY, int color) {
        if (maxX <= minX || maxY <= minY) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(minX, minY);
        graphics.pose().scale(maxX - minX, maxY - minY);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popMatrix();
    }

    private static float easeSmoothDecel(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - p, 4.0f);
    }

    private static void drawFluidSubpixelText(OwoUIGraphics graphics, Component text, float x, float y, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.drawText(text, 0, 0, 1.0f, color, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.pose().popMatrix();
    }
}