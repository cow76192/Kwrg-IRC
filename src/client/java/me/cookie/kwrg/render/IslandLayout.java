package me.cookie.kwrg.render;

import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class IslandLayout {

    private IslandLayout() {
    }

    public static float resolveIslandCenterX(float width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return width / 2.0f;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.ISLAND);
        return HudElement.Anchor.resolveCenterX(state.anchorX, state.marginX,
                HudElementRegistry.resolveOffsetX(HudElementRegistry.ISLAND), width,
                mc.getWindow().getGuiScaledWidth());
    }

    public static float resolveIslandTopY(float height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return 0.0f;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.ISLAND);
        return HudElement.Anchor.resolveY(state.anchorY, state.marginY,
                HudElementRegistry.resolveOffsetY(HudElementRegistry.ISLAND), height,
                mc.getWindow().getGuiScaledHeight());
    }

    public static float tabShiftX(float targetW, float screenW) {
        if (screenW <= targetW) return 0.0f;
        return Mth.clamp(resolveIslandCenterX(targetW) - screenW / 2.0f,
                targetW / 2.0f - screenW / 2.0f, screenW / 2.0f - targetW / 2.0f);
    }

    public static float tabShiftY(float targetH, float screenH, float smoothTabY, float baseTopY) {
        if (screenH <= targetH) return 0.0f;
        return Mth.clamp(resolveIslandTopY(targetH) - baseTopY, -smoothTabY, screenH - targetH - smoothTabY);
    }
}
