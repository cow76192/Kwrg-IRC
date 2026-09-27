package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;

public class AutoTrackPlayerModule extends Module {

    public AutoTrackPlayerModule() {
        super(
                "autoTrackPlayer",
                Category.FUNCTIONS,
                PanelItemComponent.ItemType.TOGGLE,
                () -> IrcConfig.get().autoTrackPlayer
        );
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        config.autoTrackPlayer = !config.autoTrackPlayer;
        IrcConfig.save();

        PlayerFinderManager.setAutoTrackEnabled(config.autoTrackPlayer);

        if (config.autoTrackPlayer) {
            ChatNotifier.send("§a已开启自动追踪队伍变化", NotificationType.SUCCESS);

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
                PlayerFinderManager.beginSilentPartyListFetch();
                mc.player.connection.sendCommand("party list");
            }
        } else {
            ChatNotifier.send("§c已关闭自动追踪队伍变化", NotificationType.ERR);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();
    }
}