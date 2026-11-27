package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.manager.ChatTypingManager;
import me.cookie.kwrg.svg.SvgImage.Draft;
import me.cookie.kwrg.svg.SvgImage;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    @Inject(method = "onEdited", at = @At("HEAD"))
    private void kwrg$onChatEdited(String newText, CallbackInfo ci) {
        ChatTypingManager.onTextChanged(newText);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void kwrg$onChatRemoved(CallbackInfo ci) {
        ChatTypingManager.onChatClosed();
        SvgImage.Draft.clear();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void kwrg$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (!SvgImage.Draft.acceptWithin(400L)) {
                ChatNotifier.send("§e[SVG] 粘贴过于频繁，请稍候", NotificationType.ERR);
                cir.setReturnValue(true);
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            SvgImage image = SvgImage.Draft.fromClipboard(mc.keyboardHandler.getClipboard());
            if (image != null) {
                SvgImage.Draft.put(image);
                String existing = this.input.getValue();
                this.input.setValue(existing == null || existing.isEmpty() ? "[svg]" : existing + " [svg]");
                ChatNotifier.send("§b[SVG] 已识别剪贴板 SVG，回车发送（来源 " + image.source().length() + " 字符）", NotificationType.INFO);
                cir.setReturnValue(true);
            }
            return;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) {
            String value = this.input.getValue();
            if ((value == null || value.isBlank()) && SvgImage.Draft.has()) {
                if (IrcClient.isIrcChatActive) {
                    IrcClient.sendMessage("");
                } else {
                    SvgImage.Draft.clear();
                    ChatNotifier.send("§e[SVG] 仅在 IRC 聊天或 IRC 频道内生效", NotificationType.ERR);
                }
                Minecraft.getInstance().setScreen(null);
                cir.setReturnValue(true);
            }
        }
    }

}
