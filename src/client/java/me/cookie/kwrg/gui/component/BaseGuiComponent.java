package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import me.cookie.kwrg.gui.theme.GuiThemeService;

public abstract class BaseGuiComponent extends BaseUIComponent {

    protected int circleBg() { return GuiThemeService.circleBg(); }
    protected int paneBorder() { return GuiThemeService.paneBorder(); }
    protected int rowBg() { return GuiThemeService.rowBg(); }
    protected int rowBorder() { return GuiThemeService.rowBorder(); }
    protected int textStrong() { return GuiThemeService.textStrong(); }
    protected int toggleOff() { return GuiThemeService.toggleOff(); }
    protected int toggleOn() { return GuiThemeService.toggleOn(); }
    protected int dotHover() { return GuiThemeService.dotHover(); }
}
