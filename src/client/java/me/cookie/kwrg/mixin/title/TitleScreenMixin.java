package me.cookie.kwrg.mixin.title;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import me.cookie.kwrg.gui.title.TitleBrowser;
import me.cookie.kwrg.gui.title.TitleMenuUi;
import me.cookie.kwrg.gui.title.TitleModeButton;
import me.cookie.kwrg.gui.title.TitleModeTransition;
import me.cookie.kwrg.render.LoadingOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen implements TitleBrowser.Host {
    @Unique private final TitleMenuUi kwrg$title = new TitleMenuUi();
    @Unique private final TitleBrowser kwrg$browser = new TitleBrowser((TitleScreen) (Object) this);
    @Unique private final TitleModeTransition kwrg$mode = new TitleModeTransition();
    @Unique private TitleModeButton kwrg$modeButton;
    @Unique private boolean kwrg$themed;
    @Unique private boolean kwrg$extractedPanorama;
    @Unique private boolean kwrg$warmedProjection;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Override
    public TitleBrowser kwrg$browser() {
        return kwrg$browser;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void kwrg$addModeButton(CallbackInfo ci) {
        kwrg$title.restoreBounds();
        kwrg$modeButton = addWidget(new TitleModeButton(width, button -> {
            if (kwrg$canSwitch() && LoadingOverlayRenderer.prepareSwitch()) kwrg$mode.start();
        }));
        kwrg$modeButton.visible = false;
    }

    @Unique
    private boolean kwrg$canSwitch() {
        return !kwrg$mode.switching() && !kwrg$browser.handlesInput() && minecraft.getOverlay() == null;
    }

    @WrapWithCondition(method = "lambda$createNormalMenuOptions$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private boolean kwrg$openWorlds(Minecraft game, Screen screen) {
        if (kwrg$mode.classic() || !LoadingOverlayRenderer.prepareBrowser()) return true;
        kwrg$browser.open(screen, false);
        return false;
    }

    @WrapWithCondition(method = "lambda$createNormalMenuOptions$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private boolean kwrg$openServers(Minecraft game, Screen screen) {
        if (kwrg$mode.classic() || !LoadingOverlayRenderer.prepareBrowser()) return true;
        kwrg$browser.open(screen, true);
        return false;
    }

    @WrapMethod(method = "extractRenderState")
    private void kwrg$extractTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                   Operation<Void> original) {
        kwrg$mode.update();
        boolean switching = kwrg$mode.switching();
        boolean classic = kwrg$mode.classicUi();
        if ((!classic || switching) && !LoadingOverlayRenderer.prepare()) {
            kwrg$title.restoreBounds();
            original.call(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (!classic && !kwrg$warmedProjection && minecraft.getOverlay() == null) {
            kwrg$warmedProjection = true;
            LoadingOverlayRenderer.prepareBrowser();
            LoadingOverlayRenderer.prepareSwitch();
        }

        if (classic) {
            kwrg$title.restoreBounds();
            if (switching) {
                kwrg$title.begin((TitleScreen) (Object) this, graphics, mouseX, mouseY, kwrg$browser, kwrg$mode, false);
                extractPanorama(graphics, partialTick);
                kwrg$extractedPanorama = true;
                var pose = graphics.pose();
                pose.pushMatrix().translate(width * 0.5f + kwrg$mode.shake() * 2.0f,
                                height * 0.5f + kwrg$mode.classicDrop())
                        .scale(kwrg$mode.classicScale()).translate(-width * 0.5f, -height * 0.5f);
                try {
                    original.call(graphics, -1, -1, partialTick);
                } finally {
                    pose.popMatrix();
                    kwrg$extractedPanorama = false;
                }
            } else {
                original.call(graphics, mouseX, mouseY, partialTick);
            }
        } else {
            kwrg$extractCustom(graphics, mouseX, mouseY, partialTick, original);
        }

        float flash = kwrg$mode.flash();
        if (flash > 0.0f) {
            graphics.nextStratum();
            graphics.fillGradient(0, 0, width, height, ((int) (255 * flash) << 24) | 0xF1FFFC,
                    ((int) (245 * flash) << 24) | 0xC2F1F5);
        }

        kwrg$modeButton.visible = !kwrg$browser.handlesInput() && minecraft.getOverlay() == null;
        kwrg$modeButton.active = kwrg$canSwitch();
        kwrg$modeButton.setClassic(classic);
        if (kwrg$modeButton.visible) {
            graphics.nextStratum();
            kwrg$modeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Unique
    private void kwrg$extractCustom(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                    Operation<Void> original) {
        kwrg$themed = true;
        TitleMenuUi previous = kwrg$title.activate();
        boolean switching = kwrg$mode.switching();
        var pose = graphics.pose();
        pose.pushMatrix().translate(-width * kwrg$mode.customSlide(), 0.0f);
        try {
            kwrg$title.begin((TitleScreen) (Object) this, graphics, switching ? -1 : mouseX,
                    switching ? -1 : mouseY, kwrg$browser, kwrg$mode, true);
            if (kwrg$mode.panorama()) extractPanorama(graphics, partialTick);
            if (kwrg$browser.menuVisibility() * kwrg$mode.customUi() > 0.001f) {
                original.call(graphics, switching ? -1 : mouseX, switching ? -1 : mouseY, partialTick);
            }
        } finally {
            pose.popMatrix();
            TitleMenuUi.restore(previous);
            kwrg$themed = false;
        }
        kwrg$browser.draw(graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kwrg$tickBrowser(CallbackInfo ci) {
        if (kwrg$browser.handlesInput()) {
            kwrg$browser.tick();
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void kwrg$suspendBrowser(CallbackInfo ci) {
        kwrg$browser.suspend();
    }

    @Inject(method = "added", at = @At("HEAD"))
    private void kwrg$returnToTitle(CallbackInfo ci) {
        kwrg$browser.returnedToTitle();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        kwrg$browser.resize(width, height);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kwrg$browserClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (kwrg$mode.switching()) {
            cir.setReturnValue(true);
            return;
        }
        if (kwrg$browser.handlesInput()) {
            Screen panel = kwrg$browser.inputPanel();
            if (panel != null) panel.mouseClicked(event, doubleClick);
            cir.setReturnValue(true);
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        Screen panel = kwrg$browser.inputPanel();
        if (panel != null) panel.mouseMoved(x, y);
        else super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.mouseReleased(event) : !kwrg$blocksInput() && super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.mouseDragged(event, x, y) : !kwrg$blocksInput() && super.mouseDragged(event, x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.mouseScrolled(x, y, scrollX, scrollY)
                : !kwrg$blocksInput() && super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (kwrg$mode.switching()) return true;
        Screen panel = kwrg$browser.inputPanel();
        if (panel != null) return panel.keyPressed(event);

        if (kwrg$browser.handlesInput()) {
            if (event.isEscape()) kwrg$browser.close();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.keyReleased(event) : !kwrg$blocksInput() && super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.charTyped(event) : !kwrg$blocksInput() && super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.preeditUpdated(event) : !kwrg$blocksInput() && super.preeditUpdated(event);
    }

    @Unique
    private boolean kwrg$blocksInput() {
        return kwrg$browser.handlesInput() || kwrg$mode.switching();
    }

    @Override
    public void afterMouseMove() {
        Screen panel = kwrg$browser.inputPanel();
        if (panel != null) panel.afterMouseMove();
        else super.afterMouseMove();
    }

    @Override
    public void afterMouseAction() {
        Screen panel = kwrg$browser.inputPanel();
        if (panel != null) panel.afterMouseAction();
        else super.afterMouseAction();
    }

    @Override
    public void afterKeyboardAction() {
        Screen panel = kwrg$browser.inputPanel();
        if (panel != null) panel.afterKeyboardAction();
        else super.afterKeyboardAction();
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
    private boolean kwrg$drawPanorama(TitleScreen screen, GuiGraphicsExtractor graphics, float tick) {
        return !kwrg$themed && !kwrg$extractedPanorama;
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private boolean kwrg$drawLogo(LogoRenderer logo, GuiGraphicsExtractor graphics, int width, float alpha) {
        return !kwrg$themed;
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"))
    private boolean kwrg$drawSplash(SplashRenderer splash, GuiGraphicsExtractor graphics, int width, Font font, float alpha) {
        return !kwrg$themed;
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void kwrg$fadeClassicWidgets(TitleScreen screen, GuiGraphicsExtractor graphics, int x, int y, float tick,
                                         Operation<Void> original) {
        if (kwrg$themed || !kwrg$mode.switching()) {
            original.call(screen, graphics, x, y, tick);
            return;
        }

        var widgets = children();
        float[] alpha = new float[widgets.size()];
        for (int i = 0; i < widgets.size(); i++) {
            if (widgets.get(i) instanceof AbstractWidget widget) {
                alpha[i] = widget.getAlpha();
                widget.setAlpha(alpha[i] * kwrg$mode.classicAlpha());
            }
        }
        try {
            original.call(screen, graphics, x, y, tick);
        } finally {
            for (int i = 0; i < widgets.size(); i++) {
                if (widgets.get(i) instanceof AbstractWidget widget) widget.setAlpha(alpha[i]);
            }
        }
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"), index = 2)
    private float kwrg$fadeLogo(float alpha) {
        return alpha * kwrg$mode.classicAlpha();
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"), index = 3)
    private float kwrg$fadeSplash(float alpha) {
        return alpha * kwrg$mode.classicAlpha();
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
    private float kwrg$fadeVersion(float alpha) {
        return alpha * (kwrg$themed ? kwrg$mode.customUi() : kwrg$mode.classicAlpha());
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private boolean kwrg$drawRealms(RealmsNotificationsScreen screen, GuiGraphicsExtractor graphics, int x, int y, float tick) {
        return !kwrg$mode.switching();
    }
}
