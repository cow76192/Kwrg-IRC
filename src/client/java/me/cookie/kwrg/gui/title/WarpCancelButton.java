package me.cookie.kwrg.gui.title;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class WarpCancelButton extends Button {
    private static final Component LABEL = Component.translatable("kwrg.warp.abort");

    public WarpCancelButton(int width, int height, OnPress action) {
        super(width / 2 - 68, height - 29, 136, 18, LABEL, action, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        int x = getX();
        int y = getY();
        boolean hot = isHoveredOrFocused() && active;
        graphics.fill(x, y, x + width, y + height, hot ? 0x773F141F : 0x38101B27);
        graphics.outline(x, y, width, height, hot ? 0xEEFE6475 : 0x667D9CAE);
        graphics.centeredText(Minecraft.getInstance().font, getMessage(), x + width / 2, y + 5,
                hot ? 0xFFFFA1A8 : 0x66D2E5EF);
        if (hot && isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
    }

    public static void status(GuiGraphicsExtractor graphics, Component status, int x, int height) {
        var font = Minecraft.getInstance().font;
        int width = font.width(status);

        float pulse = (float) Math.sin((Util.getMillis() % 1300L) * (2.0 * Math.PI / 1300.0));
        int y = Math.max(9, (int) (height * 0.19f)) + Math.round(2.0f * pulse);
        int left = x - width / 2;
        graphics.text(font, status, left, y + 2, 0xB546225E, false);
        graphics.text(font, status, left, y, 0xFFFFDEF7, false);
    }
}
