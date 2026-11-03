package me.cookie.kwrg.gui.pointcollection;

import me.cookie.kwrg.gui.component.route.RouteButtonComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public record DialogMorph(float x, float y, float width, float height) {

    public static DialogMorph morphFrom(float progress, RouteButtonComponent.Bounds origin, int x, int y, int width, int height) {
        return new DialogMorph(
                Mth.lerp(progress, origin.x(), x),
                Mth.lerp(progress, origin.y(), y),
                Mth.lerp(progress, origin.width(), width),
                Mth.lerp(progress, origin.height(), height));
    }

    public static void pushMorphMatrix(GuiGraphicsExtractor graphics, DialogMorph morph, int x, int y, int width, int height) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(morph.x(), morph.y());
        graphics.pose().scale(morph.width() / width, morph.height() / height);
        graphics.pose().translate(-x, -y);
    }

    public static float dialogLabelAlpha(float progress) {
        return 1 - Mth.clamp(progress * 4, 0, 1);
    }

    public static float dialogContentAlpha(float progress) {
        return Mth.clamp((progress - 0.2f) / 0.8f, 0, 1);
    }
}
