package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.cookie.kwrg.render.LoadingAtlasUpload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Mixin(AtlasManager.class)
public abstract class AtlasManagerMixin {
    @WrapOperation(method = "reload", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> kwrg$stageAtlasUploads(CompletableFuture<?> ready, Consumer<Object> apply,
                                                          Executor executor, Operation<CompletableFuture<Void>> original,
                                                          @Local(argsOnly = true) PreparableReloadListener.SharedState state) {
        AtlasManager atlasManager = (AtlasManager) (Object) this;
        var stitches = state.get(AtlasManager.PENDING_STITCH);
        Executor stagedExecutor = task -> executor.execute(() -> {
            Minecraft client = Minecraft.getInstance();
            if (!LoadingAtlasUpload.isLoadingWithoutWorld(client)) {
                task.run();
                return;
            }
            LoadingAtlasUpload.stage(client, atlasManager, stitches, task, executor);
        });
        return original.call(ready, apply, stagedExecutor);
    }
}
