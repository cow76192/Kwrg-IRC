package me.cookie.kwrg.duck;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.List;

public interface KwrgAtlasStaging {
    void kwrg$allocate(SpriteLoader.Preparations preparations);

    void kwrg$uploadStaticBatch(List<TextureAtlasSprite> batch);

    GpuTexture kwrg$detachTexture();
}
