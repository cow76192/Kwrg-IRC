package me.cookie.kwrg.gui.title;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class TitleModeButton extends Button {
    private static final int SIZE = 20;
    private static final int MARGIN = 8;
    private static final int CYAN = 0x85E8EC;

    private static final Component OPEN_CLASSIC = Component.translatable("kwrg.title.open_classic");
    private static final Component OPEN_CUSTOM = Component.translatable("kwrg.title.open_custom");

    private static final int[][] CUBE_VERTICES = {{5, 7}, {10, 4}, {15, 7}, {15, 13}, {10, 16}, {5, 13}, {10, 10}};
    private static final int[][] CUBE_EDGES = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 0}, {0, 6}, {2, 6}, {4, 6}};
    private boolean classic;

    public TitleModeButton(int screenWidth, OnPress onPress) {
        super(screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE, OPEN_CLASSIC, onPress, DEFAULT_NARRATION);
    }

    public void setClassic(boolean classic) {
        this.classic = classic;
        setMessage(classic ? OPEN_CUSTOM : OPEN_CLASSIC);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        int x = getX();
        int y = getY();

        boolean highlighted = isHoveredOrFocused() && active;

        if (classic) {
            extractDefaultSprite(graphics);
            float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() % 2800L * (2.0 * Math.PI / 2800.0));
            graphics.fill(x + 7, y + 7, x + 13, y + 13, 0x448B57ED);
            graphics.fill(x + 8, y + 8, x + 12, y + 12, 0xFF7954C5);
            graphics.fill(x + 9, y + 9, x + 11, y + 11, ((int) (110 + 145 * pulse) << 24) | CYAN);
        } else {
            graphics.fillGradient(x, y, x + SIZE, y + SIZE, 0xC0274058, 0xB0081626);
            graphics.fill(x, y, x + 8, y + 1, 0xFF000000 | CYAN);
            for (int[] edge : CUBE_EDGES) {
                int[] a = CUBE_VERTICES[edge[0]];
                int[] b = CUBE_VERTICES[edge[1]];

                float dx = b[0] - a[0];
                float dy = b[1] - a[1];

                var pose = graphics.pose();
                pose.pushMatrix().translate(x + a[0] + 0.5f, y + a[1] + 0.5f)
                        .rotate((float) Math.atan2(dy, dx));
                graphics.fill(0, 0, Math.round((float) Math.hypot(dx, dy)), 1, 0xDD000000 | CYAN);
                pose.popMatrix();
            }
        }
        if (highlighted) {
            graphics.outline(x - 1, y - 1, SIZE + 2, SIZE + 2, 0x5585E8EC);
            graphics.outline(x, y, SIZE, SIZE, 0xDD85E8EC);
            if (isHovered()) {
                graphics.requestCursor(CursorTypes.POINTING_HAND);
                graphics.setTooltipForNextFrame(getMessage(), mouseX, mouseY);
            }
        }
    }
}
