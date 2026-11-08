package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WorldSelectionList.class)
public abstract class TitleBrowserWorldListMixin {
    @Shadow private List<LevelSummary> currentlyDisplayedLevels;
    @Shadow public abstract Screen getScreen();
    @Shadow protected abstract void clearEntries();
    @Shadow public abstract void setSelected(WorldSelectionList.Entry entry);

    @Inject(method = "handleNewLevels", at = @At("HEAD"), cancellable = true)
    private void kwrg$showEmptyList(List<LevelSummary> levels, CallbackInfo ci) {
        if (levels != null && levels.isEmpty() && TitleBrowser.owner(getScreen()) != null) {
            clearEntries();
            setSelected(null);
            currentlyDisplayedLevels = levels;
            ci.cancel();
        }
    }
}
