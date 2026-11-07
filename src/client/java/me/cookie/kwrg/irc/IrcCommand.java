package me.cookie.kwrg.irc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.hud.HudEditorScreen;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.ChatWaypointManager;
import me.cookie.kwrg.irc.manager.RouteEditor;
import me.cookie.kwrg.irc.manager.RouteManager;
import me.cookie.kwrg.irc.manager.WaypointManager;
import me.cookie.kwrg.module.modules.debug.ClearIndexCacheModule;
import me.cookie.kwrg.module.modules.functions.ChannelModule;
import me.cookie.kwrg.module.modules.functions.GlowColorModule;
import me.cookie.kwrg.module.modules.functions.HideArmorModule;
import me.cookie.kwrg.module.modules.functions.ImportantPlayerModule;
import me.cookie.kwrg.module.modules.play.PlayModule;
import me.cookie.kwrg.module.modules.style.MessageStyleModule;
import me.cookie.kwrg.module.modules.style.NameStyleModule;
import me.cookie.kwrg.module.modules.style.RankModule;
import me.cookie.kwrg.render.WorldTextRenderer;
import me.cookie.kwrg.util.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;

import java.util.ArrayList;
import java.util.List;

import static me.cookie.kwrg.irc.manager.ChatWaypointManager.MAX_COORD;
import static me.cookie.kwrg.module.modules.config.LoadModule.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class IrcCommand {

    private static final List<CommandNode<FabricClientCommandSource>> HELP_ROOTS = new ArrayList<>();
    private static final int HELP_MAX_DEPTH = 3;

    private static void registerRoot(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                     LiteralArgumentBuilder<FabricClientCommandSource> root) {
        HELP_ROOTS.add(dispatcher.register(root));
    }

    private static final SuggestionProvider<FabricClientCommandSource> STYLE_SUGGESTIONS = (context, builder) -> {
        String[] presets = new String[]{
                "rainbow", "sunset", "ocean", "fire", "sakura", "neon", "gold", "purple", "bold", "underline", "italic", "strikethrough", "obfuscated",
                "rainbow bold", "#FF55AA", "rgb(255,100,50)", "gradient(#ff758c,#ff7eb3)", "gradient(#00f2fe,#4facfe,#00ff87)"
        };
        for (String p : presets) {
            builder.suggest(p);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> ITEM_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();

        for (String sbItem : me.cookie.kwrg.util.SkyblockItemHelper.getSuggestedIds()) {
            if (sbItem.toLowerCase().startsWith(remaining)) {
                builder.suggest(sbItem);
            }
        }

        for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            String full = id.toString();
            String path = id.getPath();
            if (full.startsWith(remaining)) {
                builder.suggest(full);
            } else if (path.startsWith(remaining)) {
                builder.suggest(path);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> CONFIG_SUGGESTIONS = (context, builder) -> {
        for (String configName : IrcConfig.listExistingProfiles()) {
            builder.suggest(configName);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> BAN_TYPE_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (String type : getBanUtil.getBanTypes()) {
            if (type.startsWith(remaining)) {
                builder.suggest(type);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> BAN_DURATION_SUGGESTIONS = (context, builder) -> {
        String[] durations = {"29d 23h 59m 59s", "89d 23h 59m 59s", "359d 23h 59m 59s", "13d 23h 59m 59s", "6d 23h 59m 59s"};
        for (String d : durations) {
            builder.suggest(d);
        }
        return builder.buildFuture();
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(IrcCommand::registerCommands);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> hideArmorNode() {
        var node = literal("hideArmor");
        HideArmorModule.PARTS.forEach((name, slot) -> node.then(literal(name)
                .then(argument("enabled", BoolArgumentType.bool())
                        .executes(context -> HideArmorModule.setHideArmorPart(slot, BoolArgumentType.getBool(context, "enabled"))))));
        return node
                .then(argument("enabled", BoolArgumentType.bool())
                        .executes(context -> HideArmorModule.setHideArmor(BoolArgumentType.getBool(context, "enabled"))))
                .executes(context -> HideArmorModule.showHideArmor());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> rankAlias(String alias) {
        return literal(alias)
                .then(argument("name", StringArgumentType.greedyString())
                        .executes(context -> RankModule.setRank(StringArgumentType.getString(context, "name"))))
                .executes(context -> RankModule.showRank());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> nameStyleAlias(String alias) {
        return literal(alias)
                .then(argument("style", StringArgumentType.greedyString())
                        .suggests(STYLE_SUGGESTIONS)
                        .executes(context -> NameStyleModule.setNameStyle(StringArgumentType.getString(context, "style"))))
                .executes(context -> NameStyleModule.showNameStyle());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> messageStyleAlias(String alias) {
        return literal(alias)
                .then(argument("style", StringArgumentType.greedyString())
                        .suggests(STYLE_SUGGESTIONS)
                        .executes(context -> MessageStyleModule.setChatStyle(StringArgumentType.getString(context, "style"))))
                .executes(context -> MessageStyleModule.showChatStyle());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> playAlias(String alias) {
        return literal(alias)
                .then(argument("player", StringArgumentType.string())
                        .then(argument("item", StringArgumentType.greedyString())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(context -> PlayModule.handlePlayCommand(
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "item")
                                ))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> importantAlias(String alias) {
        return literal(alias)
                .then(argument("player", StringArgumentType.string())
                        .executes(context -> ImportantPlayerModule.toggleImportant(StringArgumentType.getString(context, "player"))))
                .executes(context -> ImportantPlayerModule.listImportant());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> configAlias(String alias) {
        return literal(alias)
                .then(literal("load")
                        .then(argument("name", StringArgumentType.string())
                                .suggests(CONFIG_SUGGESTIONS)
                                .executes(context -> loadConfig(StringArgumentType.getString(context, "name")))))
                .then(literal("create")
                        .then(argument("name", StringArgumentType.string())
                                .executes(context -> createConfig(StringArgumentType.getString(context, "name")))))
                .then(literal("save")
                        .executes(context -> saveConfig()))
                .executes(context -> showCurrentConfig());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> functionsAlias(String alias) {
        return literal(alias)
                .then(hideArmorNode())
                .then(literal("glowColor")
                        .then(argument("color", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> GlowColorModule.setGlowColor(StringArgumentType.getString(context, "color"))))
                        .executes(context -> GlowColorModule.showGlowColor()));
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
        registerRoot(dispatcher, literal("ci")
                .then(literal("all")
                        .executes(context -> {
                            IrcClient.isIrcChatActive = true;
                            ChannelModule.Settings config = ChannelModule.CONFIG.get();
                            config.currentChannel = "";
                            IrcConfig.save();
                            IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled, config.savedChannels);

                            IrcMessageHandler.printMessage("§a[IRC] 已切换至 §e公共大厅发言 §a模式");
                            if (!IrcClient.isIrcConnect) {
                                IrcClient.connect();
                            }
                            return 1;
                        }))
                .then(literal("channel")
                        .then(argument("name", StringArgumentType.string())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    IrcClient.isIrcChatActive = true;
                                    ChannelModule.Settings config = ChannelModule.CONFIG.get();

                                    if (!config.savedChannels.contains(name)) {
                                        config.savedChannels.add(name);
                                    }
                                    config.currentChannel = name;
                                    config.channelEnabled = true;
                                    IrcConfig.save();
                                    IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled, config.savedChannels);

                                    IrcMessageHandler.printMessage("§a[IRC] 已切换至 §b[#" + name + "] §a频道发言模式！");
                                    if (!IrcClient.isIrcConnect) {
                                        IrcClient.connect();
                                    }
                                    return 1;
                                }))
                        .executes(context -> {
                            IrcClient.isIrcChatActive = true;
                            ChannelModule.Settings config = ChannelModule.CONFIG.get();

                            if (config.currentChannel == null || config.currentChannel.isBlank()) {
                                if (config.savedChannels != null && !config.savedChannels.isEmpty()) {
                                    config.currentChannel = config.savedChannels.get(0);
                                } else {
                                    IrcMessageHandler.printMessage("§c[IRC] 你尚未保存任何频道！用法: /ci channel <频道名>");
                                    return 0;
                                }
                            }

                            config.channelEnabled = true;
                            IrcConfig.save();
                            IrcClient.sendChannelSync(config.currentChannel, true, config.savedChannels);

                            IrcMessageHandler.printMessage("§a[IRC] 已切换至 §b[#" + config.currentChannel + "] §a频道发言模式！");
                            if (!IrcClient.isIrcConnect) {
                                IrcClient.connect();
                            }
                            return 1;
                        }))
                .executes(context -> {
                    IrcClient.isIrcChatActive = true;
                    ChannelModule.Settings config = ChannelModule.CONFIG.get();

                    if (config.savedChannels != null && !config.savedChannels.isEmpty()  && !config.connectPublicChannelFirst) {
                        config.currentChannel = config.savedChannels.get(0);
                        config.channelEnabled = true;
                        IrcMessageHandler.printMessage("§a[IRC] 快捷切换发言模式: §b[#" + config.currentChannel + "]");

                    } else if (config.channelEnabled && config.currentChannel != null && !config.currentChannel.isBlank()) {
                        config.currentChannel = "";
                        IrcMessageHandler.printMessage("§a[IRC] 快捷切换发言模式: §e公共大厅");
                    }

                     else {
                        IrcMessageHandler.printMessage("§a[IRC] 当前处于: §e公共大厅发言");
                    }

                    IrcConfig.save();
                    IrcClient.sendChannelSync(config.currentChannel, config.channelEnabled, config.savedChannels);

                    if (!IrcClient.isIrcConnect) {
                        IrcClient.connect();
                    }
                    return 1;
                })
        );

        registerRoot(dispatcher, literal("ic")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            IrcClient.sendMessage(message);
                            return 1;
                        })
                )
                .executes(context -> {
                    IrcMessageHandler.printMessage("§c[IRC] 用法: /ic <消息内容>");
                    return 0;
                })
        );

        registerRoot(dispatcher, literal("icall")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String processed = StringArgumentType.getString(context, "message");
                            processed = EmojiUtil.replaceShortcodes(processed);
                            IrcClient.sendMessageToPublic(processed);
                            return 1;
                        })
                )
                .executes(context -> {
                    IrcMessageHandler.printMessage("§c[IRC] 用法: /icall <消息内容> (强制发送至公共大厅)");
                    return 0;
                })
        );

        var ircRoot = literal("irc")
                .executes(context -> {
                    sendHelp();
                    return 1;
                })
                .then(literal("share")
                        .then(literal("open")
                                .executes(context -> {
                                    me.cookie.kwrg.module.modules.config.ShareModule.openShareGui();
                                    return 1;
                                }))
                        .executes(context -> {
                            me.cookie.kwrg.module.modules.config.ShareModule.openShareGui();
                            return 1;
                        }))
                .then(literal("waypoint")
                        .then(literal("chat")
                                .then(argument("x", IntegerArgumentType.integer(-MAX_COORD, MAX_COORD))
                                        .then(argument("y", IntegerArgumentType.integer(-MAX_COORD, MAX_COORD))
                                                .then(argument("z", IntegerArgumentType.integer(-MAX_COORD, MAX_COORD))
                                                        .then(argument("label", StringArgumentType.greedyString())
                                                                .executes(context -> ChatWaypointManager.mark(
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        IntegerArgumentType.getInteger(context, "z"),
                                                                        StringArgumentType.getString(context, "label")))))))))
                .then(literal("coords")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) {
                                IrcMessageHandler.printMessage("§c[IRC] 当前不在世界中");
                                return 0;
                            }
                            var pos = mc.player.blockPosition();
                            String coords = WaypointManager.formatCoordMessage(pos.getX(), pos.getY(), pos.getZ());
                            String island = SkyblockLocation.island().replaceAll("[^A-Za-z0-9_]", "");
                            String server = SkyblockLocation.server().replaceAll("[^A-Za-z0-9_]", "");
                            String location = island.isEmpty() ? server : server.isEmpty() ? island : island + " @ " + server;
                            IrcClient.sendMessage(location.isEmpty() ? coords : coords + " | " + location);
                            return 1;
                        }))
                .then(rankAlias("rank"))
                .then(rankAlias("r"))
                .then(nameStyleAlias("nameStyle"))
                .then(nameStyleAlias("ns"))
                .then(messageStyleAlias("messageStyle"))
                .then(messageStyleAlias("ms"))
                .then(playAlias("play"))
                .then(playAlias("p"))
                .then(importantAlias("important"))
                .then(importantAlias("imp"))
                .then(configAlias("config"))
                .then(configAlias("cfg"))
                .then(literal("online")
                        .executes(context -> {
                            IrcClient.queryOnline();
                            return 1;
                        }))
                .then(literal("routeEdit")
                        .executes(context -> RouteEditor.setEnabled(!RouteEditor.isEnabled()))
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(context -> RouteEditor.setEnabled(BoolArgumentType.getBool(context, "enabled")))))
//                .then(routeCommand())
                .then(literal("debug")
                        .then(literal("wordTextRenderTest")
                                .executes(context -> WorldTextRenderer.toggleDebugTest()))
                        .then(literal("clearImgCache")
                                .executes(context -> ClearIndexCacheModule.handleClearImgCache()))
                        .then(literal("connect")
                                .executes(context -> {
                                    IrcClient.connect();
                                    return 1;
                                }))
                        .then(literal("disconnect")
                                .executes(context -> {
                                    IrcClient.disconnect();
                                    IrcMessageHandler.printMessage("§c[IRC] 已断开 IRC 连接");
                                    return 1;
                                }))
                        .then(literal("clearIndexCache")
                                .executes(context -> {
                                    SkyblockItemHelper.invalidateCache();
                                    return 1;
                                }))
                        .then(literal("reloadRepo")
                                .executes(context -> {
                                    SkyblockItemHelper.reloadRepo();
                                    return 1;
                                }))
                        .then(literal("ping")
                                .executes(context -> {
                                    me.cookie.kwrg.module.modules.debug.PingModule.executePing(true);
                                    return 1;
                                })))
                .then(functionsAlias("functions"))
                .then(functionsAlias("func"))
                .then(literal("gui").executes(ctx -> {
                    IrcScreen.showGui();
                    return 1;
                }))
                .then(hudEditorAlias("hudeditor"))
                .then(hudEditorAlias("hud"))
                .then(literal("getBan")
                        .then(argument("type", StringArgumentType.word())
                                .suggests(BAN_TYPE_SUGGESTIONS)
                                .executes(context -> getBanUtil.triggerBan(
                                        StringArgumentType.getString(context, "type"), 0, null))
                                .then(argument("delay", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> getBanUtil.triggerBan(
                                                StringArgumentType.getString(context, "type"),
                                                DoubleArgumentType.getDouble(context, "delay"),
                                                null))
                                        .then(argument("duration", StringArgumentType.greedyString())
                                                .suggests(BAN_DURATION_SUGGESTIONS)
                                                .executes(context -> getBanUtil.triggerBan(
                                                        StringArgumentType.getString(context, "type"),
                                                        DoubleArgumentType.getDouble(context, "delay"),
                                                        StringArgumentType.getString(context, "duration"))))))
                        .executes(context -> {
                            IrcMessageHandler.printMessage("§c[IRC] 用法: /irc getBan <类型> [触发延迟(秒)] [自定义时长]");
                            return 0;
                        }));


        registerRoot(dispatcher, ircRoot);
        registerRoot(dispatcher, routeCommand());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> hudEditorAlias(String alias) {
        return literal(alias).executes(ctx -> {
            openHudEditor();
            return 1;
        });
    }

    private static void openHudEditor() {
        HudEditorScreen.open(null);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> routeCommand() {
        return literal("route")
                .then(literal("show").executes(context -> {
                    RouteManager.showInChat();
                    return 1;
                }))
                .then(literal("add")
                        .then(argument("name", StringArgumentType.string())
                                .executes(context -> addRoute(context, RouteManager.entries().size() + 1))
                                .then(argument("x", DoubleArgumentType.doubleArg())
                                        .then(argument("y", DoubleArgumentType.doubleArg())
                                                .then(argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(IrcCommand::addRouteAtCoordinates))))))
                .then(routeInsertAlias("ins"))
                .then(routeInsertAlias("insert"))
                .then(routeDeleteAlias("del"))
                .then(routeDeleteAlias("delete"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> routeInsertAlias(String alias) {
        return literal(alias)
                .then(argument("name", StringArgumentType.string())
                        .then(argument("before", IntegerArgumentType.integer(1))
                                .then(argument("after", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int before = IntegerArgumentType.getInteger(context, "before");
                                            int after = IntegerArgumentType.getInteger(context, "after");
                                            if (before >= RouteManager.entries().size() || after != before + 1) {
                                                ChatNotifier.send("§c[Route] 请指定两个相邻且存在的节点索引，例如 /route ins <节点名> 2 3", NotificationType.ERR);
                                                return 0;
                                            }
                                            return addRoute(context, after);
                                        }))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> routeDeleteAlias(String alias) {
        return literal(alias)
                .then(argument("index", IntegerArgumentType.integer(1))
                        .executes(context -> deleteRoute(IntegerArgumentType.getInteger(context, "index"))));
    }

    private static int addRouteAtCoordinates(CommandContext<FabricClientCommandSource> context) {
        return RouteEditor.addNode(StringArgumentType.getString(context, "name"),
                DoubleArgumentType.getDouble(context, "x"),
                DoubleArgumentType.getDouble(context, "y"),
                DoubleArgumentType.getDouble(context, "z"), RouteManager.entries().size() + 1);
    }

    private static int addRoute(CommandContext<FabricClientCommandSource> context, int position) {
        var player = context.getSource().getPlayer();
        if (player == null) {
            ChatNotifier.send("§c[Route] 请先进入世界", NotificationType.ERR);
            return 0;
        }
        return RouteEditor.addNode(StringArgumentType.getString(context, "name"), player.blockPosition(), position);
    }

    private static int deleteRoute(int position) {
        if (position < 1 || position > RouteManager.entries().size()) {
            ChatNotifier.send("§c[Route] 节点索引不存在 | " + position, NotificationType.ERR);
            return 0;
        }
        if (!RouteManager.deleteAt(position - 1)) return 0;
        ChatNotifier.send("§e[Route] 已删除位置 | " + position, NotificationType.SUCCESS);
        return 1;
    }

    private static void sendHelp() {
        IrcMessageHandler.printMessage("§6===== [Kwrg IRC 帮助] =====");
        IrcMessageHandler.printMessage("§e/ci §7或 §e/chat irc §7- 切换 IRC 聊天频道");
        for (CommandNode<FabricClientCommandSource> root : HELP_ROOTS) {
            printHelp("/" + root.getName(), root, 0);
        }
    }

    private static void printHelp(String path, CommandNode<FabricClientCommandSource> node, int depth) {
        for (CommandNode<FabricClientCommandSource> child : node.getChildren()) {
            String usage = path + " " + child.getUsageText();
            IrcMessageHandler.printMessage("§e" + usage);
            if (depth + 1 < HELP_MAX_DEPTH) {
                printHelp(usage, child, depth + 1);
            }
        }
    }
}
