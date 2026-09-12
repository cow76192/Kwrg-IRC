package me.cookie.kwrg.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkyblockItemHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-skyblock");

    private static final Map<String, ItemStack> ITEM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> RAW_ITEM_INDEX = new ConcurrentHashMap<>();
    private static final Set<String> KNOWN_IDS = ConcurrentHashMap.newKeySet();

    private static final List<String> PET_TIER_PRIORITY = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

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

        return createFallbackStack(sbId);
    }

    private static ItemStack loadFromItemRepo(String sbId) {
        JsonObject obj = RAW_ITEM_INDEX.get(sbId);

        if (obj == null) {
            String query = sbId.replace("_", "").replace(";", "");
            for (Map.Entry<String, JsonObject> entry : RAW_ITEM_INDEX.entrySet()) {
                if (entry.getKey().replace("_", "").replace(";", "").equalsIgnoreCase(query)) {
                    obj = entry.getValue();
                    sbId = entry.getKey();
                    break;
                }
            }
        }

        if (obj == null) {
            return null;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            DynamicOps<JsonElement> ops = mc.level != null
                    ? mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE)
                    : (mc.getConnection() != null
                       ? mc.getConnection().registryAccess().createSerializationContext(JsonOps.INSTANCE)
                       : JsonOps.INSTANCE);

            Optional<ItemStack> parsed = ItemStack.CODEC.parse(ops, obj)
                    .resultOrPartial(err -> LOGGER.warn("[Kwrg] Codec 解析警告: {}", err));

            if (parsed.isPresent() && !parsed.get().isEmpty()) {
                ItemStack stack = parsed.get();
                LOGGER.info("[Kwrg] 成功加载物品: {}", sbId);
                return stack;
            }
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 解析物品失败: " + sbId, e);
            ChatNotifier.sendInChatScreen("§c[Kwrg] 解析物品失败: " + sbId);
        }
        return null;
    }

    public static synchronized boolean indexItemRepo() {
        if (isIndexed && !RAW_ITEM_INDEX.isEmpty()) return true;

        Path repoDir = Minecraft.getInstance().gameDirectory.toPath().resolve("skyblock-repo-cache");
        if (!Files.exists(repoDir)) {
            LOGGER.warn("[Kwrg] 未找到目录: {}", repoDir);
            ChatNotifier.sendInChatScreen("§c[Kwrg] 未找到 skyblock-repo-cache 文件夹");
            return false;
        }

        RAW_ITEM_INDEX.clear();
        KNOWN_IDS.clear();
        ITEM_CACHE.clear();

        int itemsCount = indexItemsJson(repoDir.resolve("items.min.json"));
        int attributesCount = indexAttributesJson(repoDir.resolve("attributes.min.json"));
        int petsCount = indexPetsJson(repoDir.resolve("pets.min.json"));
        int enchantmentsCount = indexEnchantmentsJson(repoDir.resolve("enchantments.min.json"));
        int runesCount = indexRunesJson(repoDir.resolve("runes.min.json"));

        int totalCount = itemsCount + attributesCount + petsCount + enchantmentsCount + runesCount;
        if (totalCount > 0) {
            isIndexed = true;
            LOGGER.info("[Kwrg] 成功索引 items:{}, attributes:{}, pets:{}, enchants:{}, runes:{}, 共 {} 个标识符",
                    itemsCount, attributesCount, petsCount, enchantmentsCount, runesCount, RAW_ITEM_INDEX.size());
            return true;
        } else {
            ChatNotifier.sendInChatScreen("§c[Kwrg] 未能从 skyblock-repo-cache 加载任何数据");
            return false;
        }
    }

    private static int indexItemsJson(Path path) {
        if (!Files.exists(path)) return 0;
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String sbId = extractCustomDataId(obj);
                    if (sbId != null && !sbId.isBlank()) {
                        String upper = sbId.toUpperCase(Locale.ROOT);
                        RAW_ITEM_INDEX.put(upper, obj);
                        KNOWN_IDS.add("skyblock:" + upper);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            ChatNotifier.sendInChatScreen("§c[Kwrg] 索引 items.min.json 失败");
            LOGGER.error("[Kwrg] 索引 items.min.json 失败", e);
        }
        return count;
    }

    private static int indexAttributesJson(Path path) {
        if (!Files.exists(path)) return 0;
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("itemStack") || !obj.get("itemStack").isJsonObject()) continue;

                    JsonObject itemStackObj = obj.getAsJsonObject("itemStack");

                    if (obj.has("shard_id")) {
                        String shardId = obj.get("shard_id").getAsString().toUpperCase(Locale.ROOT);
                        RAW_ITEM_INDEX.put(shardId, itemStackObj);
                        KNOWN_IDS.add("skyblock:" + shardId);
                        count++;
                    }

                    if (obj.has("id")) {
                        String attrId = obj.get("id").getAsString().toUpperCase(Locale.ROOT);
                        RAW_ITEM_INDEX.putIfAbsent(attrId, itemStackObj);
                        RAW_ITEM_INDEX.put("ATTRIBUTE_SHARD_" + attrId, itemStackObj);
                        KNOWN_IDS.add("skyblock:" + attrId);
                        KNOWN_IDS.add("skyblock:ATTRIBUTE_SHARD_" + attrId);
                        count++;
                    }

                    if (obj.has("attribute_id")) {
                        String shortId = obj.get("attribute_id").getAsString().toUpperCase(Locale.ROOT);
                        RAW_ITEM_INDEX.putIfAbsent("SHARD_" + shortId, itemStackObj);
                        KNOWN_IDS.add("skyblock:SHARD_" + shortId);
                    }
                }
            }
        } catch (Exception e) {
            ChatNotifier.sendInChatScreen("§c[Kwrg] 索引 attributes.min.json 失败");
            LOGGER.error("[Kwrg] 索引 attributes.min.json 失败", e);
        }
        return count;
    }

    private static int indexPetsJson(Path path) {
        if (!Files.exists(path)) return 0;
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                JsonObject petsObj = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : petsObj.entrySet()) {
                    String petId = entry.getKey().toUpperCase(Locale.ROOT);
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject petData = entry.getValue().getAsJsonObject();

                    if (!petData.has("tiers") || !petData.get("tiers").isJsonObject()) continue;
                    JsonObject tiers = petData.getAsJsonObject("tiers");

                    JsonObject highestTierItem = null;
                    int maxTierRank = -1;

                    for (Map.Entry<String, JsonElement> tierEntry : tiers.entrySet()) {
                        String tierName = tierEntry.getKey().toUpperCase(Locale.ROOT);
                        if (!tierEntry.getValue().isJsonObject()) continue;
                        JsonObject tierObj = tierEntry.getValue().getAsJsonObject();

                        if (!tierObj.has("item") || !tierObj.get("item").isJsonObject()) continue;
                        JsonObject itemObj = tierObj.getAsJsonObject("item");

                        String key1 = petId + "_" + tierName;
                        String key2 = petId + ";" + tierName;
                        RAW_ITEM_INDEX.put(key1, itemObj);
                        RAW_ITEM_INDEX.put(key2, itemObj);
                        KNOWN_IDS.add("skyblock:" + key1);
                        KNOWN_IDS.add("skyblock:" + key2);
                        count++;

                        int rank = PET_TIER_PRIORITY.indexOf(tierName);
                        if (rank > maxTierRank) {
                            maxTierRank = rank;
                            highestTierItem = itemObj;
                        }
                    }

                    if (highestTierItem != null) {
                        RAW_ITEM_INDEX.put(petId, highestTierItem);
                        KNOWN_IDS.add("skyblock:" + petId);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            ChatNotifier.sendInChatScreen("§c[Kwrg] 索引 pets.min.json 失败");
            LOGGER.error("[Kwrg] 索引 pets.min.json 失败", e);
        }
        return count;
    }

    private static int indexEnchantmentsJson(Path path) {
        if (!Files.exists(path)) return 0;
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                JsonObject enchantsObj = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : enchantsObj.entrySet()) {
                    String enchantId = entry.getKey().toUpperCase(Locale.ROOT);
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject enchantData = entry.getValue().getAsJsonObject();

                    if (!enchantData.has("levels") || !enchantData.get("levels").isJsonArray()) continue;
                    JsonArray levels = enchantData.getAsJsonArray("levels");

                    JsonObject highestLevelItem = null;
                    int maxLevel = -1;

                    for (JsonElement lvlEl : levels) {
                        if (!lvlEl.isJsonObject()) continue;
                        JsonObject lvlObj = lvlEl.getAsJsonObject();

                        if (!lvlObj.has("item") || !lvlObj.get("item").isJsonObject()) continue;
                        JsonObject itemObj = lvlObj.getAsJsonObject("item");

                        int level = lvlObj.has("level") ? lvlObj.get("level").getAsInt() : 1;

                        String k1 = enchantId + "_" + level;
                        String k2 = enchantId + ";" + level;
                        String k3 = "ENCHANTMENT_" + enchantId + "_" + level;
                        String k4 = "ENCHANTMENT_" + enchantId + ";" + level;
                        String k5 = "ENCHANTED_BOOK_" + enchantId + "_" + level;

                        RAW_ITEM_INDEX.put(k1, itemObj);
                        RAW_ITEM_INDEX.put(k2, itemObj);
                        RAW_ITEM_INDEX.put(k3, itemObj);
                        RAW_ITEM_INDEX.put(k4, itemObj);
                        RAW_ITEM_INDEX.put(k5, itemObj);

                        KNOWN_IDS.add("skyblock:" + k1);
                        KNOWN_IDS.add("skyblock:" + k2);
                        KNOWN_IDS.add("skyblock:" + k3);
                        KNOWN_IDS.add("skyblock:" + k4);
                        KNOWN_IDS.add("skyblock:" + k5);
                        count++;

                        if (level > maxLevel) {
                            maxLevel = level;
                            highestLevelItem = itemObj;
                        }
                    }

                    if (highestLevelItem != null) {
                        RAW_ITEM_INDEX.putIfAbsent(enchantId, highestLevelItem);
                        RAW_ITEM_INDEX.putIfAbsent("ENCHANTMENT_" + enchantId, highestLevelItem);
                        KNOWN_IDS.add("skyblock:" + enchantId);
                        KNOWN_IDS.add("skyblock:ENCHANTMENT_" + enchantId);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            ChatNotifier.sendInChatScreen("§c[Kwrg] 索引 enchantments.min.json 失败");
            LOGGER.error("[Kwrg] 索引 enchantments.min.json 失败", e);
        }
        return count;
    }

    private static int indexRunesJson(Path path) {
        if (!Files.exists(path)) return 0;
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                JsonObject runesObj = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : runesObj.entrySet()) {
                    String runeId = entry.getKey().toUpperCase(Locale.ROOT);
                    if (!entry.getValue().isJsonArray()) continue;
                    JsonArray tiers = entry.getValue().getAsJsonArray();

                    JsonObject highestTierItem = null;
                    int maxTier = -1;

                    for (JsonElement tierEl : tiers) {
                        if (!tierEl.isJsonObject()) continue;
                        JsonObject tierObj = tierEl.getAsJsonObject();

                        if (!tierObj.has("item") || !tierObj.get("item").isJsonObject()) continue;
                        JsonObject itemObj = tierObj.getAsJsonObject("item");

                        int tier = tierObj.has("tier") ? tierObj.get("tier").getAsInt() : 1;

                        String k1 = runeId + "_" + tier;
                        String k2 = runeId + ";" + tier;
                        String k3 = "RUNE_" + runeId + "_" + tier;
                        String k4 = "RUNE_" + runeId + ";" + tier;

                        RAW_ITEM_INDEX.put(k1, itemObj);
                        RAW_ITEM_INDEX.put(k2, itemObj);
                        RAW_ITEM_INDEX.put(k3, itemObj);
                        RAW_ITEM_INDEX.put(k4, itemObj);

                        KNOWN_IDS.add("skyblock:" + k1);
                        KNOWN_IDS.add("skyblock:" + k2);
                        KNOWN_IDS.add("skyblock:" + k3);
                        KNOWN_IDS.add("skyblock:" + k4);
                        count++;

                        if (tier > maxTier) {
                            maxTier = tier;
                            highestTierItem = itemObj;
                        }
                    }

                    if (highestTierItem != null) {
                        RAW_ITEM_INDEX.putIfAbsent(runeId, highestTierItem);
                        RAW_ITEM_INDEX.putIfAbsent("RUNE_" + runeId, highestTierItem);
                        KNOWN_IDS.add("skyblock:" + runeId);
                        KNOWN_IDS.add("skyblock:RUNE_" + runeId);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            ChatNotifier.sendInChatScreen("§c[Kwrg] 索引 runes.min.json 失败");
            LOGGER.error("[Kwrg] 索引 runes.min.json 失败", e);
        }
        return count;
    }

    private static String extractCustomDataId(JsonObject obj) {
        if (obj.has("components") && obj.get("components").isJsonObject()) {
            JsonObject components = obj.getAsJsonObject("components");
            if (components.has("minecraft:custom_data") && components.get("minecraft:custom_data").isJsonObject()) {
                JsonObject customData = components.getAsJsonObject("minecraft:custom_data");
                if (customData.has("id")) {
                    return customData.get("id").getAsString();
                }
            }
        }
        return null;
    }

    private static ItemStack createFallbackStack(String sbId) {
        Item baseItem = inferBaseItem(sbId);
        ItemStack stack = new ItemStack(baseItem);

        CompoundTag extra = new CompoundTag();
        extra.putString("id", sbId);
        CompoundTag custom = new CompoundTag();
        custom.put("ExtraAttributes", extra);
        custom.putString("id", sbId);

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
        if (upper.endsWith("_HELMET") || upper.endsWith("_MASK") || upper.endsWith("_HEAD") || upper.contains("HELMET") || upper.contains("MASK") || upper.contains("PHONE") || upper.contains("ORB") || upper.contains("ABICASE") || upper.contains("SHARD") || upper.contains("RUNE"))
            return Items.PLAYER_HEAD;
        if (upper.contains("BOOK") || upper.contains("ENCHANT"))
            return Items.ENCHANTED_BOOK;
        if (upper.endsWith("_CHESTPLATE") || upper.endsWith("_TUNIC") || upper.contains("CHESTPLATE"))
            return Items.LEATHER_CHESTPLATE;
        if (upper.endsWith("_LEGGINGS") || upper.endsWith("_PANTS") || upper.contains("LEGGINGS"))
            return Items.LEATHER_LEGGINGS;
        if (upper.endsWith("_BOOTS") || upper.endsWith("_SHOES") || upper.contains("BOOTS")) return Items.LEATHER_BOOTS;
        if (upper.endsWith("_ROD") || upper.endsWith("_WHIP")) return Items.FISHING_ROD;
        if (upper.endsWith("_WAND") || upper.endsWith("_STAFF")) return Items.STICK;
        return Items.PAPER;
    }

    public static void invalidateCache() {
        ITEM_CACHE.clear();
        RAW_ITEM_INDEX.clear();
        KNOWN_IDS.clear();
        isIndexed = false;
        ChatNotifier.send("§e[Kwrg] 已清空 skyblock-repo-cache");
        ChatNotifier.sendInChatScreen("§e[Kwrg] 已清空 item-repo cache");
    }

    public static synchronized void reloadRepo() {
        invalidateCache();
        boolean success = indexItemRepo();

        if (success && !RAW_ITEM_INDEX.isEmpty()) {
            ChatNotifier.send("§a[Kwrg] 成功重新加载 repo，共索引 §e" + RAW_ITEM_INDEX.size() + "§a 个物品");
            ChatNotifier.sendInChatScreen("§a[Kwrg] 成功重新加载 repo，共索引 §e" + RAW_ITEM_INDEX.size() + "§a 个物品");
            LOGGER.info("[Kwrg] 已重新加载 repo，共索引 {} 个条目", RAW_ITEM_INDEX.size());
        }
    }

    public static List<String> getSuggestedIds() {
        if (!isIndexed) {
            indexItemRepo();
        }
        return new ArrayList<>(KNOWN_IDS);
    }
}