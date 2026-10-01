package me.cookie.kwrg.mixin;

import me.cookie.kwrg.duck.KwrgAvatarState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements KwrgAvatarState {
    @Unique private RenderType kwrg$customCape = null;

    @Override
    public RenderType kwrg$getCustomCape() {
        return kwrg$customCape;
    }

    @Override
    public void kwrg$setCustomCape(RenderType renderType) {
        this.kwrg$customCape = renderType;
    }
}