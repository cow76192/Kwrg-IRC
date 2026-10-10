package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.TabListRenderer", remap = false)
public abstract class SkyHanniTabMixin {

    @Unique private boolean kwrg$translated = false;

    @Inject(method = "drawTabList", at = @At("HEAD"), require = 0)
    private void kwrg$onDrawTabListHead(CallbackInfo ci) {
        DynamicIslandRenderer.setSkyHanniInstance(this);
        DynamicIslandRenderer.markSkyHanniTabRendered();
        DynamicIslandRenderer.renderTabBackgroundDirect();
        DynamicIslandRenderer.beginTabScissorDirect();

        float shiftX = DynamicIslandRenderer.getTabShiftX();
        float shiftY = DynamicIslandRenderer.getTabShiftY();
        if (Math.abs(shiftX) > 0.01f || Math.abs(shiftY) > 0.01f) {
            kwrg$translated = DynamicIslandRenderer.pushTabContentShift(1.0f);
        }
    }

    @Inject(method = "drawTabList", at = @At("RETURN"), require = 0)
    private void kwrg$onDrawTabListReturn(CallbackInfo ci) {
        if (kwrg$translated) {
            kwrg$translated = false;
            DynamicIslandRenderer.popTabContentShift();
        }
        DynamicIslandRenderer.endTabScissorDirect();
    }

    @WrapOperation(
            method = "drawTabList",
            at = @At(
                    value = "INVOKE",
                    target = "Lat/hannibal2/skyhanni/features/misc/compacttablist/TabListRenderer;drawColumns(IILjava/util/List;)V"
            ), require = 0)
    private void kwrg$onDrawColumns(@Coerce Object instance, int x, int headerY, List<?> columns, Operation<Void> original) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int totalWidth = Math.max(160, (screenW / 2 - x) * 2);

        int maxLines = 14;
        if (columns != null) {
            for (Object col : columns) {
                try {
                    int size = (int) col.getClass().getMethod("size").invoke(col);
                    maxLines = Math.max(maxLines, size);
                } catch (Throwable ignored) {}
            }
        }
        int totalHeight = maxLines * 9 + 26;

        DynamicIslandRenderer.updateTargetTabBounds(totalWidth + 18.0f, totalHeight, 7.0f);
        original.call(instance, x, headerY, columns);
    }


    @WrapOperation(
            method = "drawTabList",
            at = @At(
                    value = "INVOKE",
                    target = "Lat/hannibal2/skyhanni/utils/GuiRenderUtils;drawRect(IIIII)V"
            ), require = 0)
    private void kwrg$suppressOuterBlackRect(@Coerce Object utils, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (DynamicIslandRenderer.isTabFeatureOn() && DynamicIslandRenderer.getTabProgress() > 0.005f) {
            return;
        }
        original.call(utils, x1, y1, x2, y2, color);
    }

    @WrapOperation(
            method = "drawColumns",
            at = @At(
                    value = "INVOKE",
                    target = "Lat/hannibal2/skyhanni/utils/GuiRenderUtils;drawRect(IIIII)V"
            ), require = 0)
    private void kwrg$suppressColumnGreyRect(@Coerce Object utils, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (DynamicIslandRenderer.isTabFeatureOn() && DynamicIslandRenderer.getTabProgress() > 0.005f) {
            return;
        }
        original.call(utils, x1, y1, x2, y2, color);
    }
}
