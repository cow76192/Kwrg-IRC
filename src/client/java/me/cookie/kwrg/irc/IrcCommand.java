package me.cookie.kwrg.irc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.hud.HudEditorScreen;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.RouteEditor;
import me.cookie.kwrg.irc.manager.RouteManager;
import me.cookie.kwrg.module.modules.debug.ClearIndexCacheModule;
import me.cookie.kwrg.module.modules.functions.GlowColorModule;
import me.cookie.kwrg.module.modules.functions.HideArmorModule;
import me.cookie.kwrg.module.modules.functions.ImportantPlayerModule;
import me.cookie.kwrg.module.modules.play.PlayModule;
import me.cookie.kwrg.module.modules.style.MessageStyleModule;
import me.cookie.kwrg.module.modules.style.NameStyleModule;
import me.cookie.kwrg.module.modules.style.RankModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.SkyblockItemHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

import static me.cookie.kwrg.module.modules.config.LoadModule.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class IrcCommand {

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

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(literal("ci")
                .then(literal("all")
                        .executes(context -> {
                            IrcClient.isIrcChatActive = true;
                            IrcConfig config = IrcConfig.get();
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
                                    IrcConfig config = IrcConfig.get();

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
                            IrcConfig config = IrcConfig.get();

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
                    IrcConfig config = IrcConfig.get();

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

        dispatcher.register(literal("ic")
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

        dispatcher.register(literal("icall")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            IrcClient.sendMessageToPublic(message);
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
                                .then(argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                        .then(argument("y", IntegerArgumentType.integer(-30000000, 30000000))
                                                .then(argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                        .then(argument("label", StringArgumentType.greedyString())
                                                                .executes(context -> me.cookie.kwrg.irc.manager.ChatWaypointManager.mark(
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        IntegerArgumentType.getInteger(context, "z"),
                                                                        StringArgumentType.getString(context, "label")))))))))
                .then(literal("rank")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(context -> RankModule.setRank(StringArgumentType.getString(context, "name"))))
                        .executes(context -> RankModule.showRank()))
                .then(literal("r")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(context -> RankModule.setRank(StringArgumentType.getString(context, "name"))))
                        .executes(context -> RankModule.showRank()))
                .then(literal("nameStyle")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> NameStyleModule.setNameStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> NameStyleModule.showNameStyle()))
                .then(literal("ns")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> NameStyleModule.setNameStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> NameStyleModule.showNameStyle()))
                .then(literal("messageStyle")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> MessageStyleModule.setChatStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> MessageStyleModule.showChatStyle()))
                .then(literal("ms")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> MessageStyleModule.setChatStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> MessageStyleModule.showChatStyle()))
                .then(literal("play")
                        .then(argument("player", StringArgumentType.string())
                                .then(argument("item", StringArgumentType.greedyString())
                                        .suggests(ITEM_SUGGESTIONS)
                                        .executes(context -> PlayModule.handlePlayCommand(
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "item")
                                        )))))
                .then(literal("p")
                        .then(argument("player", StringArgumentType.string())
                                .then(argument("item", StringArgumentType.greedyString())
                                        .suggests(ITEM_SUGGESTIONS)
                                        .executes(context -> PlayModule.handlePlayCommand(
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "item")
                                        )))))
                .then(literal("important")
                        .then(argument("player", StringArgumentType.string())
                                .executes(context -> ImportantPlayerModule.toggleImportant(StringArgumentType.getString(context, "player"))))
                        .executes(context -> ImportantPlayerModule.listImportant()))
                .then(literal("imp")
                        .then(argument("player", StringArgumentType.string())
                                .executes(context -> ImportantPlayerModule.toggleImportant(StringArgumentType.getString(context, "player"))))
                        .executes(context -> ImportantPlayerModule.listImportant()))
                .then(literal("config")
                        .then(literal("load")
                                .then(argument("name", StringArgumentType.string())
                                        .suggests(CONFIG_SUGGESTIONS)
                                        .executes(context -> loadConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("create")
                                .then(argument("name", StringArgumentType.string())
                                        .executes(context -> createConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("save")
                                .executes(context -> saveConfig()))
                        .executes(context -> showCurrentConfig()))
                .then(literal("cfg")
                        .then(literal("load")
                                .then(argument("name", StringArgumentType.string())
                                        .suggests(CONFIG_SUGGESTIONS)
                                        .executes(context -> loadConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("create")
                                .then(argument("name", StringArgumentType.string())
                                        .executes(context -> createConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("save")
                                .executes(context -> saveConfig()))
                        .executes(context -> showCurrentConfig()))
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
                .then(literal("functions")
                        .then(hideArmorNode())
                        .then(literal("glowColor")
                                .then(argument("color", StringArgumentType.greedyString())
                                        .suggests(STYLE_SUGGESTIONS)
                                        .executes(context -> GlowColorModule.setGlowColor(StringArgumentType.getString(context, "color"))))
                                .executes(context -> GlowColorModule.showGlowColor())))
                .then(literal("func")
                        .then(hideArmorNode())
                        .then(literal("glowColor")
                                .then(argument("color", StringArgumentType.greedyString())
                                        .suggests(STYLE_SUGGESTIONS)
                                        .executes(context -> GlowColorModule.setGlowColor(StringArgumentType.getString(context, "color"))))
                                .executes(context -> GlowColorModule.showGlowColor())))
                .then(literal("gui").executes(ctx -> {
                    IrcScreen.showGui();
                    return 1;
                }))
                .then(hudEditorAlias("hudeditor"))
                .then(hudEditorAlias("hud"));

        dispatcher.register(ircRoot);
        dispatcher.register(routeCommand());
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
        IrcMessageHandler.printMessage("§e/ic <消息> §7- 快捷发送一条 IRC 消息");
        IrcMessageHandler.printMessage("§e/irc r <称号> §7- 设置称号 (如 /irc r &6VIP&b+)");
        IrcMessageHandler.printMessage("§e/irc ns <样式> §7- 设置名字样式 (支持彩虹、渐变、CSS)");
        IrcMessageHandler.printMessage("§e/irc ms <样式> §7- 设置消息样式 (支持彩虹、渐变、CSS)");
        IrcMessageHandler.printMessage("§e/irc important/imp [玩家名] §7- 添加/移除关注玩家 (不区分大小写)");
        IrcMessageHandler.printMessage("§e/irc config/cfg load/create <配置名> §7- 加载/新建配置");
        IrcMessageHandler.printMessage("§e/irc config/cfg save §7- 保存当前配置至文件(只有新建的配置文件才需要保存)");
        IrcMessageHandler.printMessage("§e/irc play/p <玩家名> <物品ID> §7- 向指定玩家发送 Totem 动画效果");
        IrcMessageHandler.printMessage("§e/irc online §7- 查看当前 IRC 在线玩家列表");
        IrcMessageHandler.printMessage("§e/irc routeEdit [true/false] §7- 切换或指定快速编辑状态，开启后 Alt＋左键方块添加节点");
        IrcMessageHandler.printMessage("§e/route show §7- 查看当前路线节点（索引从 1 开始）");
        IrcMessageHandler.printMessage("§e/route add <节点名> [<x> <y> <z>] §7- 在末尾添加节点，省略坐标时使用当前位置");
        IrcMessageHandler.printMessage("§e/route ins/insert <节点名> <前索引> <后索引> §7- 在相邻节点间插入当前位置");
        IrcMessageHandler.printMessage("§e/route del/delete <索引> §7- 删除指定节点");
        IrcMessageHandler.printMessage("§e/irc hudeditor §7或 §e/irc hud §7- 打开 HUD 布局编辑器");
        IrcMessageHandler.printMessage("§e/irc functions §7- 查看功能列表");
        IrcMessageHandler.printMessage("§e/irc debug  §7- 查看调试选项");
    }
}
