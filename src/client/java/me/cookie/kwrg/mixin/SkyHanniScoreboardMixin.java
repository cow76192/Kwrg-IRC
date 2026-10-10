package me.cookie.kwrg.mixin;

import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import me.cookie.kwrg.util.ChatNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.gui.customscoreboard.CustomScoreboard", remap = false)
public abstract class SkyHanniScoreboardMixin {

    @Unique private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-scoreboard");
    @Unique private static long kwrg$lastBackgroundCheckMs = 0L;
    @Unique private static boolean kwrg$warnedForCustomBackground = false;

    @Inject(method = "onGuiRenderOverlay", at = @At("HEAD"), require = 0)
    private void kwrg$onScoreboardRenderHead(CallbackInfo ci) {
        ScoreboardIslandRenderer.beginExternalSource();
        kwrg$checkCustomBackground();
    }

    @Inject(method = "onGuiRenderOverlay", at = @At("RETURN"), require = 0)
    private void kwrg$onScoreboardRenderReturn(CallbackInfo ci) {
        ScoreboardIslandRenderer.endExternalContent();
        ScoreboardIslandRenderer.endExternalSource();
    }

    @Unique
    private static void kwrg$checkCustomBackground() {
        if (!ScoreboardIslandRenderer.isIslandEnabled()) {
            kwrg$warnedForCustomBackground = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - kwrg$lastBackgroundCheckMs < 1000L) return;
        kwrg$lastBackgroundCheckMs = now;

        if (!kwrg$hasCustomBackground()) {
            kwrg$warnedForCustomBackground = false;
            return;
        }

        if (kwrg$warnedForCustomBackground) return;
        kwrg$warnedForCustomBackground = true;

        HudElementRegistry.get(HudElementRegistry.SCOREBOARD).setEnabled(false);
        IrcConfig.save();
        LOGGER.warn("[DynamicScoreboard] SkyHanni custom background is a resource pack texture, DynamicScoreboard disabled");
        ChatNotifier.send("§cSkyHanni 自定义计分板启用了自定义背景（材质包覆盖），灵动效果无法叠加，已自动关闭 DynamicScoreboard。",
                NotificationType.WARN);
    }

    @Unique
    private static boolean kwrg$hasCustomBackground() {
        try {
            Class<?> scoreboardClass = Class.forName("at.hannibal2.skyhanni.features.gui.customscoreboard.CustomScoreboard");
            Object instance = scoreboardClass.getField("INSTANCE").get(null);

            Object config = null;
            for (Method method : scoreboardClass.getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().startsWith("getConfig")) {
                    config = method.invoke(instance);
                    break;
                }
            }
            if (config == null) return false;

            Object background = config.getClass().getMethod("getBackground").invoke(config);
            return background != null && (Boolean) background.getClass().getMethod("getEnabled").invoke(background);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
