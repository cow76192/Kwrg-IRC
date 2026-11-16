package me.cookie.kwrg.mixin.accessor;

import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.ThreadPoolExecutor;

@Mixin(ServerSelectionList.class)
public interface TitleBrowserServerPoolAccessor {
    @Accessor("THREAD_POOL")
    static ThreadPoolExecutor kwrg$workers() {
        throw new AssertionError();
    }
}
