package me.cookie.kwrg.mixin.resource;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.cookie.kwrg.duck.KwrgLanguageReload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Mixin(ResourceManagerReloadListener.class)
public interface ResourceManagerReloadListenerMixin {
    @WrapOperation(method = "reload", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> kwrg$prepareColdReload(CompletableFuture<?> ready, Runnable apply, Executor executor,
                                                          Operation<CompletableFuture<Void>> original,
                                                          @Local(argsOnly = true, ordinal = 0) Executor preparationExecutor,
                                                          @Local(argsOnly = true) PreparableReloadListener.SharedState state) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.isSameThread() || client.isGameLoadFinished()) {
            return original.call(ready, apply, executor);
        }

        if ((Object) this instanceof KwrgLanguageReload language) {
            return language.kwrg$stageLanguage(state.resourceManager(), ready, apply, preparationExecutor, executor);
        }

        Class<?> registry;
        if ((Object) this instanceof EntityRenderDispatcher) {
            registry = EntityRenderers.class;
        } else if ((Object) this instanceof BlockEntityRenderDispatcher) {
            registry = BlockEntityRenderers.class;
        } else {
            return original.call(ready, apply, executor);
        }

        // Static initialization only installs provider factories. Their instances still use vanilla's apply executor.
        var warmed = CompletableFuture.runAsync(() -> {
            try {
                MethodHandles.lookup().ensureInitialized(registry);
            } catch (IllegalAccessException exception) {
                throw new CompletionException(exception);
            }
        }, preparationExecutor);
        return original.call(CompletableFuture.allOf(ready, warmed), apply, executor);
    }
}
