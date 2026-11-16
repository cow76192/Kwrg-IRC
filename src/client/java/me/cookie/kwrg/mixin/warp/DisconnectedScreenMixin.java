package me.cookie.kwrg.mixin.warp;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.gui.title.PortalReturnButton;
import me.cookie.kwrg.gui.title.ServerRetry;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow @Final private LinearLayout layout;
    @Shadow @Final private Screen parent;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button$Builder;build()Lnet/minecraft/client/gui/components/Button;"))
    private Button kwrg$styleReturnButton(Button.Builder builder, Operation<Button> original) {
        Button button = original.call(builder);
        return ServerPortalRenderer.fail(this, parent) ? new PortalReturnButton(button) : button;
    }

    @Inject(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V"))
    private void kwrg$addRetry(CallbackInfo ci) {
        ServerRetry target = ServerRetry.current();
        if (target == null || !minecraft.allowsMultiplayer()) return;
        Component label = Component.translatable("kwrg.warp.retry");
        Button button = Button.builder(label, ignored -> {
            if (!ServerPortalRenderer.retryFailure(this, target)) target.connect(minecraft);
        }).width(200).build();
        layout.addChild(ServerPortalRenderer.failure(this) ? new PortalReturnButton(button, label) : button);
    }

    @Override
    public void tick() {
        super.tick();
        ServerPortalRenderer.tickFailure(minecraft, this);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return kwrg$blocksCardInput() || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return kwrg$blocksCardInput() || super.keyPressed(event);
    }

    @Unique
    private boolean kwrg$blocksCardInput() {
        return ServerPortalRenderer.failure(this)
                && (ServerPortalRenderer.retrying(this) || ServerPortalRenderer.failureReveal() <= 0.18f);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void kwrg$positionCard(CallbackInfo ci) {
        if (ServerPortalRenderer.failure(this)) kwrg$liftCard();
    }

    @Unique
    private void kwrg$liftCard() {
        int centered = (height - layout.getHeight()) / 2;
        layout.setY(Math.max(6, centered - Math.min(50, height / 5)));
    }

    @Inject(method = {"lambda$init$3", "lambda$init$4"}, at = @At("HEAD"), cancellable = true)
    private void kwrg$rewindPortal(Button button, CallbackInfo ci) {
        if (ServerPortalRenderer.rewindFailure(minecraft, this)) ci.cancel();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        if (!ServerPortalRenderer.failure(this)) {
            super.extractBackground(graphics, mouseX, mouseY, tick);
            return;
        }

        ServerPortalRenderer.failureFrame();
        float scale = ServerPortalRenderer.failureCardScale();
        if (scale < 0.01f) return;

        kwrg$pushCardPose(graphics, scale);

        int x = Math.max(4, layout.getX() - 10);
        int y = Math.max(4, layout.getY() - 6);
        int right = Math.min(width - 4, layout.getX() + layout.getWidth() + 10);
        int bottom = Math.min(height - 4, layout.getY() + layout.getHeight() + 6);
        float reveal = ServerPortalRenderer.failureReveal();

        graphics.fillGradient(x, y, right, bottom, ((int) (198 * reveal) << 24) | 0x27232E,
                ((int) (221 * reveal) << 24) | 0x10141D);
        graphics.outline(x, y, right - x, bottom - y, ((int) (138 * reveal) << 24) | 0x855767);
        graphics.fill(x + 1, y + 1, Math.min(x + 30, right), y + 2, ((int) (240 * reveal) << 24) | 0xEF7684);
        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        if (!ServerPortalRenderer.failure(this)) {
            super.extractRenderState(graphics, mouseX, mouseY, tick);
            return;
        }

        float scale = ServerPortalRenderer.failureCardScale();
        if (scale < 0.01f || ServerPortalRenderer.failureReveal() <= 0.18f) return;

        kwrg$pushCardPose(graphics, scale);
        super.extractRenderState(graphics, scale < 1.0f ? -1 : mouseX, scale < 1.0f ? -1 : mouseY, tick);
        graphics.pose().popMatrix();
    }

    @Unique
    private void kwrg$pushCardPose(GuiGraphicsExtractor graphics, float scale) {
        float x = layout.getX() + layout.getWidth() * 0.5f;
        float y = layout.getY() + layout.getHeight() * 0.5f;
        graphics.pose().pushMatrix().translate(x, y).scale(scale).translate(-x, -y);
    }
}
