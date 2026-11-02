package me.cookie.kwrg.gui.component.waypoint;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.route.RouteButtonComponent;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class MorphContent {

    private MorphContent() {}

    public static float beginMorphContent(OwoUIGraphics graphics, float p, RouteButtonComponent.Bounds origin, Color originColor,
                                          int x, int y, int width, int height, String label) {
        float currentX = Mth.lerp(p, origin.x(), x), currentY = Mth.lerp(p, origin.y(), y);
        float currentW = Mth.lerp(p, origin.width(), width), currentH = Mth.lerp(p, origin.height(), height);

        RenderUtil.drawSubpixelRoundedPanel(
                graphics,
                currentX, currentY, currentW, currentH,
                Mth.lerp(p, 5, 10),
                AnimationUtil.lerpColor(p, originColor, Color.ofArgb(AnimationUtil.opaqueArgb(GuiThemeService.dialogBg()))),
                AnimationUtil.withAlpha(GuiThemeService.regionBorder(), 0x88)
        );

        float labelAlpha = 1 - Mth.clamp(p * 4, 0, 1);
        if (labelAlpha > 0.005f) {
            RenderUtil.drawCenteredSmoothText(
                    RenderUtil.withOpacity(graphics, labelAlpha),
                    Component.literal(label),
                    currentX + currentW / 2, currentY + currentH / 2,
                    0.8f, AnimationUtil.opaqueArgb(GuiThemeService.routeBody())
            );
        }

        float alpha = Mth.clamp((p - 0.2f) / 0.8f, 0, 1);
        if (alpha <= 0.005f) {
            return 0f;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(currentW / width, currentH / height);
        graphics.pose().translate(-x, -y);
        graphics.enableScissor(x, y, x + width, y + height);
        return alpha;
    }
}
