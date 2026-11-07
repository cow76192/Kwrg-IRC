package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SliderPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.GemstoneESPManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public class GemstoneESPModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public boolean gemstoneEspEnabled = false;
        public int gemstoneEspRange = 64;
        public boolean gemstoneEspRuby = true;
        public boolean gemstoneEspAmber = true;
        public boolean gemstoneEspJade = true;
        public boolean gemstoneEspJasper = true;
        public boolean gemstoneEspAmethyst = true;

        private void sanitize() {
            if (gemstoneEspRange < 16) gemstoneEspRange = 64;
            gemstoneEspRange = Math.clamp(gemstoneEspRange, 16, 128);
        }
    }

    public GemstoneESPModule() {
        super("gemstoneESP", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, GemstoneESPManager::isEnabled, CONFIG);
    }

    @Override
    protected void onConfigLoaded() {
        GemstoneESPManager.onConfigLoaded();
    }

    @Override
    public void onExecute() {
        GemstoneESPManager.toggle();
        saveConfig();
    }

    private void saveConfig() {
        IrcConfig.saveDelayed(400);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderUI(panel);
    }

    private void renderUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("扫描距离配置 (最大 128 格 / 右键重置)"));
        SliderPaneComponent rangeSlider = SliderPaneComponent.ofInt(
                "检测距离",
                16,
                128,
                64,
                GemstoneESPManager::getScanRange,
                GemstoneESPManager::setScanRange,
                val -> val + " 格"
        );
        panel.child(rangeSlider);

        panel.child(TextInfoPaneComponent.header("宝石种类"));

        panel.child(new PanelItemComponent(
                "Ruby",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    GemstoneESPManager.toggleRuby();
                    saveConfig();
                },
                null,
                GemstoneESPManager::isRubyEnabled
        ));

        panel.child(new PanelItemComponent(
                "Amber",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    GemstoneESPManager.toggleAmber();
                    saveConfig();
                },
                null,
                GemstoneESPManager::isAmberEnabled
        ));

        panel.child(new PanelItemComponent(
                "Jade",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    GemstoneESPManager.toggleJade();
                    saveConfig();
                },
                null,
                GemstoneESPManager::isJadeEnabled
        ));

        panel.child(new PanelItemComponent(
                "Jasper",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    GemstoneESPManager.toggleJasper();
                    saveConfig();
                },
                null,
                GemstoneESPManager::isJasperEnabled
        ));

        panel.child(new PanelItemComponent(
                "Amethyst",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    GemstoneESPManager.toggleAmethyst();
                    saveConfig();
                },
                null,
                GemstoneESPManager::isAmethystEnabled
        ));
    }
}