package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.irc.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HideArmorManager {

    private static final Map<String, Set<EquipmentSlot>> HIDDEN_ARMOR_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> NAME_FIELD_CACHE = new ConcurrentHashMap<>();

    public static boolean shouldHideArmor(Player player, EquipmentSlot slot) {
        if (player == null || slot == null) return false;

        String cleanName = player.getGameProfile().name();
        if (cleanName == null || cleanName.isBlank()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && cleanName.equalsIgnoreCase(mc.getUser().getName())) {
            return IrcConfig.get().hideArmor && isPartHidden(slot);
        }

        Set<EquipmentSlot> hidden = HIDDEN_ARMOR_PLAYERS.get(cleanName.toLowerCase(Locale.ROOT));
        return hidden != null && hidden.contains(slot);
    }

    public static boolean isPartHidden(EquipmentSlot slot) {
        IrcConfig config = IrcConfig.get();
        return switch (slot) {
            case HEAD -> config.hideArmorHead;
            case CHEST -> config.hideArmorChest;
            case LEGS -> config.hideArmorLegs;
            case FEET -> config.hideArmorFeet;
            default -> false;
        };
    }

    public static void setPartHidden(EquipmentSlot slot, boolean hidden) {
        IrcConfig config = IrcConfig.get();
        switch (slot) {
            case HEAD -> config.hideArmorHead = hidden;
            case CHEST -> config.hideArmorChest = hidden;
            case LEGS -> config.hideArmorLegs = hidden;
            case FEET -> config.hideArmorFeet = hidden;
            default -> {
            }
        }
    }

    public static boolean hasAnyPartHidden() {
        return isPartHidden(EquipmentSlot.HEAD) || isPartHidden(EquipmentSlot.CHEST)
                || isPartHidden(EquipmentSlot.LEGS) || isPartHidden(EquipmentSlot.FEET);
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

    public static void setRemotePlayerHideArmor(String username, EquipmentSlot slot, boolean hidden) {
        if (username == null || username.isBlank() || slot == null) return;

        String key = username.toLowerCase(Locale.ROOT);
        if (hidden) {
            HIDDEN_ARMOR_PLAYERS.computeIfAbsent(key, ignored -> ConcurrentHashMap.<EquipmentSlot>newKeySet()).add(slot);
            return;
        }

        Set<EquipmentSlot> slots = HIDDEN_ARMOR_PLAYERS.get(key);
        if (slots == null || !slots.remove(slot)) return;
        if (slots.isEmpty()) {
            HIDDEN_ARMOR_PLAYERS.remove(key, slots);
        }
    }

    public static void setRemotePlayerHideArmor(String username, boolean hidden) {
        setRemotePlayerHideArmor(username, EquipmentSlot.HEAD, hidden);
        setRemotePlayerHideArmor(username, EquipmentSlot.CHEST, hidden);
        setRemotePlayerHideArmor(username, EquipmentSlot.LEGS, hidden);
        setRemotePlayerHideArmor(username, EquipmentSlot.FEET, hidden);
    }

    public static void setRemotePlayerHideArmor(String username, Set<EquipmentSlot> hiddenParts) {
        if (username == null || username.isBlank() || hiddenParts == null) return;

        String key = username.toLowerCase(Locale.ROOT);
        if (hiddenParts.isEmpty()) {
            HIDDEN_ARMOR_PLAYERS.remove(key);
        } else {
            Set<EquipmentSlot> slots = ConcurrentHashMap.newKeySet();
            slots.addAll(hiddenParts);
            HIDDEN_ARMOR_PLAYERS.put(key, slots);
        }
    }

    public static void clear() {
        HIDDEN_ARMOR_PLAYERS.clear();
    }
}
