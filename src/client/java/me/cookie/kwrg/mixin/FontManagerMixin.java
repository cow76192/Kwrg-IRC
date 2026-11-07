package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.GlyphProvider;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.render.LoadingFontSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Mixin(FontManager.class)
public class FontManagerMixin {
    @WrapOperation(method = "reload", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> kwrg$stageFontSets(CompletableFuture<?> ready, Consumer<Object> apply,
                                                       Executor executor, Operation<CompletableFuture<Void>> original) {
        FontManager manager = (FontManager) (Object) this;
        CompletableFuture<?> prepared = ready.thenComposeAsync(
                preparation -> LoadingFontSets.stage(Minecraft.getInstance(), manager, preparation), executor);
        Consumer<Object> publish = preparation -> LoadingFontSets.publish(manager, preparation, apply);
        return original.call(prepared, publish, executor);
    }

    @WrapOperation(method = "lambda$apply$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/FontManager;createFontSet(Lnet/minecraft/resources/Identifier;Ljava/util/List;Ljava/util/Set;)Lnet/minecraft/client/gui/font/FontSet;"))
    private FontSet kwrg$reuseFontSet(FontManager manager, Identifier id, List<GlyphProvider.Conditional> providers,
                                     Set<FontOption> options, Operation<FontSet> original) {
        FontSet staged = LoadingFontSets.take(manager, id, providers, options);
        return staged != null ? staged : original.call(manager, id, providers, options);
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void kwrg$onFontManagerReload(CallbackInfo ci) {
        FontManagerHelper.onResourceReload();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void kwrg$closeStagedFonts(CallbackInfo ci) {
        LoadingFontSets.close((FontManager) (Object) this);
    }
}
