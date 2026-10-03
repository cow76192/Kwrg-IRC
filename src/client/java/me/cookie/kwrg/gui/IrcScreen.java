package me.cookie.kwrg.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Surface;
import me.cookie.kwrg.gui.component.ParticleBackgroundComponent;
import me.cookie.kwrg.gui.component.RadialMenuComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class IrcScreen extends BaseOwoScreen<StackLayout> {

    private static IrcScreen currentInstance = null;
    private RadialMenuComponent radialMenu;
    private StackLayout rootLayout = null;
    private String directCategory = null;
    private String directModule = null;

    private final ParticleBackgroundComponent particleBackground = new ParticleBackgroundComponent();
    private Surface fullscreenBlurSurface = null;

    private static final long SCALE_ANIM_DURATION = 280L;
    private float currentScale = -1.0f;
    private float fromScale = 1.0f;
    private float targetScale = 1.0f;
    private long scaleAnimStartTime = 0L;

    private BaseUIComponent currentOverlay = null;
    private final List<io.wispforest.owo.ui.core.UIComponent> hiddenRootChildren = new ArrayList<>();

    public void setDirectOpenTarget(String category, String module) {
        this.directCategory = category;
        this.directModule = module;
    }

    public IrcScreen() {
        super(Component.literal("Kwrg IRC"));
        float initialScale = Mth.clamp(IrcConfig.get().guiScale, 0.5f, 2.0f);
        this.currentScale = initialScale;
        this.fromScale = initialScale;
        this.targetScale = initialScale;
    }

    public static @Nullable IrcScreen getCurrentScreen() {
        return currentInstance;
    }

    public static void showGui() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui != null) {
                mc.setScreen(new IrcScreen());
            }
        });
        NotificationManager.send("ClickGUI Opened");
    }

    public static void openDirectToModule(String categoryName, String moduleName) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            IrcScreen screen = new IrcScreen();
            screen.setDirectOpenTarget(categoryName, moduleName);
            mc.setScreen(screen);
        });
    }

    public void closeGui() {
        this.onClose();
    }
    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, (sizingX, sizingY) -> new StackLayout(sizingX, sizingY) {
            @Override
            public boolean isInBoundingBox(double x, double y) {
                return true;
            }

            @Override
            public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                graphics.disableScissor();
                super.draw(graphics, mouseX, mouseY, partialTicks, delta);
                var window = Minecraft.getInstance().getWindow();
                graphics.enableScissor(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            }
        });
    }

    @Override
    protected void build(StackLayout rootComponent) {
        currentInstance = this;
        this.rootLayout = rootComponent;

        rootComponent.allowOverflow(true);

        updateSurface();

        this.radialMenu = new RadialMenuComponent(this, rootComponent);
        this.radialMenu.positioning(Positioning.relative(50, 50));
        rootComponent.child(this.radialMenu);

        rootComponent.mouseScroll().subscribe((mouseX, mouseY, amount) -> {
            if (this.radialMenu != null && this.radialMenu.isPanelOpen() && this.radialMenu.getSecondaryPanel() != null) {
                var sp = this.radialMenu.getSecondaryPanel();

                if (this.radialMenu.isTertiaryOpen() && sp.getTertiaryPanel() != null) {
                    if (sp.getTertiaryPanel().handleScroll(mouseX, mouseY, amount)) {
                        return true;
                    }
                }

                if (sp.handleScroll(mouseX, mouseY, amount)) {
                    return true;
                }
            }
            return false;
        });

        rootComponent.mouseDown().subscribe((click, doubled) -> {
            if (NotificationManager.onMouseDown(click)) {
                return true;
            }

            if (this.radialMenu != null) {
                if (click.button() == 1) {
                    if (this.radialMenu.isTertiaryOpen()) {
                        boolean inside2nd = this.radialMenu.isClickInsidePanel(click.x(), click.y());
                        boolean inside3rd = this.radialMenu.isClickInsideTertiary(click.x(), click.y());
                        if (!inside2nd && !inside3rd) {
                            this.radialMenu.closeTertiaryPanel();
                            return true;
                        }
                    } else if (this.radialMenu.isPanelOpen()) {
                        if (!this.radialMenu.isClickInsidePanel(click.x(), click.y())) {
                            this.radialMenu.closeSecondaryPanel();
                            return true;
                        }
                    }
                }
            }
            return false;
        });

        if (this.directCategory != null && this.radialMenu != null) {
            this.radialMenu.openDirectly(this.directCategory, this.directModule);
            this.directCategory = null;
            this.directModule = null;
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.radialMenu != null && this.radialMenu.getSecondaryPanel() != null) {
            this.radialMenu.getSecondaryPanel().destroyTertiaryImmediately();
        }
        if (currentInstance == this) {
            currentInstance = null;
        }
    }

    public float getAnimatedScale() {
        float configTarget = Mth.clamp(IrcConfig.get().guiScale, 0.5f, 2.0f);
        long now = System.currentTimeMillis();

        if (Math.abs(configTarget - this.targetScale) > 0.001f) {
            this.fromScale = this.currentScale > 0 ? this.currentScale : configTarget;
            this.targetScale = configTarget;
            this.scaleAnimStartTime = now;
        }

        if (this.currentScale < 0) {
            this.currentScale = this.targetScale;
            return this.currentScale;
        }

        if (this.scaleAnimStartTime == 0L) {
            return this.currentScale;
        }

        long elapsed = now - this.scaleAnimStartTime;
        if (elapsed >= SCALE_ANIM_DURATION) {
            this.currentScale = this.targetScale;
            this.scaleAnimStartTime = 0L;
        } else {
            float progress = (float) elapsed / SCALE_ANIM_DURATION;
            float ease = RenderUtil.easeOutCubic(progress);
            this.currentScale = RenderUtil.lerp(ease, this.fromScale, this.targetScale);
        }

        return this.currentScale;
    }

    public double toVirtualX(double screenX) {
        float scale = getAnimatedScale();
        if (Math.abs(scale - 1.0f) < 0.001f) return screenX;
        double cx = this.width / 2.0;
        return cx + (screenX - cx) / scale;
    }

    public double toVirtualY(double screenY) {
        float scale = getAnimatedScale();
        if (Math.abs(scale - 1.0f) < 0.001f) return screenY;
        double cy = this.height / 2.0;
        return cy + (screenY - cy) / scale;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        me.cookie.kwrg.gui.util.GuiFpsLimiter.apply();

        OwoUIGraphics owoGraphics = OwoUIGraphics.of(graphics);

        if (this.fullscreenBlurSurface != null && !IrcConfig.get().disableBlur && this.rootLayout != null) {
            this.fullscreenBlurSurface.draw(owoGraphics, this.rootLayout);
        }
        this.particleBackground.drawDirect(owoGraphics, a);

        float scale = getAnimatedScale();
        float cx = this.width / 2.0f;
        float cy = this.height / 2.0f;

        int vMouseX = (int) Math.round(toVirtualX(mouseX));
        int vMouseY = (int) Math.round(toVirtualY(mouseY));

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-cx, -cy);

        super.extractRenderState(graphics, vMouseX, vMouseY, a);

        graphics.pose().popMatrix();

        NotificationManager.render(graphics, mouseX, mouseY, a);
        DynamicIslandRenderer.render(graphics, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (NotificationManager.onMouseDown(click)) {
            return true;
        }

        float scale = getAnimatedScale();
        double vx = toVirtualX(click.x());
        double vy = toVirtualY(click.y());

        MouseButtonEvent virtualClick = (Math.abs(scale - 1.0f) < 0.001f)
                ? click
                : new MouseButtonEvent(vx, vy, click.buttonInfo());

        boolean handled = super.mouseClicked(virtualClick, doubled);

        if (!handled && click.button() == 1 && this.radialMenu != null) {
            if (this.radialMenu.isTertiaryOpen()) {
                boolean inside2nd = this.radialMenu.isClickInsidePanel(vx, vy);
                boolean inside3rd = this.radialMenu.isClickInsideTertiary(vx, vy);
                if (!inside2nd && !inside3rd) {
                    this.radialMenu.closeTertiaryPanel();
                    return true;
                }
            } else if (this.radialMenu.isPanelOpen()) {
                if (!this.radialMenu.isClickInsidePanel(vx, vy)) {
                    this.radialMenu.closeSecondaryPanel();
                    return true;
                }
            }
        }

        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        float scale = getAnimatedScale();
        if (Math.abs(scale - 1.0f) < 0.001f) {
            return super.mouseReleased(click);
        }

        MouseButtonEvent virtualClick = new MouseButtonEvent(
                toVirtualX(click.x()),
                toVirtualY(click.y()),
                click.buttonInfo()
        );
        return super.mouseReleased(virtualClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        float scale = getAnimatedScale();
        if (Math.abs(scale - 1.0f) < 0.001f) {
            return super.mouseDragged(click, deltaX, deltaY);
        }

        MouseButtonEvent virtualClick = new MouseButtonEvent(
                toVirtualX(click.x()),
                toVirtualY(click.y()),
                click.buttonInfo()
        );
        return super.mouseDragged(virtualClick, deltaX / scale, deltaY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(toVirtualX(mouseX), toVirtualY(mouseY), horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(toVirtualX(mouseX), toVirtualY(mouseY));
    }

    public void updateSurface() {
        if (this.rootLayout != null) {
            this.rootLayout.surface(Surface.BLANK);

            if (IrcConfig.get().disableBlur) {
                this.fullscreenBlurSurface = null;
            } else {
                float q = Math.max(0.5f, IrcConfig.get().blurQuality);
                float s = Math.max(1.0f, IrcConfig.get().blurSize);
                this.fullscreenBlurSurface = Surface.blur(q, s);
            }
        }
    }

    public @Nullable RadialMenuComponent getRadialMenu() {
        return this.radialMenu;
    }

    public @Nullable TertiaryPanelComponent getActiveTertiaryPanel() {
        if (this.radialMenu != null && this.radialMenu.getSecondaryPanel() != null) {
            return this.radialMenu.getSecondaryPanel().getTertiaryPanel();
        }
        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.closeGui();
            return true;
        }
        return super.keyPressed(input);
    }

    public void showOverlay(BaseUIComponent overlay) {
        if (this.currentOverlay != null) {
            this.rootLayout.removeChild(this.currentOverlay);
        }
        for (io.wispforest.owo.ui.core.UIComponent child : new ArrayList<>(this.rootLayout.children())) {
            this.rootLayout.removeChild(child);
            this.hiddenRootChildren.add(child);
        }
        this.currentOverlay = overlay;
        this.rootLayout.child(overlay);
    }

    public void hideOverlay() {
        if (this.currentOverlay != null) {
            this.rootLayout.removeChild(this.currentOverlay);
            this.currentOverlay = null;
        }
        for (io.wispforest.owo.ui.core.UIComponent child : this.hiddenRootChildren) {
            this.rootLayout.child(child);
        }
        this.hiddenRootChildren.clear();
    }
}