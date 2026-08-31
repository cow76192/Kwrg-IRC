package me.cookie.kwrg.util;

import com.google.common.collect.Multimaps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import me.cookie.kwrg.irc.IrcMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SkyblockItemHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-skyblock");

    private static final Map<String, ItemStack> ITEM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Path> FILE_INDEX = new ConcurrentHashMap<>();
    private static final Set<String> KNOWN_IDS = ConcurrentHashMap.newKeySet();

    private static final Pattern EXTRA_ATTRIBUTES_PATTERN = Pattern.compile("ExtraAttributes\\s*:\\s*\\{([^}]+)\\}");
    private static final Pattern KV_PATTERN = Pattern.compile("(\\w+)\\s*:\\s*(?:\"([^\"]*)\"|(\\d+)(?:[bBsSlLdDfF])?|([\\w.-]+))");
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("Value\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern COLOR_PATTERN = Pattern.compile("color\\s*:\\s*(\\d+)");
    private static volatile boolean isIndexed = false;

    public static boolean isSkyblockItem(String str) {
        if (str == null) return false;
        String s = str.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("skyblock:") || s.startsWith("sb:");
    }

    public static ItemStack resolve(String rawInput) {
        String clean = rawInput.trim();
        int colon = clean.indexOf(':');
        String sbId = (colon >= 0 ? clean.substring(colon + 1) : clean).toUpperCase(Locale.ROOT);

        if (!isIndexed) {
            indexItemRepo();
        }

        if (ITEM_CACHE.containsKey(sbId)) {
            return ITEM_CACHE.get(sbId).copy();
        }

        ItemStack stack = loadFromItemRepo(sbId);
        if (stack != null && !stack.isEmpty()) {
            ITEM_CACHE.put(sbId, stack.copy());
            return stack;
        }

        ItemStack fallback = createFallbackStack(sbId);
        return fallback;
    }

    private static ItemStack loadFromItemRepo(String sbId) {
        Path itemFile = FILE_INDEX.get(sbId);

        if (itemFile == null || !Files.exists(itemFile)) {
            String query = sbId.replace("_", "");
            for (Map.Entry<String, Path> entry : FILE_INDEX.entrySet()) {
                if (entry.getKey().replace("_", "").equalsIgnoreCase(query)) {
                    itemFile = entry.getValue();
                    sbId = entry.getKey();
                    break;
                }
            }
        }

        if (itemFile == null || !Files.exists(itemFile)) {
            return null;
        }

        try {
            String jsonStr = Files.readString(itemFile);
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

            String mcItemId = json.has("itemid") ? json.get("itemid").getAsString() : "minecraft:paper";
            int damage = json.has("damage") ? json.get("damage").getAsInt() : 0;
            Item baseItem = parseBaseItem(mcItemId, damage);
            ItemStack stack = new ItemStack(baseItem);

            String nbtStr = json.has("nbttag") ? json.get("nbttag").getAsString() : "";
            CompoundTag customData = new CompoundTag();

            customData.putString("id", sbId);
            customData.putString("ID", sbId);

            CompoundTag extra = new CompoundTag();
            extra.putString("id", sbId);

            if (!nbtStr.isEmpty()) {
                Matcher extraMatcher = EXTRA_ATTRIBUTES_PATTERN.matcher(nbtStr);
                if (extraMatcher.find()) {
                    String content = extraMatcher.group(1);
                    Matcher kv = KV_PATTERN.matcher(content);
                    while (kv.find()) {
                        String k = kv.group(1);
                        if (kv.group(2) != null) {
                            extra.putString(k, kv.group(2));
                            customData.putString(k, kv.group(2));
                        } else if (kv.group(3) != null) {
                            int val = Integer.parseInt(kv.group(3));
                            extra.putInt(k, val);
                            customData.putInt(k, val);
                        } else if (kv.group(4) != null) {
                            extra.putString(k, kv.group(4));
                            customData.putString(k, kv.group(4));
                        }
                    }
                }
            }
            customData.put("ExtraAttributes", extra);

            if (baseItem == Items.PLAYER_HEAD && !nbtStr.isEmpty()) {
                Matcher texMatcher = TEXTURE_PATTERN.matcher(nbtStr);
                if (texMatcher.find()) {
                    String base64 = texMatcher.group(1).replace("\\n", "").replace("\\r", "").replaceAll("\\s+", "").trim();

                    UUID skullUuid = UUID.randomUUID();
                    Matcher idMatcher = Pattern.compile("SkullOwner\\s*:\\s*\\{[^}]*Id\\s*:\\s*\"([^\"]+)\"").matcher(nbtStr);
                    if (idMatcher.find()) {
                        try {
                            skullUuid = UUID.fromString(idMatcher.group(1));
                        } catch (Exception ignored) {
                        }
                    }

                    applySkullProfile(stack, skullUuid, base64);

                    CompoundTag skullOwner = new CompoundTag();
                    skullOwner.putString("Id", skullUuid.toString());
                    CompoundTag properties = new CompoundTag();
                    ListTag textures = new ListTag();
                    CompoundTag texVal = new CompoundTag();
                    texVal.putString("Value", base64);
                    textures.add(texVal);
                    properties.put("textures", textures);
                    skullOwner.put("Properties", properties);
                    customData.put("SkullOwner", skullOwner);
                }
            }

            if (!nbtStr.isEmpty()) {
                Matcher colorMatcher = COLOR_PATTERN.matcher(nbtStr);
                if (colorMatcher.find()) {
                    int rgb = Integer.parseInt(colorMatcher.group(1));
                    stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
                }
            }

            if (!nbtStr.isEmpty()) {
                Matcher modelMatcher = Pattern.compile("ItemModel\\s*:\\s*\"([^\"]+)\"").matcher(nbtStr);
                if (modelMatcher.find()) {
                    Identifier modelId = Identifier.tryParse(modelMatcher.group(1));
                    if (modelId != null) {
                        stack.set(DataComponents.ITEM_MODEL, modelId);
                    }
                }
            }

            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

            if (json.has("displayname")) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(json.get("displayname").getAsString()));
            }

            LOGGER.info("[Kwrg] 成功从 item-repo 加载物品: {} (Base: {})", sbId, baseItem);
            return stack;
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 解析 item-repo 物品失败: " + sbId, e);
            IrcMessageHandler.printMessage("§c[Kwrg] 解析 item-repo 物品失败");
        }
        return null;
    }

    public static synchronized boolean indexItemRepo() {
        if (isIndexed && !FILE_INDEX.isEmpty()) return true;

        Path repoDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config/skyblocker/item-repo");
        if (!Files.exists(repoDir)) {
            LOGGER.warn("[Kwrg] 未找到目录: {}", repoDir);
            IrcMessageHandler.printMessage("§c[Kwrg] 未找到目录 item-repo，请确认是否安装skybloocker");
            return false;
        }

        FILE_INDEX.clear();
        KNOWN_IDS.clear();

        try (Stream<Path> stream = Files.walk(repoDir, 5)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String fn = p.getFileName().toString();
                String sbId = fn.substring(0, fn.length() - 5).toUpperCase(Locale.ROOT);
                FILE_INDEX.put(sbId, p);
                KNOWN_IDS.add("skyblock:" + sbId);
            });

            isIndexed = true;
            LOGGER.info("[Kwrg] 成功索引 config/skyblocker/item-repo 下的 {} 个物品", FILE_INDEX.size());
            return true;
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 索引 item-repo 失败", e);
            IrcMessageHandler.printMessage("§c[Kwrg] 读取 item-repo 失败: " + e.getMessage());
            return false;
        }
    }

    private static Item parseBaseItem(String mcItemId, int damage) {
        String full = mcItemId.contains(":") ? mcItemId : "minecraft:" + mcItemId;
        if (full.equals("minecraft:skull") || full.equals("minecraft:player_head")) {
            return Items.PLAYER_HEAD;
        }

        Identifier id = Identifier.tryParse(full);
        if (id != null) {
            return BuiltInRegistries.ITEM.get(id)
                    .map(Holder.Reference::value)
                    .filter(i -> i != Items.AIR)
                    .orElse(Items.PAPER);
        }
        return Items.PAPER;
    }

    private static ItemStack createFallbackStack(String sbId) {
        Item baseItem = inferBaseItem(sbId);
        ItemStack stack = new ItemStack(baseItem);

        CompoundTag extra = new CompoundTag();
        extra.putString("id", sbId);
        CompoundTag custom = new CompoundTag();
        custom.put("ExtraAttributes", extra);

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6" + sbId.replace("_", " ")));
        return stack;
    }

    private static Item inferBaseItem(String sbId) {
        String upper = sbId.toUpperCase(Locale.ROOT);
        if (upper.endsWith("_SWORD") || upper.endsWith("_BLADE") || upper.endsWith("_DAGGER") || upper.endsWith("_KATANA") || upper.contains("SWORD") || upper.contains("DAGGER"))
            return Items.IRON_SWORD;
        if (upper.endsWith("_BOW") || upper.endsWith("_SHORTBOW") || upper.contains("BOW") || upper.contains("TERMINATOR"))
            return Items.BOW;
        if (upper.endsWith("_AXE") || upper.contains("AXE")) return Items.IRON_AXE;
        if (upper.endsWith("_PICKAXE") || upper.endsWith("_DRILL") || upper.contains("DRILL") || upper.contains("PICKAXE"))
            return Items.DIAMOND_PICKAXE;
        if (upper.endsWith("_SHOVEL") || upper.endsWith("_SPADE")) return Items.DIAMOND_SHOVEL;
        if (upper.endsWith("_HOE") || upper.endsWith("_SCYTHE")) return Items.DIAMOND_HOE;
        if (upper.endsWith("_HELMET") || upper.endsWith("_MASK") || upper.endsWith("_HEAD") || upper.contains("HELMET") || upper.contains("MASK") || upper.contains("PHONE") || upper.contains("ORB") || upper.contains("ABICASE"))
            return Items.PLAYER_HEAD;
        if (upper.endsWith("_CHESTPLATE") || upper.endsWith("_TUNIC") || upper.contains("CHESTPLATE"))
            return Items.LEATHER_CHESTPLATE;
        if (upper.endsWith("_LEGGINGS") || upper.endsWith("_PANTS") || upper.contains("LEGGINGS"))
            return Items.LEATHER_LEGGINGS;
        if (upper.endsWith("_BOOTS") || upper.endsWith("_SHOES") || upper.contains("BOOTS")) return Items.LEATHER_BOOTS;
        if (upper.endsWith("_ROD") || upper.endsWith("_WHIP")) return Items.FISHING_ROD;
        if (upper.endsWith("_WAND") || upper.endsWith("_STAFF")) return Items.STICK;
        return Items.PAPER;
    }

    private static void applySkullProfile(ItemStack stack, UUID uuid, String base64) {
        try {

            String Base64 = base64.replace("\\n", "").replace("\\r", "").replaceAll("\\s+", "").trim();
            while (Base64.length() % 4 != 0) {
                Base64 += "=";
            }

            com.google.common.collect.Multimap<String, Property> multimap = Multimaps.forMap(
                    Map.of("textures", new Property("textures", Base64))
            );
            PropertyMap propertyMap = new PropertyMap(multimap);
            GameProfile gameProfile = new GameProfile(uuid, "LameGuy123", propertyMap);

            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gameProfile));
        } catch (Throwable t) {
            LOGGER.error("[Kwrg] 注入头颅皮肤失败", t);
        }
    }

    public static void invalidateCache() {
        ITEM_CACHE.clear();
        FILE_INDEX.clear();
        isIndexed = false;
        IrcMessageHandler.printMessage("§e[Kwrg] 已清空 item-repo cache");
        ChatNotifier.send("§e[Kwrg] 已清空 item-repo cache");
    }

    public static synchronized void reloadRepo() {
        invalidateCache();
        boolean success = indexItemRepo();

        if (success && !FILE_INDEX.isEmpty()) {
            IrcMessageHandler.printMessage("§a[Kwrg] 成功重新加载 item-repo，共索引 §e" + FILE_INDEX.size() + "§a 个物品");
            ChatNotifier.send("§a[Kwrg] 成功重新加载 item-repo，共索引 §e" + FILE_INDEX.size() + "§a 个物品");
            LOGGER.info("[Kwrg] 已重新加载 item-repo，共索引 {} 个物品", FILE_INDEX.size());
        }
    }

    public static List<String> getSuggestedIds() {
        if (!isIndexed) {
            indexItemRepo();
        }
        return new ArrayList<>(KNOWN_IDS);
    }
}