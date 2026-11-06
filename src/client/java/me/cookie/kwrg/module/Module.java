package me.cookie.kwrg.module;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class Module {

    private final String name;
    private final Category category;
    private final PanelItemComponent.ItemType type;
    private final BooleanSupplier activeSupplier;
    private final ModuleConfig<?> config;

    protected Module(String name, Category category, PanelItemComponent.ItemType type,
                     BooleanSupplier activeSupplier, ModuleConfig<?> config) {
        this.name = name;
        this.category = category;
        this.type = type;
        this.activeSupplier = activeSupplier;
        this.config = config;
    }

    public Module(String name, Category category, PanelItemComponent.ItemType type, BooleanSupplier activeSupplier) {
        this(name, category, type, activeSupplier, null);
    }

    protected Module(String name, Category category, PanelItemComponent.ItemType type, ModuleConfig<?> config) {
        this(name, category, type, () -> false, config);
    }

    public Module(String name, Category category, PanelItemComponent.ItemType type) {
        this(name, category, type, () -> false);
    }

    final void registerConfig() {
        if (config != null) IrcConfig.register(config, this::onConfigLoaded);
    }

    /** Rebuilds runtime state from the active profile after the module's settings have been loaded. */
    protected void onConfigLoaded() {
    }

    public String getName() {
        return this.name;
    }

    public Category getCategory() {
        return this.category;
    }

    public PanelItemComponent.ItemType getType() {
        return this.type;
    }

    public boolean isActive() {
        return this.activeSupplier != null && this.activeSupplier.getAsBoolean();
    }

    public abstract void onExecute();

    public void buildTertiaryUI(TertiaryPanelComponent panel) {

    }

    public PanelItemComponent createPanelItem(Consumer<PanelItemComponent> openTertiaryCallback) {
        return new PanelItemComponent(this.name, this.type, this::onExecute, openTertiaryCallback, this::isActive);
    }
}
