package me.cookie.kwrg.mixin.warp;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.gui.title.WarpCancelButton;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        if (ServerPortalRenderer.enterTerrain(this)) {
            addRenderableWidget(new WarpCancelButton(width, height, button -> ServerPortalRenderer.abortTerrain(minecraft, this)));
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void kwrg$waitForPortalEntry(CallbackInfo ci) {
        if (ServerPortalRenderer.holdTerrainForPortal(this)) ci.cancel();
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void kwrg$continueWarp(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick, CallbackInfo ci) {
        if (ServerPortalRenderer.terrain(this)) {
            ServerPortalRenderer.terrainFrame();
            ci.cancel();
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void kwrg$drawStatus(GuiGraphicsExtractor graphics, Font font, Component component, int x, int y, int color,
                                 Operation<Void> original) {
        if (ServerPortalRenderer.terrain(this)) {
            WarpCancelButton.status(graphics, ServerPortalRenderer.status(component), x, height);
        } else {
            original.call(graphics, font, component, x, y, color);
        }
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;extractChunksForRendering(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/server/level/progress/ChunkLoadStatusView;)V"))
    private boolean kwrg$drawChunkMap(GuiGraphicsExtractor graphics, int x, int y, int size, int spacing, ChunkLoadStatusView view) {
        return !ServerPortalRenderer.terrain(this);
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
    private boolean kwrg$drawProgressBar(LevelLoadingScreen screen, GuiGraphicsExtractor graphics,
                                         int x, int y, int width, int height, float progress) {
        return !ServerPortalRenderer.terrain(this);
    }
}
