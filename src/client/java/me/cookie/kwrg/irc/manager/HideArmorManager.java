package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HideArmorManager {

    private static final Set<String> HIDDEN_ARMOR_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Field> NAME_FIELD_CACHE = new ConcurrentHashMap<>();

    public static boolean shouldHideArmor(net.minecraft.world.entity.player.Player player) {
        if (player == null) return false;

        Minecraft mc = Minecraft.getInstance();
        String cleanName = player.getGameProfile().name();

        if (mc.getUser() != null && cleanName.equalsIgnoreCase(mc.getUser().getName())) {
            return IrcConfig.get().hideArmor;
        }

        return HIDDEN_ARMOR_PLAYERS.contains(cleanName.toLowerCase());
    }

    private static String extractPlayerName(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        Class<?> clazz = state.getClass();
        java.lang.reflect.Field cachedField = NAME_FIELD_CACHE.get(clazz);

        if (cachedField != null) {
            try {
                return getStringFromField(cachedField, state);
            } catch (Exception ignored) {
            }
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field f : current.getDeclaredFields()) {
                String name = f.getName().toLowerCase();
                if (name.contains("name") || name.contains("profile") || name.contains("username")) {
                    f.setAccessible(true);
                    try {
                        String extracted = getStringFromField(f, state);
                        if (extracted != null && !extracted.isBlank()) {
                            NAME_FIELD_CACHE.put(clazz, f);
                            return extracted;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String getStringFromField(Field f, Object obj) throws Exception {
        Object val = f.get(obj);
        if (val instanceof String s) {
            return s;
        } else if (val instanceof Component c) {
            return c.getString();
        } else if (val instanceof com.mojang.authlib.GameProfile gp) {
            return gp.name();
        }
        return null;
    }

    public static void setRemotePlayerHideArmor(String username, boolean hidden) {
        if (username == null || username.isBlank()) return;
        if (hidden) {
            HIDDEN_ARMOR_PLAYERS.add(username.toLowerCase());
        } else {
            HIDDEN_ARMOR_PLAYERS.remove(username.toLowerCase());
        }
    }

    public static void setPlayerHideArmor(String username, boolean hidden) {
        if (username == null || username.isBlank()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && username.equalsIgnoreCase(mc.getUser().getName())) {
            IrcConfig.get().hideArmor = hidden;
            IrcConfig.save();
            ChatNotifier.send("§a[IRC] 你的 hideArmor 状态已同步更新为: " + (hidden ? "§a开启" : "§c关闭"));
        }

        if (hidden) {
            HIDDEN_ARMOR_PLAYERS.add(username.toLowerCase());
        } else {
            HIDDEN_ARMOR_PLAYERS.remove(username.toLowerCase());
        }
    }

    public static void clear() {
        HIDDEN_ARMOR_PLAYERS.clear();
    }
}