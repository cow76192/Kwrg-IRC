package me.cookie.kwrg.gui.title;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class PortalReturnButton extends Button {
    private static final Component LABEL = Component.translatable("kwrg.warp.return");

    public PortalReturnButton(Button vanilla) {
        this(vanilla, LABEL);
    }

    public PortalReturnButton(Button vanilla, Component label) {
        super(vanilla.getX(), vanilla.getY(), vanilla.getWidth(), vanilla.getHeight(), label,
                button -> vanilla.onPress(null), DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        int x = getX();
        int y = getY();
        boolean hot = isActive() && isHoveredOrFocused();
        graphics.fillGradient(x, y, x + width, y + height,
                hot ? 0x88522737 : 0xA0191623, hot ? 0x7B281926 : 0x8C101018);
        graphics.outline(x, y, width, height, hot ? 0x8AF59A9A : 0x30FFFFFF);

        if (hot) {
            graphics.fill(x + 1, y + 3, x + 3, y + height - 3, 0xFFF59296);
            if (isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
        }

        var font = Minecraft.getInstance().font;
        int textX = x + (width - font.width(getMessage())) / 2;
        graphics.text(font, getMessage(), textX, y + (height - 8) / 2, 0xFFFFFFFF, false);
    }
}
