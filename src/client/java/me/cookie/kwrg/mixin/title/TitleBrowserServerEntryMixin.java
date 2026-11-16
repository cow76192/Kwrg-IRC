package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import me.cookie.kwrg.mixin.accessor.TitleBrowserServerPoolAccessor;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class TitleBrowserServerEntryMixin implements TitleBrowser.Pingable {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private ServerData serverData;

    @Invoker("lambda$extractContent$0")
    protected abstract void kwrg$pingServer();

    @Override
    public boolean kwrg$queuePing() {
        if (serverData.state() != ServerData.State.INITIAL) return false;

        TitleBrowser browser = TitleBrowser.owner(screen);
        if (browser == null) return false;

        serverData.setState(ServerData.State.PINGING);
        serverData.motd = CommonComponents.EMPTY;
        serverData.status = CommonComponents.EMPTY;

        TitleBrowserServerPoolAccessor.kwrg$workers().submit(() -> {
            if (browser.isActive(screen)) kwrg$pingServer();
        });

        return true;
    }
}
