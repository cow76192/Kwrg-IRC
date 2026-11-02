package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Unique private static boolean kwrg$prevInInventoryRendering = false;

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))
    private static void kwrg$onStartInventoryExtract(CallbackInfo ci) {
        kwrg$prevInInventoryRendering = PlayerCustomizationManager.inInventoryRendering;
        PlayerCustomizationManager.inInventoryRendering = true;
    }

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("RETURN"))
    private static void kwrg$onEndInventoryExtract(CallbackInfo ci) {
        PlayerCustomizationManager.inInventoryRendering = kwrg$prevInInventoryRendering;
    }
}