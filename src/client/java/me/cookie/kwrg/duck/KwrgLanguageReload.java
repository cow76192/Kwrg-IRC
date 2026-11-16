package me.cookie.kwrg.duck;

import net.minecraft.server.packs.resources.ResourceManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface KwrgLanguageReload {
    CompletableFuture<Void> kwrg$stageLanguage(ResourceManager resources, CompletableFuture<?> ready, Runnable apply,
                                               Executor preparationExecutor, Executor renderExecutor);
}
