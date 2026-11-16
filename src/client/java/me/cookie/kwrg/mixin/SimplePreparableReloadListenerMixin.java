package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.render.utils.LoadingAudio;
import me.cookie.kwrg.render.utils.LoadingShaderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Mixin(SimplePreparableReloadListener.class)
public abstract class SimplePreparableReloadListenerMixin {
    @WrapOperation(method = "reload", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> kwrg$stageLoadingWork(CompletableFuture<?> ready, Consumer<Object> apply,
                                                        Executor executor, Operation<CompletableFuture<Void>> original) {
        if ((Object) this instanceof ShaderManager) {
            return ready.thenComposeAsync(configs -> {
                LoadingAudio.preload(Minecraft.getInstance());
                return LoadingShaderPipelines.stage((ShaderManager.Configs) configs, () -> apply.accept(configs), executor);
            }, executor);
        }

        if ((Object) this instanceof SoundManager manager) {
            return ready.thenComposeAsync(sounds -> LoadingAudio.stage(manager, () -> apply.accept(sounds), executor), executor);
        }
        return original.call(ready, apply, executor);
    }
}
