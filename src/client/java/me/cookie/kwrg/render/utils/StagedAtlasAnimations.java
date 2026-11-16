package me.cookie.kwrg.render.utils;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class StagedAtlasAnimations implements AutoCloseable {
    private final SpriteLoader.Preparations preparations;
    private final List<TextureAtlasSprite> sprites;
    private final Map<TextureAtlasSprite, SpriteContents.AnimationState> states = new IdentityHashMap<>();
    private GpuBuffer uniforms;
    private int stride;
    private int bytesPerSprite;
    private int nextSprite;

    StagedAtlasAnimations(SpriteLoader.Preparations preparations) {
        this.preparations = preparations;
        sprites = preparations.regions().values().stream().filter(sprite -> sprite.contents().isAnimated()).toList();
    }

    long pixels() {
        return sprites.stream().map(TextureAtlasSprite::contents)
                .mapToLong(contents -> (long) contents.width() * contents.height() * contents.getUniqueFrames().size())
                .sum();
    }

    boolean advance() {
        if (nextSprite == sprites.size()) {
            return true;
        }

        if (uniforms == null) {
            allocateUniforms();
            return false;
        }

        TextureAtlasSprite sprite = sprites.get(nextSprite);
        var slice = uniforms.slice((long) nextSprite * bytesPerSprite, bytesPerSprite);
        states.put(sprite, sprite.createAnimationState(slice, stride));
        return ++nextSprite == sprites.size();
    }

    private void allocateUniforms() {
        var device = RenderSystem.getDevice();
        stride = Mth.roundToward(SpriteContents.UBO_SIZE, device.getUniformOffsetAlignment());

        int mip = preparations.mipLevel();
        bytesPerSprite = stride * (mip + 1);
        ByteBuffer data = MemoryUtil.memAlloc(Math.multiplyExact(bytesPerSprite, sprites.size()));
        try {
            for (int index = 0; index < sprites.size(); index++) {
                sprites.get(index).uploadSpriteUbo(data, index * bytesPerSprite, mip,
                        preparations.width(), preparations.height(), stride);
            }
            uniforms = device.createBuffer(() -> "KWRG staged atlas animation uniforms", GpuBuffer.USAGE_UNIFORM, data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    public GpuBuffer uniforms() {
        return uniforms;
    }

    public SpriteContents.AnimationState state(TextureAtlasSprite sprite) {
        return states.get(sprite);
    }

    public void transferOwnership() {
        uniforms = null;
        states.clear();
    }

    @Override
    public void close() {
        for (var state : states.values()) {
            if (state != null) {
                state.close();
            }
        }

        if (uniforms != null) {
            uniforms.close();
        }
        transferOwnership();
    }
}
