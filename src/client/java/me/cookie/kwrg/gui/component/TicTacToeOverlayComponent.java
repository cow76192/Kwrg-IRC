package me.cookie.kwrg.gui.component;

import com.mojang.blaze3d.platform.NativeImage;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.module.modules.play.TicTacToeAI;
import me.cookie.kwrg.module.modules.play.TicTacToeGame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;

import java.io.InputStream;

public class TicTacToeOverlayComponent extends BaseUIComponent {

    private static final Identifier BG_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/background.png");
    private static final Identifier X_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/x.png");
    private static final Identifier O_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/ttt/o.png");

    private static final float BOARD_U0 = 0.3230f;
    private static final float BOARD_V0 = 0.3530f;
    private static final float BOARD_U1 = 0.6640f;
    private static final float BOARD_V1 = 0.7840f;

    private static final int BLANK_BG = 0xFF11161F;
    private static final int ACCENT = 0x4FC3F7;

    private static int[] bgSize = null;
    private static int[] xSize = null;
    private static int[] oSize = null;
    private static boolean sizeLoadAttempted = false;

    private final TicTacToeGame game = new TicTacToeGame();
    private boolean isUltron = false;

    public TicTacToeOverlayComponent() {
        loadTextureSizes();
        this.cursorStyle(CursorStyle.HAND);
        this.sizing(Sizing.fill(100), Sizing.fill(100));
        this.positioning(Positioning.absolute(0, 0));
    }

    private static synchronized void loadTextureSizes() {
        if (sizeLoadAttempted) return;
        sizeLoadAttempted = true;

        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        bgSize = readTextureSize(rm, BG_TEXTURE);
        xSize = readTextureSize(rm, X_TEXTURE);
        oSize = readTextureSize(rm, O_TEXTURE);
    }

    private static int[] readTextureSize(ResourceManager rm, Identifier id) {
        try {
            var res = rm.getResource(id);
            if (res.isEmpty()) return null;

            try (InputStream is = res.get().open(); NativeImage img = NativeImage.read(is)) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int ox = this.x();
        int oy = this.y();
        int w = this.width();
        int h = this.height();

        if (bgSize != null) {
            float[] cover = coverFit(w, h, bgSize);
            drawTextureScaled(graphics, BG_TEXTURE, bgSize, ox + cover[0], oy + cover[1], cover[2], cover[3]);
        } else {
            graphics.fill(ox, oy, ox + w, oy + h, BLANK_BG);
        }

        int[] board = getBoardRect(w, h);

        drawPieces(graphics, ox + board[0], oy + board[1], board[2], board[3]);
        drawTopBar(graphics, ox, oy, w, h);
        drawReset(graphics, ox, oy, w, h);
        drawStatus(graphics, ox, oy, w, h);
    }

    private float[] coverFit(int w, int h, int[] texSize) {
        float screenAspect = (float) w / (float) h;
        float imageAspect = (float) texSize[0] / (float) texSize[1];

        float drawW;
        float drawH;

        if (screenAspect > imageAspect) {
            drawW = w;
            drawH = w / imageAspect;
        } else {
            drawH = h;
            drawW = h * imageAspect;
        }

        return new float[]{
                (w - drawW) / 2.0f,
                (h - drawH) / 2.0f,
                drawW,
                drawH
        };
    }

    private int[] getBoardRect(int w, int h) {
        if (bgSize != null) {
            float[] cover = coverFit(w, h, bgSize);
            int bx = Math.round(cover[0] + BOARD_U0 * cover[2]);
            int by = Math.round(cover[1] + BOARD_V0 * cover[3]);
            int bx2 = Math.round(cover[0] + BOARD_U1 * cover[2]);
            int by2 = Math.round(cover[1] + BOARD_V1 * cover[3]);
            return new int[]{bx, by, bx2 - bx, by2 - by};
        }

        int size = (int) (Math.min(w, h) * 0.40f);
        int bx = (w - size) / 2;
        int by = (h - size) / 2 + (int) (h * 0.03f);
        return new int[]{bx, by, size, size};
    }

    private void drawTextureScaled(OwoUIGraphics graphics, Identifier id, int[] texSize, float x, float y, float w, float h) {
        if (texSize == null || texSize[0] <= 0 || texSize[1] <= 0 || w <= 0.0f || h <= 0.0f) return;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(w / texSize[0], h / texSize[1]);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                id,
                0,
                0,
                0.0f,
                0.0f,
                texSize[0],
                texSize[1],
                texSize[0],
                texSize[1]
        );
        graphics.pose().popMatrix();
    }

    private void drawPieces(OwoUIGraphics graphics, int bx, int by, int bw, int bh) {
        int cellW = bw / 3;
        int cellH = bh / 3;

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int piece = game.getBoard()[r][c];
                if (piece == 0) continue;

                int ps = Math.min(cellW, cellH);
                if (ps <= 0) continue;
                int px = bx + c * cellW + (cellW - ps) / 2;
                int py = by + r * cellH + (cellH - ps) / 2;

                if (piece == 1) {
                    if (xSize != null) {
                        drawTextureScaled(graphics, X_TEXTURE, xSize, px, py, ps, ps);
                    }
                } else {
                    if (oSize != null) {
                        drawTextureScaled(graphics, O_TEXTURE, oSize, px, py, ps, ps);
                    }
                }
            }
        }
    }

    private void drawTopBar(OwoUIGraphics graphics, int ox, int oy, int w, int h) {
        Font font = Minecraft.getInstance().font;

        Component back = FontManagerHelper.apply("< 返回");
        graphics.drawText(back, ox + w * 0.02f, oy + h * 0.025f, 0.95f, 0xFFB0BEC5, OwoUIGraphics.TextAnchor.TOP_LEFT);

        Component title = FontManagerHelper.apply("TIC TAC TOE");
        graphics.drawText(title, ox + (w - font.width(title)) / 2.0f, oy + h * 0.025f, 1.1f, 0xFFFFFFFF, OwoUIGraphics.TextAnchor.TOP_LEFT);

        drawDifficulty(graphics, font, ox, oy, w, h, "人类", 0.40f, !isUltron);
        drawDifficulty(graphics, font, ox, oy, w, h, "奥创", 0.60f, isUltron);
    }

    private void drawDifficulty(OwoUIGraphics graphics, Font font, int ox, int oy, int w, int h,
                                String text, float centerFrac, boolean active) {
        Component c = FontManagerHelper.apply(text);
        float cx = ox + w * centerFrac;
        float tx = cx - font.width(c) / 2.0f;
        float ty = oy + h * 0.065f;

        graphics.drawText(c, tx, ty, 0.95f, active ? 0xFFFFFFFF : 0xFF78909C, OwoUIGraphics.TextAnchor.TOP_LEFT);

        if (active) {
            int uy = (int) (ty + font.lineHeight * 0.95f + 2);
            graphics.fill(
                    (int) (tx - 2),
                    uy,
                    (int) (tx + font.width(c) + 2),
                    uy + 2,
                    ARGB.color(255, (ACCENT >> 16) & 0xFF, (ACCENT >> 8) & 0xFF, ACCENT & 0xFF)
            );
        }
    }

    private void drawReset(OwoUIGraphics graphics, int ox, int oy, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int bw = (int) (w * 0.16f);
        int bh = (int) (h * 0.05f);
        int bx = ox + (w - bw) / 2;
        int by = oy + (int) (h * 0.86f);

        Color border = Color.ofArgb(ARGB.color(120, 144, 164, 174));
        RenderUtil.drawRoundedPanel(graphics, bx, by, bw, bh, 6.0f, null, border);

        Component c = FontManagerHelper.apply("重新开始");
        graphics.drawText(c, bx + (bw - font.width(c)) / 2.0f, by + (bh - font.lineHeight) / 2.0f, 0.9f, 0xFFECEFF1, OwoUIGraphics.TextAnchor.TOP_LEFT);
    }

    private void drawStatus(OwoUIGraphics graphics, int ox, int oy, int w, int h) {
        Font font = Minecraft.getInstance().font;
        Component s = FontManagerHelper.apply(getStatusText());
        graphics.drawText(s, ox + (w - font.width(s)) / 2.0f, oy + h * 0.93f, 0.95f, 0xFFECEFF1, OwoUIGraphics.TextAnchor.TOP_LEFT);
    }

    private String getStatusText() {
        int winner = game.getWinner();
        if (winner == 1) return "你赢了 · 点棋盘重开";
        if (winner == 2) return "AI 赢了 · 点棋盘重开";
        if (winner == 3) return "平局 · 点棋盘重开";
        return game.getCurrentPlayer() == 1 ? "你的回合" : "AI 思考中";
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return true;

        int lx = (int) (click.x() - this.x());
        int ly = (int) (click.y() - this.y());
        int w = this.width();
        int h = this.height();

        if (lx <= w * 0.12 && ly <= h * 0.06) {
            closeOverlay();
            return true;
        }

        if (ly >= h * 0.055 && ly <= h * 0.095) {
            if (lx >= w * 0.34 && lx <= w * 0.48) {
                isUltron = false;
                game.reset();
                return true;
            }
            if (lx >= w * 0.52 && lx <= w * 0.66) {
                isUltron = true;
                game.reset();
                return true;
            }
        }

        int bw = (int) (w * 0.16f);
        int bh = (int) (h * 0.05f);
        int rbx = (w - bw) / 2;
        int rby = (h * 0.86f) != 0 ? (int) (h * 0.86f) : 0;

        if (lx >= rbx && lx <= rbx + bw && ly >= rby && ly <= rby + bh) {
            game.reset();
            return true;
        }

        int[] board = getBoardRect(w, h);
        int bx = board[0];
        int by = board[1];
        int bbw = board[2];
        int bbh = board[3];

        if (lx >= bx && lx <= bx + bbw && ly >= by && ly <= by + bbh) {
            if (game.getWinner() != 0) {
                game.reset();
                return true;
            }

            if (game.getCurrentPlayer() == 1) {
                int cellW = bbw / 3;
                int cellH = bbh / 3;
                int c = (lx - bx) / cellW;
                int r = (ly - by) / cellH;

                c = Math.min(2, Math.max(0, c));
                r = Math.min(2, Math.max(0, r));

                if (game.makeMove(r, c) && game.getWinner() == 0) {
                    int[] mv = TicTacToeAI.getBestMove(game.getBoard(), isUltron);
                    if (mv != null) {
                        game.makeMove(mv[0], mv[1]);
                    }
                }
            }

            return true;
        }

        return true;
    }

    private void closeOverlay() {
        IrcScreen screen = IrcScreen.getCurrentScreen();
        if (screen != null) {
            screen.hideOverlay();
        }
    }
}