package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.ColorSettingComponent;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.render.EntityOutlineRenderer.Target;
import me.cookie.kwrg.util.ColorUtil;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ESPModule extends Module {
    private static final String DEFAULT_ENTITY_OUTLINE_COLOR = "#00E5FF";
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public boolean espEnabled;
        public String espEntityOutlineColor = DEFAULT_ENTITY_OUTLINE_COLOR;
        public Set<Target> espHiddenTargets = new LinkedHashSet<>();

        private void sanitize() {
            if (ColorUtil.parseSingleColor(espEntityOutlineColor) == null) {
                espEntityOutlineColor = DEFAULT_ENTITY_OUTLINE_COLOR;
            }
            if (espHiddenTargets == null) espHiddenTargets = new LinkedHashSet<>();
            espHiddenTargets.remove(null);
        }
    }

    public ESPModule() {
        super("ESP", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, ESPModule::isEnabled, CONFIG);
    }

    public static boolean isEnabled() {
        return CONFIG.get().espEnabled;
    }

    public static int entityOutlineColor() {
        return ColorUtil.parseSingleColor(CONFIG.get().espEntityOutlineColor).getRGB();
    }

    public static boolean showsTarget(Target target) {
        return !CONFIG.get().espHiddenTargets.contains(target);
    }

    @Override
    public void onExecute() {
        CONFIG.update(settings -> settings.espEnabled = !settings.espEnabled);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        for (Target target : Target.values()) {
            String label = switch (target) {
                case PLAYER -> "其他玩家";
                case MONSTER -> "怪物";
                case ANIMAL -> "动物";
                case OTHER -> "其他";
            };
            panel.child(new PanelItemComponent(label, PanelItemComponent.ItemType.TOGGLE,
                    () -> CONFIG.updateDelayed(400, settings -> {
                        if (!settings.espHiddenTargets.remove(target)) settings.espHiddenTargets.add(target);
                    }), null, () -> showsTarget(target)));
        }
        panel.child(new ColorSettingComponent(CONFIG.get().espEntityOutlineColor,
                value -> CONFIG.updateDelayed(400, settings -> settings.espEntityOutlineColor = value)));
    }
}
