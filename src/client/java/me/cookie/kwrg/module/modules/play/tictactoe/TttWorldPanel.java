package me.cookie.kwrg.module.modules.play.tictactoe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cookie.kwrg.gui.notification.NotificationManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.Random;

public final class TttWorldPanel {

    private static final Identifier BG_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/background.png");
    private static final Identifier X_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/x.png");
    private static final Identifier O_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/o.png");
    private static final Identifier BUTTON_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/button.png");

    private static final float PX_W = 1024.0f;
    private static final float PX_H = 768.0f;
    private static final float WORLD_W = 2.4f;
    private static final float K = WORLD_W / PX_W;

    private static final float BTN_CX0 = 385.5f;
    private static final float BTN_CY0 = 313.5f;
    private static final float BTN_CX_STEP = 119.5f;
    private static final float BTN_CY_STEP = 120.0f;
    private static final float BTN_SCALE = 7.65f;
    private static final float BTN_HALF_W = 3.0f;
    private static final float BTN_HALF_H = 2.0f;
    private static final float BTN_PRESS_TICKS = 20.0f;
    private static final float BTN_DEPTH = 8.0f;
    private static final float BTN_PROTRUDE = 8.0f;
    private static final float BTN_TRAVEL = 5.0f;
    private static final float PIECE_Z = 1.0f;
    private static final float CELL_HALF_W = 59.75f;
    private static final float CELL_HALF_H = 60.0f;
    private static final int SHADE_TOP = 199;
    private static final int SHADE_BOTTOM = 91;
    private static final int SHADE_SIDE = 139;

    private static final TicTacToeGame GAME = new TicTacToeGame();
    private static boolean active = false;
    private static boolean isUltron = false;
    private static double px = 0, py = 0, pz = 0;
    private static float yawRad = 0;

    private static int[] pendingAiMove = null;
    private static long pendingAiDeadline = 0L;
    private static final Random AI_DELAY_RANDOM = new Random();

    private static boolean hooksRegistered = false;
    private static boolean renderErrorNotified = false;
    private static long lastMissNotify = 0L;
    private static int rightClickDelay = 0;
    private static boolean prevUseDown = false;

    private static final boolean[] BTN_PRESSED = new boolean[9];
    private static final float[] BTN_OFFSET = new float[9];
    private static final int[] BTN_PRESS_TICKS_LEFT = new int[9];

    private static java.lang.reflect.Method clientSwingMethod = null;

    private TttWorldPanel() {}

    public static void ensureHooks() {
        if (hooksRegistered) return;
        hooksRegistered = true;

        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register((LevelRenderContext ctx) -> {
            if (active) render(ctx);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean down = mc.options != null && mc.options.keyUse != null && mc.options.keyUse.isDown();
            if (rightClickDelay > 0) rightClickDelay--;
            if (down && rightClickDelay == 0 && active && !isHandsBusy(mc)) {
                if (tryClick()) {
                    rightClickDelay = 4;
                    swingClientOnly(mc);
                }
            }
            prevUseDown = down;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (active) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });

        // 面板的挥手必须只走本地动画：LocalPlayer.swing 会发送 ServerboundSwingPacket，
        // 而 AbstractClientPlayer 没有声明 swing，只能反射取 LivingEntity 的实现来绕过覆写。
        try {
            clientSwingMethod = LivingEntity.class.getDeclaredMethod("swing", InteractionHand.class);
            clientSwingMethod.setAccessible(true);
        } catch (Throwable ignored) {
            clientSwingMethod = null;
        }
    }

    public static void openAtPlayer() {
        Minecraft mc = Minecraft.getInstance();
        ensureHooks();

        double[] cp = cameraPos(mc);
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

        resetGame();
        resetButtons();
        rightClickDelay = 0;
        active = true;
        notifyInfo("面板已生成在视线高度，准星瞄准+右键交互");
    }

    public static void close() {
        active = false;
        resetButtons();
        rightClickDelay = 0;
    }

    private static void resetGame() {
        GAME.reset();
        pendingAiMove = null;
    }

    private static void resetButtons() {
        for (int i = 0; i < 9; i++) {
            BTN_PRESSED[i] = false;
            BTN_OFFSET[i] = 0.0f;
            BTN_PRESS_TICKS_LEFT[i] = 0;
        }
    }

    private static void tickPendingAi() {
        if (pendingAiMove == null || !active) return;
        long now = System.currentTimeMillis();
        if (now < pendingAiDeadline) return;
        int[] mv = pendingAiMove;
        pendingAiMove = null;
        if (GAME.getWinner() == 0 && GAME.getCurrentPlayer() == 2) {
            if (GAME.makeMove(mv[0], mv[1])) {
                announce();
            }
        }
    }

    private static void tickButtons() {
        float step = 0.5f;
        for (int i = 0; i < 9; i++) {
            if (BTN_PRESSED[i]) {
                if (BTN_PRESS_TICKS_LEFT[i] > 0) BTN_PRESS_TICKS_LEFT[i]--;
                else BTN_PRESSED[i] = false;
            }
            float target = BTN_PRESSED[i] ? 1.0f : 0.0f;
            if (BTN_OFFSET[i] < target) BTN_OFFSET[i] = Math.min(target, BTN_OFFSET[i] + step);
            else if (BTN_OFFSET[i] > target) BTN_OFFSET[i] = Math.max(target, BTN_OFFSET[i] - step);
        }
    }

    private static boolean isHandsBusy(Minecraft mc) {
        if (mc.player == null) return true;
        return mc.player.isHandsBusy();
    }

    private static void swingClientOnly(Minecraft mc) {
        if (clientSwingMethod == null || mc.player == null) return;
        try {
            clientSwingMethod.invoke(mc.player, InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }

    private static void notifyInfo(String msg) {
        Minecraft.getInstance().execute(() -> NotificationManager.info("Tic Tac Toe", msg));
    }

    private static void notifySuccess(String msg) {
        Minecraft.getInstance().execute(() -> NotificationManager.success("Tic Tac Toe", msg));
    }

    private static void notifyWarn(String msg) {
        Minecraft.getInstance().execute(() -> NotificationManager.warn("Tic Tac Toe", msg));
    }

    private static void notifyErr(String msg) {
        Minecraft.getInstance().execute(() -> NotificationManager.err("Tic Tac Toe", msg));
    }

    private static double[] cameraPos(Minecraft mc) {
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        if (cam != null) return new double[]{cam.x, cam.y, cam.z};
        return eyePos(mc);
    }

    private static double[] eyePos(Minecraft mc) {
        if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            return new double[]{eye.x, eye.y, eye.z};
        }
        return new double[]{0, 0, 0};
    }

    private static double[] originLook(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera != null && camera.isInitialized()) {
            Vector3fc forward = camera.forwardVector();
            if (forward.x() != 0.0f || forward.y() != 0.0f || forward.z() != 0.0f) {
                return new double[]{forward.x(), forward.y(), forward.z()};
            }
        }
        if (mc.player != null) {
            Vec3 look = mc.player.getLookAngle();
            return new double[]{look.x, look.y, look.z};
        }
        return null;
    }

    private static boolean tryClick() {
        if (!active) return false;
        Minecraft mc = Minecraft.getInstance();
        double[] cp = cameraPos(mc);
        double[] lk = originLook(mc);
        if (cp == null || lk == null) return false;

        float sa = (float) Math.sin(yawRad);
        float ca = (float) Math.cos(yawRad);
        double denom = lk[0] * sa + lk[2] * ca;
        if (denom > -0.15) return false;

        double t = ((px - cp[0]) * sa + (pz - cp[2]) * ca) / denom;
        if (t <= 0) return false;
        double hx = cp[0] + lk[0] * t - px;
        double hy = cp[1] + lk[1] * t - py;
        double hz = cp[2] + lk[2] * t - pz;

        double xl = hx * ca - hz * sa;
        double yl = hy;
        float gx = (float) (xl / K + PX_W / 2.0);
        float gy = (float) (PX_H / 2.0 - yl / K);

        return handleClick(gx, gy);
    }

    private static boolean handleClick(float gx, float gy) {
        if (gx >= PX_W - 160 && gy <= 76) {
            close();
            notifyInfo("面板已关闭");
            return true;
        }
        if (gy >= 48 && gy <= 112) {
            if (gx >= PX_W * 0.30f && gx <= PX_W * 0.46f) {
                isUltron = false;
                resetGame();
                notifyInfo("难度：人类");
                return true;
            }
            if (gx >= PX_W * 0.54f && gx <= PX_W * 0.70f) {
                isUltron = true;
                resetGame();
                notifyInfo("难度：奥创");
                return true;
            }
        }
        if (gy >= PX_H - 80 && gy <= PX_H - 24 && gx >= PX_W * 0.30f && gx <= PX_W * 0.70f) {
            resetGame();
            notifyInfo("已重开");
            return true;
        }

        int idx = buttonAt(gx, gy);
        if (idx < 0) {
            long now = System.currentTimeMillis();
            if (now - lastMissNotify > 1000L) {
                lastMissNotify = now;
                notifyWarn("miss gx=" + (int) gx + " gy=" + (int) gy);
            }
            return false;
        }

        if (GAME.getWinner() != 0) {
            resetGame();
            notifyInfo("已重开");
            return true;
        }

        if (GAME.getCurrentPlayer() == 1) {
            if (GAME.makeMove(idx / 3, idx % 3)) {
                pressButton(idx);
                if (GAME.getWinner() == 0) {
                    int[] mv = TicTacToeAI.getBestMove(GAME.getBoard(), isUltron);
                    if (mv != null) {
                        pendingAiMove = mv;
                        pendingAiDeadline = System.currentTimeMillis() + 600L + AI_DELAY_RANDOM.nextInt(1201);
                    }
                }
                announce();
            }
        }
        return true;
    }

    private static int buttonAt(float gx, float gy) {
        for (int i = 0; i < 9; i++) {
            float dx = Math.abs(gx - buttonCenterX(i));
            float dy = Math.abs(gy - buttonCenterY(i));
            if (dx <= CELL_HALF_W && dy <= CELL_HALF_H) return i;
        }
        return -1;
    }

    private static void pressButton(int index) {
        BTN_PRESSED[index] = true;
        BTN_PRESS_TICKS_LEFT[index] = (int) BTN_PRESS_TICKS;
    }

    private static float buttonCenterX(int index) {
        return BTN_CX0 + (index % 3) * BTN_CX_STEP;
    }

    private static float buttonCenterY(int index) {
        return BTN_CY0 + (index / 3) * BTN_CY_STEP;
    }

    private static void announce() {
        int w = GAME.getWinner();
        if (w == 1) notifySuccess("你赢了！右键棋盘重开");
        else if (w == 2) notifyErr("AI 赢了！右键棋盘重开");
        else if (w == 3) notifyInfo("平局！右键棋盘重开");
        else notifyInfo(GAME.getCurrentPlayer() == 1 ? "你的回合" : "AI 思考中");
    }

    private static void render(LevelRenderContext ctx) {
        try {
            tickPendingAi();
            tickButtons();

            PoseStack ps = ctx.poseStack();
            MultiBufferSource consumers = ctx.bufferSource();

            Minecraft mc = Minecraft.getInstance();
            double[] cam = cameraPos(mc);

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

            for (int i = 0; i < 9; i++) {
                if (GAME.getBoard()[i / 3][i % 3] != 0 && BTN_OFFSET[i] <= 0.0f) continue;
                drawButton(consumers, pose, i);
            }

            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int piece = GAME.getBoard()[r][c];
                    if (piece == 0) continue;
                    if (BTN_PRESSED[r * 3 + c]) continue;
                    float ccx = buttonCenterX(r * 3 + c) - PX_W / 2.0f;
                    float ccy = PX_H / 2.0f - buttonCenterY(r * 3 + c);
                    RenderType pt = piece == 1 ? xType : oType;
                    if (pt != null) {
                        VertexConsumer pvc = consumers.getBuffer(pt);
                        quad(pvc, pose, ccx - CELL_HALF_W, ccy - CELL_HALF_H, ccx + CELL_HALF_W, ccy + CELL_HALF_H, PIECE_Z, 0, 0, 1, 1);
                        quadBack(pvc, pose, ccx - CELL_HALF_W, ccy - CELL_HALF_H, ccx + CELL_HALF_W, ccy + CELL_HALF_H, PIECE_Z, 0, 0, 1, 1);
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
            drawText(font, consumers, tp, "X", PX_W - 60, 44, 0xFFECEFF1, light, 3.0f);
            drawText(font, consumers, tp, "人类", PX_W * 0.36f, 80, isUltron ? 0xFF78909C : 0xFFFFFFFF, light);
            drawText(font, consumers, tp, "奥创", PX_W * 0.60f, 80, isUltron ? 0xFFFFFFFF : 0xFF78909C, light);
            drawText(font, consumers, tp, "重新开始", PX_W / 2 - 30, PX_H - 45, 0xFFECEFF1, light);
            ps.popPose();
        } catch (Throwable t) {
            if (!renderErrorNotified) {
                renderErrorNotified = true;
                notifyErr("渲染异常: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    private static void drawButton(MultiBufferSource consumers, Matrix4f pose, int index) {
        RenderType type = RenderTypes.text(BUTTON_TEXTURE);
        if (type == null) return;

        float cx = buttonCenterX(index) - PX_W / 2.0f;
        float cy = PX_H / 2.0f - buttonCenterY(index);
        float s = BTN_SCALE;
        float z1 = BTN_PROTRUDE - BTN_TRAVEL * BTN_OFFSET[index];
        float z0 = z1 - BTN_DEPTH;

        float x0 = cx - BTN_HALF_W * s;
        float x1 = cx + BTN_HALF_W * s;
        float y0 = cy - BTN_HALF_H * s;
        float y1 = cy + BTN_HALF_H * s;

        VertexConsumer vc = consumers.getBuffer(type);
        int light = 15728880;

        texQuad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, SHADE_TOP, light, 0.0f, 0.0f, 1.0f);
        texQuad(vc, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, SHADE_BOTTOM, light, 0.0f, 0.0f, -1.0f);
        texQuad(vc, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, SHADE_TOP, light, 0.0f, 1.0f, 0.0f);
        texQuad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, SHADE_BOTTOM, light, 0.0f, -1.0f, 0.0f);
        texQuad(vc, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, SHADE_SIDE, light, 1.0f, 0.0f, 0.0f);
        texQuad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, SHADE_SIDE, light, -1.0f, 0.0f, 0.0f);
    }

    private static void texQuad(VertexConsumer vc, Matrix4f pose,
                                float ax, float ay, float az, float bx, float by, float bz,
                                float cx, float cy, float cz, float dx, float dy, float dz,
                                int shade, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, ax, ay, az).setColor(shade, shade, shade, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(pose, bx, by, bz).setColor(shade, shade, shade, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(pose, cx, cy, cz).setColor(shade, shade, shade, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(pose, dx, dy, dz).setColor(shade, shade, shade, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
    }

    private static void drawText(Font font, MultiBufferSource consumers, Matrix4f pose, String text, float x, float y, int color, int light) {
        drawText(font, consumers, pose, text, x, y, color, light, 1.0f);
    }

    private static void drawText(Font font, MultiBufferSource consumers, Matrix4f pose, String text, float x, float y, int color, int light, float scale) {
        Matrix4f glyphPose = pose;
        if (scale != 1.0f) {
            float ax = x - PX_W / 2.0f;
            float ay = y - PX_H / 2.0f;
            glyphPose = new Matrix4f(pose)
                    .translate(ax, ay, 0.0f)
                    .scale(scale, scale, 1.0f)
                    .translate(-ax, -ay, 0.0f);
        }
        font.drawInBatch(Component.literal(text), x - PX_W / 2.0f, y - PX_H / 2.0f, color, false, glyphPose, consumers, Font.DisplayMode.NORMAL, 0, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float z,
                             float u0, float v0, float u1, float v1) {
        vc.addVertex(pose, x0, y1, z).setUv(u0, v0).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x0, y0, z).setUv(u0, v1).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x1, y0, z).setUv(u1, v1).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x1, y1, z).setUv(u1, v0).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void quadBack(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float z,
                                 float u0, float v0, float u1, float v1) {
        vc.addVertex(pose, x1, y1, z).setUv(u1, v0).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x1, y0, z).setUv(u1, v1).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x0, y0, z).setUv(u0, v1).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, -1.0F);
        vc.addVertex(pose, x0, y1, z).setUv(u0, v0).setColor(255, 255, 255, 255).setLight(15728880).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, -1.0F);
    }
}
