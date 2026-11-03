package me.cookie.kwrg.irc;

import net.minecraft.client.Minecraft;

import java.util.Objects;

public class EffectDispatcher {

    public static void triggerTotemEffect(String sender, String rawItemId) {
        if (rawItemId.startsWith("http://") || rawItemId.startsWith("https://")) {
            me.cookie.kwrg.util.TotemAnimationManager.playUrlEffect(sender, rawItemId);
            return;
        }

        if (me.cookie.kwrg.skyblock.SkyblockItemHelper.isSkyblockItem(rawItemId)) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null) return;
                net.minecraft.world.item.ItemStack itemStack = me.cookie.kwrg.skyblock.SkyblockItemHelper.resolve(rawItemId);
                mc.gameRenderer.displayItemActivation(itemStack);
                mc.player.playSound(net.minecraft.sounds.SoundEvents.TOTEM_USE, 1.0F, 1.0F);

                if (!Objects.equals(sender, "server ") && !Objects.equals(sender, "控制台管理员")) {
                    IrcMessageHandler.printMessage("§6[IRC] §e" + sender + " §a向你触发了图腾激活效果！(§f" + itemStack.getHoverName().getString() + "§a)");
                }
            });
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null || mc.gameRenderer == null) return;

            String fullId = rawItemId.contains(":") ? rawItemId : "minecraft:" + rawItemId;
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fullId);

            java.util.Optional<net.minecraft.world.item.Item> optionalItem = (id == null)
                    ? java.util.Optional.empty()
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);

            if (optionalItem.isEmpty() || (optionalItem.get() == net.minecraft.world.item.Items.AIR && !fullId.equals("minecraft:air"))) {
                String errorReason = "未知的物品 " + rawItemId;
                IrcMessageHandler.printMessage("§c[IRC] 收到来自 §e" + sender + " §c的未知物品: §e" + rawItemId);
                IrcClient.sendPlayFail(sender, rawItemId, errorReason);
                return;
            }

            net.minecraft.world.item.Item item = optionalItem.get();
            net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item);

            mc.gameRenderer.displayItemActivation(itemStack);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.TOTEM_USE, 1.0F, 1.0F);
            IrcMessageHandler.printMessage("§6[IRC] §e" + sender + " §a向你触发了图腾激活效果！(§f" + item.getName(itemStack).getString() + "§a)");
        });
    }

    public static void handlePlayFail(String receiver, String reason) {
        IrcMessageHandler.printMessage("§c[IRC] 玩家 §f" + receiver + " §c播放失败: §e" + reason);
    }
}
