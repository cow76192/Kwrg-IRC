package me.cookie.kwrg.util;


import java.util.List;
import java.util.Locale;

/** Island mode IDs follow SkyHanni's IslandType and the Hypixel location API. */
public record SkyblockIsland(String id, String name) {
    public static final List<SkyblockIsland> ALL = List.of(
            new SkyblockIsland("all", "所有区域 / All"),
            new SkyblockIsland("dynamic", "私人岛屿 / Private Island"),
            new SkyblockIsland("dynamic_guest", "拜访私人岛屿 / Private Island Guest"),
            new SkyblockIsland("hub", "主岛 / Hub"),
            new SkyblockIsland("dark_auction", "暗拍 / Dark Auction"),
            new SkyblockIsland("winter", "杰瑞工坊 / Jerry's Workshop"),
            new SkyblockIsland("farming_1", "农场群岛 / The Farming Islands"),
            new SkyblockIsland("garden", "花园 / Garden"),
            new SkyblockIsland("garden_guest", "拜访花园 / Garden Guest"),
            new SkyblockIsland("mining_1", "金矿 / Gold Mine"),
            new SkyblockIsland("mining_2", "深层矿洞 / Deep Caverns"),
            new SkyblockIsland("mining_3", "矮人矿场 / Dwarven Mines"),
            new SkyblockIsland("crystal_hollows", "水晶洞窟 / Crystal Hollows"),
            new SkyblockIsland("mineshaft", "矿井 / Mineshaft"),
            new SkyblockIsland("fishing_1", "回水沼泽 / Backwater Bayou"),
            new SkyblockIsland("lotus_atoll", "莲花环礁 / Lotus Atoll"),
            new SkyblockIsland("foraging_1", "公园 / The Park"),
            new SkyblockIsland("foraging_2", "沼光湿地 / Moonglade Marsh"),
            new SkyblockIsland("foraging_3", "托鲁斯峡谷 / Torrhus Canyon"),
            new SkyblockIsland("combat_1", "蜘蛛巢穴 / Spider's Den"),
            new SkyblockIsland("combat_3", "末地 / The End"),
            new SkyblockIsland("crimson_isle", "绯红之岛 / Crimson Isle"),
            new SkyblockIsland("dungeon_hub", "地牢大厅 / Dungeon Hub"),
            new SkyblockIsland("dungeon", "地下墓穴 / Catacombs"),
            new SkyblockIsland("kuudra", "库德拉 / Kuudra"),
            new SkyblockIsland("rift", "裂隙 / The Rift"),
            new SkyblockIsland("safari", "生物狩猎 / Critter Safari"));

    public static List<SkyblockIsland> search(String query) {
        String key = query.strip().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(island -> island.id.equals("all") || island.id.contains(key) || island.name.toLowerCase(Locale.ROOT).contains(key)).toList();
    }
}
