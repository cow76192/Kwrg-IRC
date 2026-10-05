package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.HudEditorScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;

public class HudEditorModule extends Module {

    public HudEditorModule() {
        super("hudEditor", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        openEditor(null);
    }

    private static void openEditor(String elementId) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new HudEditorScreen(elementId)));
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
