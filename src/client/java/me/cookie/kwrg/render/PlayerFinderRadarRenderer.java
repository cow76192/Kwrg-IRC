package me.cookie.kwrg.render;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PlayerFinderRadarRenderer {

    public static void render(GuiGraphicsExtractor graphics, float delta) {
        render(OwoUIGraphics.of(graphics), delta);
    }

    public static void render(OwoUIGraphics graphics, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (PlayerFinderManager.getPlayers().isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        var window = mc.getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();

        float yawRad = (float) Math.toRadians(camera.yRot());
        float pitchRad = (float) Math.toRadians(camera.xRot());
        Vec3 forward = new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
        );

        float bob = (float) Math.sin((System.currentTimeMillis() % 1600L) / 1600.0 * Math.PI * 2.0) * 2.5f;

        for (Player target : mc.level.players()) {
            if (!PlayerFinderManager.isTarget(target)) continue;

            double tx = Mth.lerp(delta, target.xo, target.getX());
            double ty = Mth.lerp(delta, target.yo, target.getY()) + target.getBbHeight() + 0.45;
            double tz = Mth.lerp(delta, target.zo, target.getZ());
            Vec3 targetWorldPos = new Vec3(tx, ty, tz);

            Vec3 toTarget = targetWorldPos.subtract(camPos);
            if (toTarget.dot(forward) <= 0.15) continue;

            double dist = toTarget.length();

            Vec3 ndc = mc.gameRenderer.projectPointToScreen(targetWorldPos);

            float screenX = (float) ((ndc.x + 1.0) * 0.5 * screenW);
            float screenY = (float) ((1.0 - ndc.y) * 0.5 * screenH);

            float scale = Mth.clamp(1.15f - (float) dist * 0.006f, 0.75f, 1.25f);

            String playerName = target.getGameProfile().name();
            drawTacticalArrowMarker(graphics, mc, screenX, screenY + bob, playerName, dist, scale);
        }
    }

    private static void drawTacticalArrowMarker(OwoUIGraphics graphics, Minecraft mc, float sx, float sy, String playerName, double dist, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(sx, sy);
        graphics.pose().scale(scale, scale);

        graphics.fill(-1, -11, 2, -4, 0xFFFFFFFF);

        for (int row = 0; row <= 5; row++) {
            int halfW = 5 - row;
            graphics.fill(-halfW, -4 + row, halfW + 1, -3 + row, 0xFFFFFFFF);
        }

        String distText = ((int) dist) + "m";
        Component badgeText = Component.literal(playerName).append(Component.literal(" §e" + distText));

        float textScale = 0.80f;
        float textW = mc.font.width(badgeText) * textScale;
        float fontH = mc.font.lineHeight * textScale;

        int badgeW = (int) Math.ceil(textW + 10.0f);
        int badgeH = 12;
        int badgeX = -badgeW / 2;
        int badgeY = -24;

        Color badgeBg = Color.ofArgb(ARGB.color(195, 16, 22, 30));
        Color badgeBorder = Color.ofArgb(ARGB.color(140, 255, 255, 255));
        RenderUtil.drawRoundedPanel(graphics, badgeX, badgeY, badgeW, badgeH, 3.0f, badgeBg, badgeBorder);

        float textX = badgeX + (badgeW - textW) / 2.0f;
        float textY = badgeY + (badgeH - fontH) / 2.0f + 0.5f;
        graphics.drawText(badgeText, textX, textY, textScale, 0xFFFFFFFF, OwoUIGraphics.TextAnchor.TOP_LEFT);

        graphics.pose().popMatrix();
    }
}