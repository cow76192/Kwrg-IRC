package me.cookie.kwrg.duck;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public interface KwrgAvatarState {
    // 披风使用的是 RenderType
    RenderType kwrg$getCustomCape();
    void kwrg$setCustomCape(RenderType renderType);

    Identifier kwrg$getCustomElytra();
    void kwrg$setCustomElytra(Identifier texture);
}