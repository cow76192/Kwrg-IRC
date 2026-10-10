package me.cookie.kwrg.module.modules.style.dynamic;

import me.cookie.kwrg.gui.HudEditorScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

import java.util.function.BooleanSupplier;

public abstract class DynamicModule extends Module {

    private final String elementId;

    protected DynamicModule(String name, String elementId, PanelItemComponent.ItemType type) {
        super(name, Category.STYLE, type);
        this.elementId = elementId;
    }

    protected DynamicModule(String name, String elementId) {
        this(name, elementId, PanelItemComponent.ItemType.TOGGLE);
    }

    @Override
    public boolean isActive() {
        return elementId != null && HudElementRegistry.isEnabled(elementId);
    }

    protected boolean toggleElement() {
        if (elementId == null) return false;

        boolean enabled = !isActive();
        HudElementRegistry.get(elementId).setEnabled(enabled);
        return enabled;
    }

    protected PanelItemComponent toggle(String title, BooleanSupplier getter, Runnable setter) {
        return new PanelItemComponent(title, PanelItemComponent.ItemType.TOGGLE, setter, null, getter);
    }

    protected PanelItemComponent row(String title, Runnable action) {
        return new PanelItemComponent(title, action);
    }

    protected TextInfoPaneComponent header(String title) {
        return TextInfoPaneComponent.header(title);
    }

    protected void openEditor() {
        HudEditorScreen.open(elementId);
    }

    protected HudElement.State elementState() {
        return HudElementRegistry.state(elementId);
    }

    protected static IrcConfig config() {
        return IrcConfig.get();
    }

    protected static void save() {
        IrcConfig.save();
    }

    protected static void saveDelayed() {
        IrcConfig.saveDelayed(300);
    }
}
