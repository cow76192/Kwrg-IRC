package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import me.cookie.kwrg.module.modules.functions.AutoPurchasePassModule;
import me.cookie.kwrg.module.modules.functions.ChatSuffixModule;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.EmojiUtil; // 导入新工具类
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        if (IrcClient.isIrcChatActive) {
            IrcClient.sendMessage(message);
            ci.cancel();
            return;
        }
        if (me.cookie.kwrg.svg.SvgImage.Draft.has()) {
            me.cookie.kwrg.svg.SvgImage.Draft.clear();
            ChatNotifier.send("§e[SVG] 仅在 IRC 聊天或 IRC 频道内生效，本条按文本 [svg] 发出", NotificationType.ERR);
        }
    }

    @ModifyVariable(method = "sendChat", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String kwrg$appendPublicChatSuffix(String message) {
        if (IrcClient.isIrcChatActive) return message;
        String text = me.cookie.kwrg.svg.SvgImage.Draft.has() ? message.replace("[svg]", "").trim() : message;
        String processed = EmojiUtil.replaceShortcodes(text);
        processed = ChatNotifier.expandCoordsShortcut(processed);
        return ChatSuffixModule.applyPublicSuffixSafely(processed);
    }

    @ModifyVariable(method = "sendCommand", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String kwrg$appendPublicCommandSuffix(String command) {
        String processed = EmojiUtil.replaceShortcodes(command);
        return ChatSuffixModule.applyPublicCommandSuffixSafely(processed);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        PlayerFinderManager.onCommandSent(command);

        String lower = command.trim().toLowerCase();
        if (lower.startsWith("svg ")) {
            if (!IrcClient.isIrcChatActive) {
                ChatNotifier.send("§e[SVG] 仅在 IRC 聊天或 IRC 频道内生效", NotificationType.ERR);
                return;
            }
            String path = command.trim().substring(4).trim();
            me.cookie.kwrg.svg.SvgImage image = me.cookie.kwrg.svg.SvgImage.Draft.fromPath(path);
            if (image == null) {
                ChatNotifier.send("§c[SVG] 读取失败: " + path, NotificationType.ERR);
            } else {
                me.cookie.kwrg.svg.SvgImage.Draft.put(image);
                IrcClient.sendMessage("");
            }
            ci.cancel();
            return;
        }
        if (lower.equals("ac") || lower.startsWith("ac ") ||
                lower.equals("cc") || lower.startsWith("cc ") ||
                lower.equals("pc") || lower.startsWith("pc ")) {
            return;
        }

        if (lower.startsWith("chat ") || lower.startsWith("msg ") || lower.startsWith("message ")) {
            String sub = lower.substring(5).trim();
            if (sub.equals("irc")) {
                IrcClient.isIrcChatActive = true;
                ChatNotifier.sendInChatScreen("§a[IRC] 已切换至 IRC 聊天频道!");
                ci.cancel();
            } else {
                if (IrcClient.isIrcChatActive) {
                    IrcClient.isIrcChatActive = false;
                    ChatNotifier.sendInChatScreen("§e[IRC] 已退出 IRC 频道");
                }
            }
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void onHandleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet == null || packet.content() == null) return;

        AutoPurchasePassModule.onSystemChat((ClientPacketListener) (Object) this, packet.content());

        if (PlayerFinderManager.processSystemChat(packet.content().getString())) {
            ci.cancel();
        }
    }
}
