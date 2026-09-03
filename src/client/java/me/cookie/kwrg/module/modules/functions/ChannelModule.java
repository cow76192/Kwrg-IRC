package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
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

        IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled);

        if (config.channelEnabled) {
            String ch = (config.currentChannel != null && !config.currentChannel.isBlank()) ? config.currentChannel : "无";
            ChatNotifier.send("§a[IRC] 频道聊天已: §a开启 §7(当前频道: §f" + ch + "§7)");
        } else {
            ChatNotifier.send("§e[IRC] 频道聊天已: §c关闭 §7");
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderChannelUI(panel, null);
    }

    private void renderChannelUI(TertiaryPanelComponent panel, String newlyAddedChannel) {
        panel.clearChildren();

        panel.child(new TextInputPaneComponent("输入频道名称 (回车新建)...", name -> {
            String cleanName = name.trim();
            if (!cleanName.isEmpty()) {
                joinAndSaveChannel(cleanName);
                renderChannelUI(panel, cleanName);
            }
        }));

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
                                joinChannel(ch);
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

    private static void joinAndSaveChannel(String channelName) {
        IrcConfig config = IrcConfig.get();
        if (config.savedChannels == null) config.savedChannels = new ArrayList<>();

        if (!config.savedChannels.contains(channelName)) {
            config.savedChannels.add(channelName);
        }

        config.currentChannel = channelName;
        config.channelEnabled = true;
        IrcConfig.save();

        IrcClient.sendChannelSync(channelName, true);
        ChatNotifier.send("§a[IRC] 已创建并加入频道: §f" + channelName);
    }

    private static void joinChannel(String channelName) {
        IrcConfig config = IrcConfig.get();
        config.currentChannel = channelName;
        config.channelEnabled = true;
        IrcConfig.save();

        IrcClient.sendChannelSync(channelName, true);
        ChatNotifier.send("§a[IRC] 已加入频道: §f" + channelName);
    }

    private static void deleteChannel(String channelName) {
        IrcConfig config = IrcConfig.get();
        if (config.savedChannels != null) {
            config.savedChannels.remove(channelName);
        }

        if (channelName.equalsIgnoreCase(config.currentChannel)) {
            config.currentChannel = "";
            config.channelEnabled = false;
            IrcClient.sendChannelSync("", false);
            ChatNotifier.send("§e[IRC] 已删除并退出当前频道");
        } else {
            ChatNotifier.send("§e[IRC] 已删除频道: §f" + channelName);
        }

        IrcConfig.save();
    }
}