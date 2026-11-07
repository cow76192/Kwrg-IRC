package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SliderPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.GemstoneESPManager;
import me.cookie.kwrg.irc.manager.GemstoneESPManager.GemstoneType;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

import java.util.LinkedHashSet;
import java.util.Set;

public class GemstoneESPModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public boolean gemstoneEspEnabled = false;
        public int gemstoneEspRange = 64;
        public int gemstoneEspOpacity = 100;
        public Set<GemstoneType> gemstoneEspDisabledTypes = new LinkedHashSet<>();

        private void sanitize() {
            if (gemstoneEspRange < 16) gemstoneEspRange = 64;
            gemstoneEspRange = Math.clamp(gemstoneEspRange, 16, 128);
            gemstoneEspOpacity = Math.clamp(gemstoneEspOpacity, 0, 100);
            if (gemstoneEspDisabledTypes == null) gemstoneEspDisabledTypes = new LinkedHashSet<>();
            gemstoneEspDisabledTypes.remove(null);
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

        panel.child(TextInfoPaneComponent.header("显示配置 (右键重置)"));
        panel.child(SliderPaneComponent.ofInt(
                "ESP 不透明度",
                0,
                100,
                100,
                GemstoneESPManager::getOpacity,
                GemstoneESPManager::setOpacity,
                val -> val + "%"
        ));

        panel.child(TextInfoPaneComponent.header("宝石种类"));

        for (GemstoneType type : GemstoneType.values()) {
            panel.child(new PanelItemComponent(
                    type.displayName,
                    PanelItemComponent.ItemType.TOGGLE,
                    () -> {
                        GemstoneESPManager.toggleGemstone(type);
                        saveConfig();
                    },
                    null,
                    () -> GemstoneESPManager.isGemstoneEnabled(type)
            ));
        }
    }
}
