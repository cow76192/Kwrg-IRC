package me.cookie.kwrg.module;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class Module {

    private final String name;
    private final Category category;
    private final PanelItemComponent.ItemType type;
    private final BooleanSupplier activeSupplier;

    public Module(String name, Category category, PanelItemComponent.ItemType type, BooleanSupplier activeSupplier) {
        this.name = name;
        this.category = category;
        this.type = type;
        this.activeSupplier = activeSupplier;
    }

    public Module(String name, Category category, PanelItemComponent.ItemType type) {
        this(name, category, type, () -> false);
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