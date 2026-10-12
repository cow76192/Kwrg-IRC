package me.cookie.kwrg.mixin.skyblocker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.tabhud.screenbuilder.WidgetManager", remap = false)
public abstract class SkyblockerUnblockMixin {

    @Inject(method = "hasFancyTab", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kwrg$fixSkyblockerBlockingVanilla(CallbackInfoReturnable<Boolean> cir) {
        try {
            Class<?> cfgClass = Class.forName("de.hysky.skyblocker.config.SkyblockerConfigManager");
            Object cfg = cfgClass.getMethod("get").invoke(null);
            Object ui = cfg.getClass().getField("uiAndVisuals").get(cfg);
            Object tabHud = ui.getClass().getField("tabHud").get(ui);
            boolean enabled = tabHud.getClass().getField("tabHudEnabled").getBoolean(tabHud);
            if (!enabled) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
