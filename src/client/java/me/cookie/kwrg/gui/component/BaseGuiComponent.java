package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import me.cookie.kwrg.gui.manager.GuiColorManager;

public abstract class BaseGuiComponent extends BaseUIComponent {

    protected int paneBg() { return GuiColorManager.paneBg(); }
    protected int paneBorder() { return GuiColorManager.paneBorder(); }
    protected int rowBg() { return GuiColorManager.rowBg(); }
    protected int rowBorder() { return GuiColorManager.rowBorder(); }
    protected int rowText() { return GuiColorManager.rowText(); }
    protected int textStrong() { return GuiColorManager.textStrong(); }
    protected int toggleOff() { return GuiColorManager.toggleOff(); }
    protected int toggleOn() { return GuiColorManager.toggleOn(); }
    protected int dotHover() { return GuiColorManager.dotHover(); }
}
