package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ChannelModule extends Module {

    public ChannelModule() {
        super("channel", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().channelEnabled);
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        config.channelEnabled = !config.channelEnabled;
        IrcConfig.save();

        IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled, config.savedChannels);

        if (config.channelEnabled) {
            int count = config.savedChannels != null ? config.savedChannels.size() : 0;
            ChatNotifier.send("§a[IRC] 频道消息监听已: §a开启 §7(接收 " + count + " 个频道的消息)", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§e[IRC] 频道消息监听已: §c关闭 §7", NotificationType.ERR);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderChannelUI(panel, null);
    }

    private void renderChannelUI(TertiaryPanelComponent panel, String newlyAddedChannel) {
        panel.clearChildren();

        panel.child(new TextInputPaneComponent("输入频道名称 (回车保存)...", name -> {
            String cleanName = name.trim();
            if (!cleanName.isEmpty()) {
                addSavedChannel(cleanName);
                renderChannelUI(panel, cleanName);
            }
        }));

        panel.child(new TextInfoPaneComponent(
                "当前发言目标",
                () -> {
                    IrcConfig cfg = IrcConfig.get();
                    if (cfg.channelEnabled && cfg.currentChannel != null && !cfg.currentChannel.isBlank()) {
                        return Component.literal("§b#" + cfg.currentChannel);
                    }
                    return Component.literal("§e公共大厅");
                },
                false
        ));

        panel.child(TextInfoPaneComponent.header("使用 ci 指令行为"));

        panel.child(new PanelItemComponent("直接连接公共频道", PanelItemComponent.ItemType.TOGGLE, (btn, click) -> {
            IrcConfig config = IrcConfig.get();
            config.connectPublicChannelFirst = !config.connectPublicChannelFirst;
            IrcConfig.save();
            btn.setToggled(config.connectPublicChannelFirst);
        }, null, () -> IrcConfig.get().connectPublicChannelFirst));

        panel.child(TextInfoPaneComponent.header("已保存频道"));

        IrcConfig config = IrcConfig.get();
        List<String> channels = config.savedChannels != null ? config.savedChannels : new ArrayList<>();

        if (channels.isEmpty()) {
            panel.child(new PanelItemComponent("暂无已保存频道"));
        } else {
            for (String ch : new ArrayList<>(channels)) {
                PanelItemComponent item = new PanelItemComponent(
                        ch,
                        PanelItemComponent.ItemType.TOGGLE,
                        (btn, click) -> {
                            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();

                            if (isShiftDown) {
                                btn.dismiss(() -> {
                                    deleteChannel(ch);
                                    renderChannelUI(panel, null);
                                });
                            } else {
                                toggleSpeakingChannel(ch);
                                renderChannelUI(panel, null);
                            }
                        },
                        null,
                        () -> ch.equalsIgnoreCase(IrcConfig.get().currentChannel) && IrcConfig.get().channelEnabled
                );

                if (newlyAddedChannel != null && ch.equalsIgnoreCase(newlyAddedChannel)) {
                    item.withSpawnAnimation();
                }

                panel.child(item);
            }
        }
    }

    private static void toggleSpeakingChannel(String channelName) {
        IrcConfig config = IrcConfig.get();

        if (channelName.equalsIgnoreCase(config.currentChannel) && config.channelEnabled) {
            config.currentChannel = "";
            ChatNotifier.send("§e[IRC] 已退出当前频道发言，切换至: §e公共大厅", NotificationType.INFO);
        } else {
            config.currentChannel = channelName;
            config.channelEnabled = true;
            ChatNotifier.send("§a[IRC] 已将发言目标切换为频道: §b#" + channelName, NotificationType.INFO);
        }

        IrcConfig.save();
        IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled, config.savedChannels);
    }

    private static void addSavedChannel(String channelName) {
        IrcConfig config = IrcConfig.get();
        if (config.savedChannels == null) config.savedChannels = new ArrayList<>();

        boolean exists = config.savedChannels.stream().anyMatch(c -> c.equalsIgnoreCase(channelName));
        if (!exists) {
            config.savedChannels.add(channelName);
            IrcConfig.save();
            IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled, config.savedChannels);
            ChatNotifier.send("§a[IRC] 已添加频道: §f" + channelName, NotificationType.INFO);
        } else {
            ChatNotifier.send("§e[IRC] 频道 §f" + channelName + " §e已存在于列表中！", NotificationType.WARN);
        }
    }

    private static void deleteChannel(String channelName) {
        IrcConfig config = IrcConfig.get();
        if (config.savedChannels != null) {
            config.savedChannels.removeIf(c -> c.equalsIgnoreCase(channelName));
        }

        if (channelName.equalsIgnoreCase(config.currentChannel)) {
            config.currentChannel = "";
            ChatNotifier.send("§e[IRC] 已删除频道: §f" + channelName, NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§e[IRC] 已取消监听频道: §f" + channelName, NotificationType.SUCCESS);
        }

        IrcConfig.save();
        IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled, config.savedChannels);
    }
}