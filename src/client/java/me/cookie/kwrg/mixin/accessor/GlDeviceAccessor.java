package me.cookie.kwrg.mixin.accessor;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface GlDeviceAccessor {
    @Accessor("pipelineCache")
    Map<RenderPipeline, GlRenderPipeline> kwrg$getPipelineCache();

    @Mutable
    @Accessor("pipelineCache")
    void kwrg$setPipelineCache(Map<RenderPipeline, GlRenderPipeline> cache);

    @Accessor("shaderCache")
    Map<?, GlShaderModule> kwrg$getShaderCache();

    @Mutable
    @Accessor("shaderCache")
    void kwrg$setShaderCache(Map<?, GlShaderModule> cache);
}
