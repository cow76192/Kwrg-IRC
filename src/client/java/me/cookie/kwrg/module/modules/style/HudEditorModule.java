package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.hud.HudEditorScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class HudEditorModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public Map<String, HudElement.State> hudElements = new LinkedHashMap<>();

        private void sanitize() {
            if (hudElements == null) {
                hudElements = new LinkedHashMap<>();
            } else {
                hudElements.values().removeIf(Objects::isNull);
                hudElements.values().forEach(HudElement.State::sanitize);
            }
        }
    }

    public HudEditorModule() {
        super("hudEditor", Category.STYLE, PanelItemComponent.ItemType.TRIGGER, CONFIG);
    }

    @Override
    public void onExecute() {
        openEditor(null);
    }

    private static void openEditor(String elementId) {
        HudEditorScreen.open(elementId);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("HUD 元素 (左键定位编辑)"));
        for (HudElement element : HudElementRegistry.all()) {
            panel.child(new PanelItemComponent(describe(element), () -> openEditor(element.id())));
        }

        panel.child(TextInfoPaneComponent.header("显示开关"));
        for (HudElement element : HudElementRegistry.all()) {
            panel.child(new PanelItemComponent(
                    element.displayName(),
                    PanelItemComponent.ItemType.TOGGLE,
                    () -> {
                        element.setEnabled(!element.isEnabled());
                        IrcConfig.save();
                    },
                    null,
                    element::isEnabled
            ));
        }

        panel.child(new PanelItemComponent("打开布局编辑器", () -> openEditor(null)));

        panel.child(new PanelItemComponent("全部显示", () -> {
            for (HudElement element : HudElementRegistry.all()) {
                element.setEnabled(true);
            }
            IrcConfig.save();
            ChatNotifier.send("§a所有 HUD 元素已显示", NotificationType.SUCCESS);
        }));

        panel.child(new PanelItemComponent("重置全部位置", () -> {
            HudElementRegistry.resetAll();
            ChatNotifier.send("§a所有 HUD 元素位置已重置", NotificationType.SUCCESS);
        }));
    }

    private static String describe(HudElement element) {
        HudElement.State state = HudElementRegistry.state(element.id());
        return element.displayName() + " (" + HudElement.Anchor.labelX(state.anchorX) + "/" + HudElement.Anchor.labelY(state.anchorY)
                + " " + Math.round(state.offsetX) + "," + Math.round(state.offsetY) + ")";
    }
}
