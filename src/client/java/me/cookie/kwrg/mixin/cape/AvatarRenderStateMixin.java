package me.cookie.kwrg.mixin.cape;

import me.cookie.kwrg.duck.KwrgAvatarState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements KwrgAvatarState {
    @Unique private RenderType kwrg$customCape = null;
    @Unique private Identifier kwrg$customElytra = null;

    @Override
    public RenderType kwrg$getCustomCape() {
        return kwrg$customCape;
    }

    @Override
    public void kwrg$setCustomCape(RenderType renderType) {
        this.kwrg$customCape = renderType;
    }

    @Override
    public Identifier kwrg$getCustomElytra() {
        return kwrg$customElytra;
    }

    @Override
    public void kwrg$setCustomElytra(Identifier texture) {
        this.kwrg$customElytra = texture;
    }
}