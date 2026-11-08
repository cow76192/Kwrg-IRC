package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Screen.class)
public abstract class TitleBrowserScreenMixin implements TitleBrowser.Embedded {
    @Unique private TitleBrowser kwrg$owner;

    @Override
    public TitleBrowser kwrg$owner() {
        return kwrg$owner;
    }

    @Override
    public void kwrg$owner(TitleBrowser browser) {
        kwrg$owner = browser;
    }
}
