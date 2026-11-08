package me.cookie.kwrg.mixin.title;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class TitleBrowserWorldEntryMixin {
    @Shadow @Final private Screen screen;

    @WrapMethod(method = "joinWorld")
    private void kwrg$diveIntoWorld(Operation<Void> original) {
        TitleBrowser browser = TitleBrowser.owner(screen);
        if (browser == null || !browser.isActive(screen)
                || !browser.dive((WorldSelectionList.WorldListEntry) (Object) this, original::call)) {
            original.call();
        }
    }
}
