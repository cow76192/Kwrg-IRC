package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.ColorSettingComponent;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.manager.GuiColorManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ColorUtil;

public class GuiColorModule extends Module {

    public GuiColorModule() {
        super("guiColor", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new ColorSettingComponent(GuiColorManager.getGuiColorStr(), GuiColorManager::setGuiColor));

        TextInfoPaneComponent resetPane = new TextInfoPaneComponent(
                "重置默认",
                () -> {
                    String current = GuiColorManager.getGuiColorStr();
                    return ColorUtil.applyStyle(current, current);
                },
                (pane, click) -> {
                    GuiColorManager.resetDefault();
                    panel.clearChildren();
                    this.buildTertiaryUI(panel);
                }
        );

        panel.child(resetPane);
    }
}