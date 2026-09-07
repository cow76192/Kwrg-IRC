package me.cookie.kwrg.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Surface;
import me.cookie.kwrg.gui.component.ParticleBackgroundComponent;
import me.cookie.kwrg.gui.component.RadialMenuComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class IrcScreen extends BaseOwoScreen<StackLayout> {

    private static IrcScreen currentInstance = null;
    private RadialMenuComponent radialMenu;

    public IrcScreen() {
        super(Component.literal("Kwrg IRC"));
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

    public void closeGui() {
        this.onClose();
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout rootComponent) {
        currentInstance = this;

        rootComponent.surface(Surface.blur(8.0f, 16.0f));
        rootComponent.child(new ParticleBackgroundComponent());

        this.radialMenu = new RadialMenuComponent(this, rootComponent);
        this.radialMenu.positioning(Positioning.relative(50, 50));
        rootComponent.child(this.radialMenu);

        rootComponent.mouseScroll().subscribe((mouseX, mouseY, amount) -> {
            if (this.radialMenu != null) {
                if (this.radialMenu.isTertiaryOpen() && this.radialMenu.getSecondaryPanel() != null) {
                    var tp = this.radialMenu.getSecondaryPanel().getTertiaryPanel();
                    if (tp != null && tp.handleScroll(mouseX, mouseY, amount)) {
                        return true;
                    }
                } else if (this.radialMenu.isPanelOpen() && this.radialMenu.getSecondaryPanel() != null) {
                    if (this.radialMenu.getSecondaryPanel().handleScroll(mouseX, mouseY, amount)) {
                        return true;
                    }
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
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        NotificationManager.render(graphics, mouseX, mouseY, a);
        DynamicIslandRenderer.render(graphics, a);
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
    public void removed() {
        super.removed();
        if (currentInstance == this) {
            currentInstance = null;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.closeGui();
            return true;
        }
        return super.keyPressed(input);
    }
}