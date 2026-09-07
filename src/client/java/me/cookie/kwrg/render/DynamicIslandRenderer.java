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

import java.util.Locale;

public class DynamicIslandRenderer {

    private static final FontDescription ISLAND_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("kwrg", "island"));
    private static final Style ISLAND_STYLE = Style.EMPTY.withFont(ISLAND_FONT);

    private static final float BASE_HEIGHT = 20.0f;
    private static final float EXPANDED_HEIGHT = 36.0f;
    private static final float CORNER_RADIUS = 10.0f;

    private static final float WIDTH_ANIM_DURATION = 0.85f;
    private static final float HEIGHT_ANIM_DURATION = 0.45f;

    private static final float ITEM_GAP = 5.0f;
    private static final float SIDE_PAD = 12.0f;

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

        float getWidth() {
            return currentWidth;
        }

        float getRatio() {
            return maxSlotWidth > 0 ? Mth.clamp(currentWidth / maxSlotWidth, 0.0f, 1.0f) : 0.0f;
        }
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

    // 状态跟踪
    private static float islandOpenProgress = 0.0f;

    private static float currentIslandHeight = BASE_HEIGHT;
    private static float startIslandHeight = BASE_HEIGHT;
    private static float targetIslandHeight = BASE_HEIGHT;
    private static float heightAnimTimer = 1.0f;

    private static boolean isLaggingState = false;
    private static long lastFrameTime = System.currentTimeMillis();

    public static void render(GuiGraphicsExtractor extractor, float ignoredDelta) {
        IrcConfig config = IrcConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.font == null) return;

        OwoUIGraphics graphics = OwoUIGraphics.of(extractor);

        // 1. 真实物理时间步进
        long nowTime = System.currentTimeMillis();
        float dt = (nowTime - lastFrameTime) / 1000.0f;
        lastFrameTime = nowTime;
        dt = Mth.clamp(dt, 0.001f, 0.04f);

        HardwareMetricsTracker.onRenderFrame(mc);

        boolean hasAnyOption = config.islandShowFps || config.islandShowPing || config.islandShowTps
                || config.islandShowRam || config.islandShowGpu || config.islandShowCpu;
        boolean shouldShow = config.dynamicIsland && hasAnyOption;

        // 开关进退场
        float targetOpen = shouldShow ? 1.0f : 0.0f;
        islandOpenProgress = Mth.clamp(islandOpenProgress + (targetOpen - islandOpenProgress) * dt * 6.0f, 0.0f, 1.0f);
        float openEase = easeSmoothDecel(islandOpenProgress);

        if (islandOpenProgress <= 0.005f) {
            return;
        }

        // 2. 推进所有槽位宽度动画
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

        // 3. 计算连续内容总宽
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

        // 4. 施密特滞后触发 TPS 警告高度形变
        if (tps < 15.5f) {
            isLaggingState = true;
        } else if (tps > 16.5f) {
            isLaggingState = false;
        }
        boolean isLowTps = config.islandShowTps && isLaggingState && mc.level != null;

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

        // 5. ★ 绝对坐标精确定位（彻底杜绝 2px 偶数跳动，恢复 1 像素级平滑连续拉伸） ★
        float warnTextW = font.width(warnComp);
        float normalTargetWidth = dynamicContentW + SIDE_PAD * 2;
        float targetWidth = Math.max(38.0f, Mth.lerp(warnEase, normalTargetWidth, Math.max(normalTargetWidth, warnTextW + 18.0f)));
        float renderWidth = targetWidth * (0.85f + 0.15f * openEase);
        float renderHeight = currentIslandHeight;

        int screenW = mc.getWindow().getGuiScaledWidth();
        float cx = screenW / 2.0f;
        float topY = 7.0f * openEase;
        float alpha = openEase;

        // 采用浮点计算左右边界，四舍五入到整像素（允许奇数宽度，告别偶数翻倍卡顿）
        int panelX1 = (int) Math.round(cx - renderWidth / 2.0f);
        int panelY1 = (int) Math.round(topY);
        int panelX2 = (int) Math.round(cx + renderWidth / 2.0f);
        int panelY2 = (int) Math.round(topY + renderHeight);

        int actualWidth = panelX2 - panelX1;
        int actualHeight = panelY2 - panelY1;

        int borderR = (int) Mth.lerp(warnEase, 84f, 255f);
        int borderG = (int) Mth.lerp(warnEase, 152f, 100f);
        int borderB = (int) Mth.lerp(warnEase, 178f, 90f);

        Color bgColor = Color.ofArgb(ARGB.color((int) (230 * alpha), 17, 22, 31));
        Color borderColor = Color.ofArgb(ARGB.color((int) (210 * alpha), borderR, borderG, borderB));

        // 6. ★ 绘制底盘外壳（屏幕绝对坐标系，严丝合缝） ★
        RenderUtil.drawRoundedPanel(graphics, panelX1, panelY1, actualWidth, actualHeight, CORNER_RADIUS, bgColor, borderColor);

        // 7. ★ 修复视口裁剪（在未位移前调用，确保文字绝不被甩出视口！） ★
        int scissorX1 = panelX1 + 1;
        int scissorY1 = panelY1 + 1;
        int scissorX2 = panelX2 - 1;
        int scissorY2 = panelY2 - 1;

        if (scissorX2 <= scissorX1 || scissorY2 <= scissorY1) {
            return;
        }

        graphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);

        // 8. ★ 第一行文字：绝对居中排布（文字在各自槽位内水平对齐，单项时 100% 死锁屏幕正中） ★
        float row1Y = panelY1 + (BASE_HEIGHT - fontH) / 2.0f;
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

                // 槽位对称居中，单项时左右完全抵消，文字严格锁定在屏幕正中央
                float tx = curX + (w - tw) / 2.0f;

                float slotRatio = slot.getRatio();
                float textFade = Mth.clamp(w / Math.max(1.0f, tw), 0.0f, 1.0f);
                int itemAlpha = (int) (255 * alpha * slotRatio * textFade);

                if (itemAlpha > 2) {
                    int finalColor = (baseColor & 0x00FFFFFF) | (itemAlpha << 24);
                    drawFluidSubpixelText(graphics, comp, tx, row1Y, finalColor);
                }

                curX += w;
                lastVisible = slot;
            }
        }

        // 9. 第二行：Lag 警告信息（在扩展出来的下层空间完全居中）
        if (warnEase > 0.02f) {
            float secondRowY = panelY1 + BASE_HEIGHT + (EXPANDED_HEIGHT - BASE_HEIGHT - fontH) / 2.0f;
            float slideY = Mth.lerp(warnEase, -5.0f, 0.0f);

            int warnAlpha = (int) (255 * alpha * warnEase);
            int warnColor = ARGB.color(warnAlpha, 255, 95, 60);

            float actualW = font.width(warnComp);
            float warnX = cx - actualW / 2.0f;

            drawFluidSubpixelText(graphics, warnComp, warnX, secondRowY + slideY, warnColor);
        }

        graphics.disableScissor();
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