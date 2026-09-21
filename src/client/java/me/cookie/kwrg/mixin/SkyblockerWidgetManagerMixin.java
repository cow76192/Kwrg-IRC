package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.tabhud.screenbuilder.WidgetManager", remap = false)
public abstract class SkyblockerWidgetManagerMixin {

    @Unique private static boolean kwrg$scissored = false;

    @WrapOperation(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;isDown()Z"
            )
    )
    private static boolean kwrg$keepTabRenderingOnRelease(KeyMapping keyMapping, Operation<Boolean> original) {
        boolean down = original.call(keyMapping);
        if (down) {
            return true;
        }

        if (IrcConfig.get().dynamicIsland && DynamicIslandRenderer.isTabAnimating()) {
            Minecraft mc = Minecraft.getInstance();
            if (keyMapping == mc.options.keyPlayerList) {
                return true;
            }
        }
        return false;
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;"
            )
    )
    private static void kwrg$beforeScaleTabRender(GuiGraphicsExtractor context, boolean hud, CallbackInfo ci) {
        kwrg$scissored = false;
        if (hud || !IrcConfig.get().dynamicIsland) return;

        if (DynamicIslandRenderer.getTabProgress() > 0.005f) {
            DynamicIslandRenderer.setCurrentExtractor(context);
            DynamicIslandRenderer.markSkyblockerTabRendered();
            DynamicIslandRenderer.renderTabBackgroundDirect();
            DynamicIslandRenderer.beginTabScissorDirect();
            kwrg$scissored = true;
        }
    }


    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix3x2fStack;popMatrix()Lorg/joml/Matrix3x2fStack;",
                    shift = At.Shift.AFTER
            )
    )
    private static void kwrg$afterScaleTabRender(GuiGraphicsExtractor context, boolean hud, CallbackInfo ci) {
        if (kwrg$scissored) {
            DynamicIslandRenderer.endTabScissorDirect();
            kwrg$scissored = false;
        }
    }
}