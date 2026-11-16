package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import me.cookie.kwrg.duck.KwrgAtlasStaging;
import me.cookie.kwrg.render.utils.LoadingAtlasUpload;
import me.cookie.kwrg.render.utils.StagedAtlasAnimations;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Supplier;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin extends AbstractTexture implements KwrgAtlasStaging {
    @Shadow private List<TextureAtlasSprite> sprites;

    @Unique private GpuTexture kwrg$stagedTexture;
    @Unique private StagedAtlasAnimations kwrg$stagedAnimations;
    @Unique private boolean kwrg$staticSpritesReady;

    @Invoker("createTexture")
    protected abstract void kwrg$createTexture(int width, int height, int mipLevel);

    @Invoker("uploadInitialContents")
    protected abstract void kwrg$uploadInitialContents();

    @Invoker("uploadAnimationFrames")
    protected abstract void kwrg$uploadAnimationFrames();

    @Override
    public void kwrg$allocate(SpriteLoader.Preparations preparations) {
        kwrg$createTexture(preparations.width(), preparations.height(), preparations.mipLevel());
    }

    @Override
    public void kwrg$uploadStaticBatch(List<TextureAtlasSprite> batch) {
        sprites = batch;
        try {
            kwrg$uploadInitialContents();
        } finally {
            sprites = List.of();
        }
    }

    @Override
    public GpuTexture kwrg$detachTexture() {
        GpuTexture staged = texture;
        texture = null;
        return staged;
    }

    @WrapMethod(method = "upload")
    private void kwrg$publishStagedTexture(SpriteLoader.Preparations preparations, Operation<Void> original) {
        TextureAtlas atlas = (TextureAtlas) (Object) this;
        kwrg$stagedTexture = LoadingAtlasUpload.takeTexture(atlas, preparations);
        kwrg$stagedAnimations = LoadingAtlasUpload.preparedAnimations(atlas, preparations);
        try {
            original.call(preparations);
        } finally {
            if (kwrg$stagedTexture != null) {
                kwrg$stagedTexture.close();
                kwrg$stagedTexture = null;
            }
            kwrg$staticSpritesReady = false;
            kwrg$stagedAnimations = null;
        }
    }

    @WrapOperation(method = "upload", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"))
    private GpuBuffer kwrg$reuseAnimationUniforms(GpuDevice device, Supplier<String> label, int usage, ByteBuffer data,
                                                  Operation<GpuBuffer> original) {
        return kwrg$stagedAnimations == null ? original.call(device, label, usage, data) : kwrg$stagedAnimations.uniforms();
    }

    @WrapOperation(method = "upload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;createAnimationState(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;I)Lnet/minecraft/client/renderer/texture/SpriteContents$AnimationState;"))
    private SpriteContents.AnimationState kwrg$reuseAnimationState(TextureAtlasSprite sprite, GpuBufferSlice uniforms,
                                                                 int stride, Operation<SpriteContents.AnimationState> original) {
        var staged = kwrg$stagedAnimations == null ? null : kwrg$stagedAnimations.state(sprite);
        return staged == null ? original.call(sprite, uniforms, stride) : staged;
    }

    @WrapOperation(method = "createTexture", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createTexture(Ljava/util/function/Supplier;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;"))
    private GpuTexture kwrg$reuseTexture(GpuDevice device, Supplier<String> label, int usage, TextureFormat format,
                                        int width, int height, int layers, int mipLevels, Operation<GpuTexture> original) {
        GpuTexture staged = kwrg$stagedTexture;
        if (staged == null || staged.getWidth(0) != width || staged.getHeight(0) != height
                || staged.getDepthOrLayers() != layers || staged.getMipLevels() != mipLevels
                || staged.getFormat() != format || staged.usage() != usage) {
            return original.call(device, label, usage, format, width, height, layers, mipLevels);
        }
        kwrg$stagedTexture = null;
        kwrg$staticSpritesReady = true;
        return staged;
    }

    @WrapOperation(method = "upload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlas;uploadInitialContents()V"))
    private void kwrg$uploadRemainingSprites(TextureAtlas atlas, Operation<Void> original) {
        if (kwrg$stagedAnimations != null) {
            kwrg$stagedAnimations.transferOwnership();
        }
        if (kwrg$staticSpritesReady) {
            kwrg$uploadAnimationFrames();
        } else {
            original.call(atlas);
        }
    }
}
