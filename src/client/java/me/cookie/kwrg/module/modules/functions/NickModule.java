package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.NickManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.network.chat.Component;

public class NickModule extends Module {

    public NickModule() {
        super(
                "Nick",
                Category.FUNCTIONS,
                PanelItemComponent.ItemType.TOGGLE,
                NickManager::isEnabled
        );
    }

    @Override
    public void onExecute() {
        boolean newState = !NickManager.isEnabled();
        if (newState && NickManager.getCustomNick().isEmpty()) {
            ChatNotifier.send("§c请先在右侧面板中输入自定义昵称！", NotificationType.WARN);
            return;
        }
        NickManager.setEnabled(newState);
        ChatNotifier.send(
                newState ? "§a[Nick] 已开启，伪装为: §f" + NickManager.getCustomNick() : "§c[Nick] 已关闭",
                newState ? NotificationType.SUCCESS : NotificationType.INFO
        );
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(TextInfoPaneComponent.header("§b§l自定义昵称 (Nick)"));

        panel.child(new TextInfoPaneComponent(
                "原名",
                () -> Component.literal(NickManager.getProtectedRealName()),
                (pane, click) -> {
                    String realName = NickManager.getRealPlayerName();
                    if (!realName.isEmpty()) {
                        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(realName);
                        ChatNotifier.send("§a已复制 原名: §f" + NickManager.getProtectedRealName(), NotificationType.SUCCESS);
                    }
                }
        ));


        panel.child(new TextInfoPaneComponent(
                "当前伪装",
                () -> NickManager.getCustomNick().isEmpty() ? "§7未设置" : "§a" + NickManager.getCustomNick(),
                false,
                false
        ));

        TextInputPaneComponent inputPane = new TextInputPaneComponent(
                "输入自定义昵称...",
                newNick -> {
                    if (newNick != null && !newNick.isBlank()) {
                        NickManager.setCustomNick(newNick.trim());
                        NickManager.setEnabled(true);
                        ChatNotifier.send("§a[Nick] 昵称已保存并生效: §f" + newNick, NotificationType.SUCCESS);
                    }
                },
                "Enter保存"
        );

        if (!NickManager.getCustomNick().isEmpty()) {
            inputPane.setText(NickManager.getCustomNick());
        }

        inputPane.onTextChanged(NickManager::setCustomNick);
        panel.child(inputPane);

        panel.child(new TextInfoPaneComponent(
                "§c重置昵称",
                () -> Component.literal("§7[点击清除]"),
                () -> {
                    NickManager.setCustomNick("");
                    NickManager.setEnabled(false);
                    inputPane.setText("");
                    ChatNotifier.send("§e[Nick] 已清除自定义昵称", NotificationType.INFO);
                }
        ));
    }
}