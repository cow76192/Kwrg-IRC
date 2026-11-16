package me.cookie.kwrg.mixin.resource;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.duck.KwrgLanguageReload;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin implements KwrgLanguageReload {
    @Shadow @Final private static LanguageInfo DEFAULT_LANGUAGE;
    @Shadow private String currentCode;

    @Unique private PreparedLanguage kwrg$prepared;

    @Shadow
    private static Map<String, LanguageInfo> extractLanguages(Stream<PackResources> packs) {
        throw new AssertionError();
    }

    @Override
    public CompletableFuture<Void> kwrg$stageLanguage(ResourceManager resources, CompletableFuture<?> ready, Runnable apply,
                                                      Executor preparationExecutor, Executor renderExecutor) {
        String selected = currentCode;
        var preparation = CompletableFuture.supplyAsync(() -> {
            Map<String, LanguageInfo> languages = extractLanguages(resources.listPacks());
            LanguageInfo selectedInfo = "en_us".equals(selected) ? null : languages.get(selected);
            List<String> codes = selectedInfo == null ? List.of("en_us") : List.of("en_us", selected);
            boolean rightToLeft = (selectedInfo == null ? DEFAULT_LANGUAGE : selectedInfo).bidirectional();
            return new PreparedLanguage(languages, codes, rightToLeft, ClientLanguage.loadFrom(resources, codes, rightToLeft));
        }, preparationExecutor);

        return ready.thenCombine(preparation, (unused, language) -> language).thenAcceptAsync(language -> {
            if (selected.equals(currentCode)) {
                kwrg$prepared = language;
            }
            try {
                apply.run();
            } finally {
                kwrg$prepared = null;
            }
        }, renderExecutor);
    }

    @WrapOperation(method = "onResourceManagerReload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/language/LanguageManager;extractLanguages(Ljava/util/stream/Stream;)Ljava/util/Map;"))
    private Map<String, LanguageInfo> kwrg$preparedLanguages(Stream<PackResources> packs,
                                                              Operation<Map<String, LanguageInfo>> original) {
        if (kwrg$prepared == null) {
            return original.call(packs);
        }
        packs.close();
        return kwrg$prepared.languages();
    }

    @WrapOperation(method = "onResourceManagerReload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/language/ClientLanguage;loadFrom(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resources/language/ClientLanguage;"))
    private ClientLanguage kwrg$preparedTranslations(ResourceManager resources, List<String> codes, boolean rightToLeft,
                                                     Operation<ClientLanguage> original) {
        if (kwrg$prepared != null && kwrg$prepared.codes().equals(codes) && kwrg$prepared.rightToLeft() == rightToLeft) {
            return kwrg$prepared.language();
        }
        return original.call(resources, codes, rightToLeft);
    }

    @Unique
    private record PreparedLanguage(Map<String, LanguageInfo> languages, List<String> codes, boolean rightToLeft,
                                    ClientLanguage language) {
    }
}
