package me.cookie.kwrg.mixin.title;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cookie.kwrg.gui.title.TitleBrowser;
import me.cookie.kwrg.gui.title.TitleMenuUi;
import me.cookie.kwrg.render.LoadingOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen implements TitleBrowser.Host {
    @Unique private final TitleMenuUi kwrg$title = new TitleMenuUi();
    @Unique private final TitleBrowser kwrg$browser = new TitleBrowser((TitleScreen) (Object) this);
    @Unique private boolean kwrg$themed;
    @Unique private boolean kwrg$warmedProjection;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Override
    public TitleBrowser kwrg$browser() {
        return kwrg$browser;
    }

    @WrapWithCondition(method = "lambda$createNormalMenuOptions$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private boolean kwrg$openWorlds(Minecraft game, Screen screen) {
        if (!LoadingOverlayRenderer.prepareBrowser()) return true;
        kwrg$browser.open(screen, false);
        return false;
    }

    @WrapWithCondition(method = "lambda$createNormalMenuOptions$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private boolean kwrg$openServers(Minecraft game, Screen screen) {
        if (!LoadingOverlayRenderer.prepareBrowser()) return true;
        kwrg$browser.open(screen, true);
        return false;
    }

    @WrapMethod(method = "extractRenderState")
    private void kwrg$extractTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                   Operation<Void> original) {
        if (!LoadingOverlayRenderer.prepare()) {
            kwrg$title.restoreBounds();
            original.call(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (!kwrg$warmedProjection && minecraft.getOverlay() == null) {
            kwrg$warmedProjection = true;
            LoadingOverlayRenderer.prepareBrowser();
        }

        kwrg$themed = true;
        TitleMenuUi previous = kwrg$title.activate();

        try {
            kwrg$title.begin((TitleScreen) (Object) this, graphics, mouseX, mouseY, kwrg$browser);
            if (kwrg$browser.menuVisibility() > 0.001f) {
                original.call(graphics, mouseX, mouseY, partialTick);
            }

            kwrg$browser.draw(graphics, mouseX, mouseY, partialTick);
        } finally {
            TitleMenuUi.restore(previous);
            kwrg$themed = false;
        }
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
        return panel != null ? panel.mouseReleased(event) : !kwrg$browser.handlesInput() && super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.mouseDragged(event, x, y) : !kwrg$browser.handlesInput() && super.mouseDragged(event, x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.mouseScrolled(x, y, scrollX, scrollY)
                : !kwrg$browser.handlesInput() && super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
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
        return panel != null ? panel.keyReleased(event) : !kwrg$browser.handlesInput() && super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.charTyped(event) : !kwrg$browser.handlesInput() && super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        Screen panel = kwrg$browser.inputPanel();
        return panel != null ? panel.preeditUpdated(event) : !kwrg$browser.handlesInput() && super.preeditUpdated(event);
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
        return !kwrg$themed;
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
}
