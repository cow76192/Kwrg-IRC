package me.cookie.kwrg.mixin.resource;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.GpuDevice;
import me.cookie.kwrg.render.utils.LoadingShaderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
    @WrapOperation(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;clearPipelineCache()V", ordinal = 0))
    private void kwrg$publishCompiledPipelines(GpuDevice device, Operation<Void> original,
                                             @Local(argsOnly = true) ShaderManager.Configs configs) {
        if (!LoadingShaderPipelines.install(device, configs, () -> original.call(device))) {
            original.call(device);
        }
    }
}
