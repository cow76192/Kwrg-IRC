package me.cookie.kwrg.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.module.modules.play.TicTacToeAI;
import me.cookie.kwrg.module.modules.play.TicTacToeGame;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.lang.reflect.Method;

public final class TttWorldPanel {

    private static final Identifier BG_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/background.png");
    private static final Identifier X_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/x.png");
    private static final Identifier O_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/o.png");

    private static final float BOARD_U0 = 0.3230f;
    private static final float BOARD_V0 = 0.3530f;
    private static final float BOARD_U1 = 0.6640f;
    private static final float BOARD_V1 = 0.7840f;

    private static final float PX_W = 1024.0f;
    private static final float PX_H = 768.0f;
    private static final float WORLD_W = 2.4f;
    private static final float K = WORLD_W / PX_W;

    private static final String[] POS_NAMES = {"position", "getPosition", "getPos"};
    private static final String[] LOOK_NAMES = {"getLookAngle", "lookAngle", "getLookVector", "lookVector", "getLookDirection", "getViewVector", "getRotationVector"};

    private static final TicTacToeGame GAME = new TicTacToeGame();
    private static boolean active = false;
    private static boolean isUltron = false;
    private static double px = 0, py = 0, pz = 0;
    private static float yawRad = 0;

    private static boolean hooksRegistered = false;
    private static boolean renderErrorNotified = false;
    private static long lastMissNotify = 0L;
    private static long lastRayNotify = 0L;
    private static long lastClickMs = 0L;
    private static boolean prevUseDown = false;
    private static int[] overlayUv = null;

    private TttWorldPanel() {}

    public static void ensureHooks() {
        if (hooksRegistered) return;
        hooksRegistered = true;

        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(ctx -> {
            if (active) render(ctx);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean down = mc.options != null && mc.options.keyUse != null && mc.options.keyUse.isDown();
            if (down && !prevUseDown && active) {
                long now = System.currentTimeMillis();
                if (now - lastClickMs > 250L) {
                    lastClickMs = now;
                    tryClick();
                }
            }
            prevUseDown = down;
        });
    }

    public static void openAtPlayer() {
        Minecraft mc = Minecraft.getInstance();
        ensureHooks();

        double[] cp = cameraPosSmooth(mc);
        double[] lk = originLook(mc);

        if (lk == null) {
            notifyErr("无法读取朝向");
            return;
        }

        double hx = lk[0], hz = lk[2];
        double hl = Math.sqrt(hx * hx + hz * hz);
        if (hl < 0.001) { hx = 0; hz = 1; } else { hx /= hl; hz /= hl; }

        px = cp[0] + hx * 2.6;
        py = cp[1];
        pz = cp[2] + hz * 2.6;

        yawRad = (float) Math.atan2(-hx, -hz);

        GAME.reset();
        active = true;
        renderErrorNotified = false;
        notifyInfo("面板已生成在视线高度，准星瞄准+右键交互");
    }

    public static void close() {
        active = false;
    }

    private static void notifyInfo(String msg) {
        Minecraft.getInstance().execute(() -> NotificationManager.info("Tic Tac Toe", msg));
    }

    private static void notifyErr(String msg) {
        Minecraft.getInstance().execute(() -> NotificationManager.err("Tic Tac Toe", msg));
    }

    private static double[] cameraPosSmooth(Minecraft mc) {
        Object cam = getCamera(mc);
        if (cam != null) {
            double[] v = vec3(cam, POS_NAMES);
            if (v != null) return v;
        }
        return eyePos(mc);
    }

    private static double[] eyePos(Minecraft mc) {
        if (mc.player != null) {
            for (String n : new String[]{"getEyePosition", "eyePosition"}) {
                for (Method m : mc.player.getClass().getMethods()) {
                    if (m.getName().equals(n) && m.getParameterCount() == 0) {
                        try {
                            Object v = m.invoke(mc.player);
                            if (v != null) return new double[]{num(v, "x"), num(v, "y"), num(v, "z")};
                        } catch (Throwable ignored) {}
                    }
                }
            }
            double[] p = vec3(mc.player, POS_NAMES);
            if (p != null) return new double[]{p[0], p[1] + 1.62, p[2]};
        }
        return new double[]{0, 0, 0};
    }

    private static double[] originLook(Minecraft mc) {
        if (mc.player != null) {
            double[] v = vec3(mc.player, LOOK_NAMES);
            if (v != null) return v;
        }
        Object cam = getCamera(mc);
        if (cam != null) return vec3(cam, LOOK_NAMES);
        return null;
    }

    private static Object getCamera(Minecraft mc) {
        try {
            Object gr = mc.gameRenderer;
            for (String n : new String[]{"getMainCamera", "mainCamera", "getCamera", "camera"}) {
                for (Method m : gr.getClass().getMethods()) {
                    if (m.getName().equals(n) && m.getParameterCount() == 0) {
                        return m.invoke(gr);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static double[] vec3(Object obj, String[] names) {
        if (obj == null) return null;
        for (String n : names) {
            try {
                for (Method m : obj.getClass().getMethods()) {
                    if (m.getName().equals(n) && m.getParameterCount() == 0) {
                        Object v = m.invoke(obj);
                        if (v == null) continue;
                        return new double[]{num(v, "x"), num(v, "y"), num(v, "z")};
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static double num(Object vec, String field) {
        try {
            return vec.getClass().getField(field).getDouble(vec);
        } catch (Throwable t) {
            try {
                Method m = vec.getClass().getMethod(field);
                return ((Number) m.invoke(vec)).doubleValue();
            } catch (Throwable t2) {
                return 0;
            }
        }
    }

    private static boolean tryClick() {
        if (!active) return false;
        Minecraft mc = Minecraft.getInstance();
        double[] cp = cameraPosSmooth(mc);
        double[] lk = originLook(mc);
        if (cp == null || lk == null) return false;

        float sa = (float) Math.sin(yawRad);
        float ca = (float) Math.cos(yawRad);
        double denom = lk[0] * sa + lk[2] * ca;
        if (denom > -0.15) {
            rayReject("denom=" + denom);
            return false;
        }

        double t = ((px - cp[0]) * sa + (pz - cp[2]) * ca) / denom;
        if (t <= 0) {
            rayReject("t=" + t);
            return false;
        }
        double hx = cp[0] + lk[0] * t - px;
        double hy = cp[1] + lk[1] * t - py;
        double hz = cp[2] + lk[2] * t - pz;

        double xl = hx * ca - hz * sa;
        double yl = hy;
        float gx = (float) (xl / K + PX_W / 2.0);
        float gy = (float) (PX_H / 2.0 - yl / K);

        return handleClick(gx, gy);
    }

    private static void rayReject(String detail) {
        long now = System.currentTimeMillis();
        if (now - lastRayNotify > 1000L) {
            lastRayNotify = now;
            notifyInfo("ray reject " + detail);
        }
    }

    private static boolean handleClick(float gx, float gy) {
        if (gx >= PX_W - 160 && gy <= 64) {
            close();
            notifyInfo("面板已关闭");
            return true;
        }
        if (gy >= 48 && gy <= 112) {
            if (gx >= PX_W * 0.30f && gx <= PX_W * 0.46f) {
                isUltron = false;
                GAME.reset();
                notifyInfo("难度：人类");
                return true;
            }
            if (gx >= PX_W * 0.54f && gx <= PX_W * 0.70f) {
                isUltron = true;
                GAME.reset();
                notifyInfo("难度：奥创");
                return true;
            }
        }
        if (gy >= PX_H - 80 && gy <= PX_H - 24 && gx >= PX_W * 0.30f && gx <= PX_W * 0.70f) {
            GAME.reset();
            notifyInfo("已重开");
            return true;
        }

        float b0x = BOARD_U0 * PX_W, b1x = BOARD_U1 * PX_W;
        float b0y = BOARD_V0 * PX_H, b1y = BOARD_V1 * PX_H;
        if (gx >= b0x && gx <= b1x && gy >= b0y && gy <= b1y) {
            if (GAME.getWinner() != 0) {
                GAME.reset();
                notifyInfo("已重开");
                return true;
            }
            if (GAME.getCurrentPlayer() == 1) {
                float cw = (b1x - b0x) / 3.0f;
                float ch = (b1y - b0y) / 3.0f;
                int c = (int) ((gx - b0x) / cw);
                int r = (int) ((gy - b0y) / ch);
                c = Math.min(2, Math.max(0, c));
                r = Math.min(2, Math.max(0, r));
                if (GAME.makeMove(r, c)) {
                    if (GAME.getWinner() == 0) {
                        int[] mv = TicTacToeAI.getBestMove(GAME.getBoard(), isUltron);
                        if (mv != null) GAME.makeMove(mv[0], mv[1]);
                    }
                    announce();
                }
            }
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastMissNotify > 1000L) {
            lastMissNotify = now;
            notifyInfo("miss gx=" + (int) gx + " gy=" + (int) gy);
        }
        return false;
    }

    private static void announce() {
        int w = GAME.getWinner();
        if (w == 1) notifyInfo("你赢了！右键棋盘重开");
        else if (w == 2) notifyInfo("AI 赢了！右键棋盘重开");
        else if (w == 3) notifyInfo("平局！右键棋盘重开");
        else notifyInfo(GAME.getCurrentPlayer() == 1 ? "你的回合" : "AI 思考中");
    }

    private static void render(Object ctx) {
        try {
            PoseStack ps = (PoseStack) ctx.getClass().getMethod("poseStack").invoke(ctx);
            MultiBufferSource consumers = (MultiBufferSource) ctx.getClass().getMethod("bufferSource").invoke(ctx);

            if (ps == null || consumers == null) {
                if (!renderErrorNotified) {
                    renderErrorNotified = true;
                    notifyErr("渲染上下文获取失败 (ps/consumers 为 null)");
                }
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            double[] cam = cameraPosSmooth(mc);

            RenderType bgType = RenderTypes.text(BG_TEXTURE);
            RenderType xType = RenderTypes.text(X_TEXTURE);
            RenderType oType = RenderTypes.text(O_TEXTURE);

            if (bgType == null) {
                if (!renderErrorNotified) {
                    renderErrorNotified = true;
                    notifyErr("RenderType 获取失败");
                }
                return;
            }

            ps.pushPose();
            ps.translate((float) (px - cam[0]), (float) (py - cam[1]), (float) (pz - cam[2]));
            ps.mulPose(new Quaternionf().rotationY(yawRad));
            ps.scale(K, K, K);

            Matrix4f pose = ps.last().pose();
            VertexConsumer bgVc = consumers.getBuffer(bgType);
            quad(bgVc, pose, -PX_W / 2, -PX_H / 2, PX_W / 2, PX_H / 2, 0.0f, 0, 0, 1, 1);
            quadBack(bgVc, pose, -PX_W / 2, -PX_H / 2, PX_W / 2, PX_H / 2, 0.0f, 0, 0, 1, 1);

            float b0x = BOARD_U0 * PX_W - PX_W / 2;
            float b1x = BOARD_U1 * PX_W - PX_W / 2;
            float b0y = PX_H / 2 - BOARD_V0 * PX_H;
            float b1y = PX_H / 2 - BOARD_V1 * PX_H;
            float cw = (b1x - b0x) / 3.0f;
            float ch = (b0y - b1y) / 3.0f;

            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int piece = GAME.getBoard()[r][c];
                    if (piece == 0) continue;
                    float inset = 0.06f;
                    float x0 = b0x + c * cw + cw * inset;
                    float x1 = b0x + (c + 1) * cw - cw * inset;
                    float yTop = b0y - r * ch;
                    float y0 = yTop - ch * (1 - inset);
                    float y1 = yTop - ch * inset;
                    RenderType pt = piece == 1 ? xType : oType;
                    if (pt != null) {
                        VertexConsumer pvc = consumers.getBuffer(pt);
                        quad(pvc, pose, x0, y0, x1, y1, 2.0f, 0, 0, 1, 1);
                        quadBack(pvc, pose, x0, y0, x1, y1, 2.0f, 0, 0, 1, 1);
                    }
                }
            }
            ps.popPose();

            ps.pushPose();
            ps.translate((float) (px - cam[0]), (float) (py - cam[1]), (float) (pz - cam[2]));
            ps.mulPose(new Quaternionf().rotationY(yawRad));
            ps.scale(K, -K, K);

            Matrix4f tp = ps.last().pose();
            Font font = Minecraft.getInstance().font;
            int light = 15728880;
            drawText(font, consumers, tp, "TIC TAC TOE", PX_W / 2 - 60, 30, 0xFFFFFFFF, light);
            drawText(font, consumers, tp, "X", PX_W - 60, 30, 0xFFB0BEC5, light);
            drawText(font, consumers, tp, "人类", PX_W * 0.36f, 80, isUltron ? 0xFF78909C : 0xFFFFFFFF, light);
            drawText(font, consumers, tp, "奥创", PX_W * 0.60f, 80, isUltron ? 0xFFFFFFFF : 0xFF78909C, light);
            drawText(font, consumers, tp, "重新开始", PX_W / 2 - 30, PX_H - 45, 0xFFECEFF1, light);
            drawText(font, consumers, tp, statusText(), PX_W / 2 - 40, PX_H - 12, 0xFFECEFF1, light);
            ps.popPose();
        } catch (Throwable t) {
            if (!renderErrorNotified) {
                renderErrorNotified = true;
                notifyErr("渲染异常: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    private static String statusText() {
        int w = GAME.getWinner();
        if (w == 1) return "你赢了";
        if (w == 2) return "AI 赢了";
        if (w == 3) return "平局";
        return GAME.getCurrentPlayer() == 1 ? "你的回合" : "AI 思考中";
    }

    private static void drawText(Font font, MultiBufferSource consumers, Matrix4f pose, String text, float x, float y, int color, int light) {
        font.drawInBatch(Component.literal(text), x - PX_W / 2.0f, y - PX_H / 2.0f, color, false, pose, consumers, Font.DisplayMode.NORMAL, 0, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float z,
                             float u0, float v0, float u1, float v1) {
        int[] ov = overlayUv();
        vc.addVertex(pose, x0, y1, z).setUv(u0, v0).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x0, y0, z).setUv(u0, v1).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x1, y0, z).setUv(u1, v1).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x1, y1, z).setUv(u1, v0).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void quadBack(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float z,
                                 float u0, float v0, float u1, float v1) {
        int[] ov = overlayUv();
        vc.addVertex(pose, x1, y1, z).setUv(u1, v0).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x1, y0, z).setUv(u1, v1).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x0, y0, z).setUv(u0, v1).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x0, y1, z).setUv(u0, v0).setColor(255, 255, 255, 255).setLight(15728880).setUv1(ov[0], ov[1]).setNormal(0.0F, 0.0F, -1.0F);
    }

    private static int[] overlayUv() {
        if (overlayUv == null) {
            int packed = -1;
            for (String cn : new String[]{
                    "net.minecraft.client.renderer.texture.OverlayTexture",
                    "net.minecraft.client.renderer.OverlayTexture",
                    "net.minecraft.client.renderer.texture.overlay.OverlayTexture"}) {
                try {
                    Class<?> c = Class.forName(cn);
                    packed = c.getField("NO_OVERLAY").getInt(null);
                    break;
                } catch (Throwable ignored) {}
            }
            if (packed == -1) packed = (10 << 16) | 0;
            overlayUv = new int[]{packed & 0xFFFF, (packed >> 16) & 0xFFFF};
        }
        return overlayUv;
    }
}