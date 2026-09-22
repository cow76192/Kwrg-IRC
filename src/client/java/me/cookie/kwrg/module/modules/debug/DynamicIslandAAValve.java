package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class DynamicIslandAAValve extends Module {

    public DynamicIslandAAValve() {
        super("DynamicIslandAAValve", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderUI(panel);
    }

    private void renderUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(new TextInfoPaneComponent(
                "当前 AA 值",
                () -> Component.literal("§b" + IrcConfig.get().dynamicIslandAAValue),
                true
        ));

        final TextInputPaneComponent[] aaInputHolder = new TextInputPaneComponent[1];
        aaInputHolder[0] = new TextInputPaneComponent("AA 值 (例如: 0.6 或 0.75)", input -> {
            IrcConfig config = IrcConfig.get();
            if (input == null || input.isBlank()) return;

            try {
                String clean = input.trim();
                float val = Float.parseFloat(clean);

                val = Mth.clamp(val, 0.0f, 5.0f);
                float formattedVal = Math.round(val * 100.0f) / 100.0f;

                config.dynamicIslandAAValue = formattedVal;
                IrcConfig.save();

                aaInputHolder[0].setText(String.valueOf(formattedVal));
                ChatNotifier.send("§a灵动岛 AA 值已设置为: §e" + formattedVal, NotificationType.SUCCESS);
            } catch (NumberFormatException e) {
                ChatNotifier.send("§c请输入有效的浮点数", NotificationType.WARN);
                aaInputHolder[0].setText(String.valueOf(config.dynamicIslandAAValue));
            }
        });

        aaInputHolder[0].setText(String.valueOf(IrcConfig.get().dynamicIslandAAValue));
        panel.child(aaInputHolder[0]);

        panel.child(new PanelItemComponent(
                "重置为默认值 (0.35)",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    IrcConfig.get().dynamicIslandAAValue = 0.35f;
                    IrcConfig.save();
                    aaInputHolder[0].setText("0.35");
                    ChatNotifier.send("§a[IRC] 已重置 DynamicIsland AA 值为 0.35", NotificationType.SUCCESS);
                },
                null,
                () -> false
        ));
    }
}