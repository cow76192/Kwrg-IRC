package me.cookie.kwrg.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalTime;

@Mixin(Gui.class)
public abstract class GuiGreetingMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract Font getFont();

    @Unique private static boolean kwrg$hasGreeted = false;
    @Unique private static int kwrg$delayTicks = 60;
    @Unique private static int kwrg$showTicksLeft = 0;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderStateTail(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        me.cookie.kwrg.util.TotemAnimationManager.render(graphics, deltaTracker);

        if (this.minecraft.player == null || this.minecraft.level == null) {
            kwrg$hasGreeted = false;
            kwrg$delayTicks = 60;
            kwrg$showTicksLeft = 0;
            return;
        }

        String name = this.minecraft.getUser().getName();
        if (!"kwrg".equalsIgnoreCase(name)) {
            return;
        }

        if (!kwrg$hasGreeted) {
            if (kwrg$delayTicks > 0) {
                kwrg$delayTicks--;
                return;
            }
            kwrg$hasGreeted = true;
            kwrg$showTicksLeft = 80;
        }

        if (kwrg$showTicksLeft <= 0) {
            return;
        }

        kwrg$showTicksLeft--;

        float alpha = 1.0F;
        int elapsed = 130 - kwrg$showTicksLeft;
        if (elapsed < 15) {
            alpha = elapsed / 15.0F; // 前 15 ticks 淡入
        } else if (kwrg$showTicksLeft < 20) {
            alpha = kwrg$showTicksLeft / 20.0F; // 后 20 ticks 淡出
        }

        int alphaInt = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        if (alphaInt <= 0) return;

        Font font = this.getFont();
        String period = kwrg$getTimePeriod();

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2 - 25;

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) centerX, (float) centerY);

        graphics.pose().pushMatrix();
        graphics.pose().scale(2.5F, 2.5F);
        Component title = Component.literal("宝宝" + period + "好~");
        int titleWidth = font.width(title);
        int titleColor = ARGB.color(alphaInt, 255, 105, 180);
        graphics.textWithBackdrop(font, title, -titleWidth / 2, -5, titleWidth, titleColor);
        graphics.pose().popMatrix();

        graphics.pose().popMatrix();
    }

    @Unique
    private static String kwrg$getTimePeriod() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 9) {
            return "早上";
        } else if (hour >= 9 && hour < 12) {
            return "上午";
        } else if (hour >= 12 && hour < 14) {
            return "中午";
        } else if (hour >= 14 && hour < 18) {
            return "下午";
        } else {
            return "晚上";
        }
    }


}