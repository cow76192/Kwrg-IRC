package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.AntiNickManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class AntiNickModule extends Module {
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public boolean antiNickEnabled = false;
    }

    public AntiNickModule() {
        super("antiNick", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> CONFIG.get().antiNickEnabled, CONFIG);
    }

    @Override
    public void onExecute() {
        setEnabled(!CONFIG.get().antiNickEnabled);
    }

    public static int setEnabled(boolean enabled) {
        CONFIG.update(settings -> settings.antiNickEnabled = enabled);
        AntiNickManager.reset();
        ChatNotifier.send("§b[AntiNick] §f已" + (enabled ? "开启" : "关闭"), NotificationType.INFO);
        return 1;
    }

    @Override
    protected void onConfigLoaded() {
        AntiNickManager.reset();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();
        panel.child(TextInfoPaneComponent.header("Hypixel nick 检测"));
        panel.child(new PanelItemComponent("扫描当前大厅", () -> AntiNickManager.scanNow()));

    }
}
