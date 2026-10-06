package me.cookie.kwrg.mixin;

import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.utils.RenderUtils", remap = false)
public abstract class SkyHanniRenderUtilsMixin {

    @Unique private static Method kwrg$absXMethod;
    @Unique private static Method kwrg$absYMethod;
    @Unique private static Method kwrg$posScaleMethod;
    @Unique private static Method kwrg$widthMethod;
    @Unique private static Method kwrg$heightMethod;
    @Unique private static Method kwrg$getDrawContextMethod;
    @Unique private static Object kwrg$guiEditManager;
    @Unique private static Object kwrg$drawContextUtils;

    @Inject(method = "renderRenderable", at = @At("HEAD"), require = 0)
    private void kwrg$beforeRenderable(@Coerce Object position, @Coerce Object renderable, String label, boolean visible,
                                       CallbackInfo ci) {
        if (!ScoreboardIslandRenderer.isExternalSourceActive()) return;

        try {
            kwrg$resolveMembers(position, renderable);

            float scale = (Float) kwrg$posScaleMethod.invoke(position);
            float width = (Integer) kwrg$widthMethod.invoke(renderable) * scale;
            float height = (Integer) kwrg$heightMethod.invoke(renderable) * scale;

            float x = (Integer) kwrg$absXMethod.invoke(kwrg$guiEditManager, position);
            float y = (Integer) kwrg$absYMethod.invoke(kwrg$guiEditManager, position);

            Object graphics = kwrg$getDrawContextMethod.invoke(kwrg$drawContextUtils);
            if (!(graphics instanceof GuiGraphicsExtractor extractor)) return;

            ScoreboardIslandRenderer.renderExternalContent(extractor, x, y, width, height);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderRenderable", at = @At("RETURN"), require = 0)
    private void kwrg$afterRenderable(@Coerce Object position, @Coerce Object renderable, String label, boolean visible,
                                      CallbackInfo ci) {
        ScoreboardIslandRenderer.endExternalContent();
    }

    @Unique
    private static void kwrg$resolveMembers(Object position, Object renderable) throws Exception {
        if (kwrg$absXMethod == null) {
            Class<?> posClass = position.getClass();
            kwrg$posScaleMethod = posClass.getMethod("getEffectiveScale");

            Class<?> editClass = Class.forName("at.hannibal2.skyhanni.data.GuiEditManager");
            kwrg$guiEditManager = editClass.getField("INSTANCE").get(null);
            kwrg$absXMethod = editClass.getMethod("getAbsX", posClass);
            kwrg$absYMethod = editClass.getMethod("getAbsY", posClass);
        }

        if (kwrg$widthMethod == null) {
            kwrg$widthMethod = renderable.getClass().getMethod("getWidth");
            kwrg$heightMethod = renderable.getClass().getMethod("getHeight");
        }

        if (kwrg$getDrawContextMethod == null) {
            Class<?> utilsClass = Class.forName("at.hannibal2.skyhanni.utils.compat.DrawContextUtils");
            kwrg$drawContextUtils = utilsClass.getField("INSTANCE").get(null);
            kwrg$getDrawContextMethod = utilsClass.getMethod("getDrawContext");
        }
    }
}
