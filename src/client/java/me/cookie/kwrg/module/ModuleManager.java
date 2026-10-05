package me.cookie.kwrg.module;

import me.cookie.kwrg.module.modules.config.*;
import me.cookie.kwrg.module.modules.debug.*;
import me.cookie.kwrg.module.modules.functions.*;
import me.cookie.kwrg.module.modules.play.PlayModule;
import me.cookie.kwrg.module.modules.style.*;

import java.util.*;

public class ModuleManager {

    private static final Map<String, Module> MODULES = new LinkedHashMap<>();

    static {
        // --- DEBUG 模块 ---
        register(new ConnectModule());
        register(new DisconnectModule());
        register(new ClearIndexCacheModule());
        register(new ReloadRepoModule());
        register(new PingModule());
        register(new DisableBlurModule());
        register(new GuiFpsModule());
        register(new DynamicIslandAAValve());
        register(new ClearCapeCacheModule());
        // --- CONFIG 模块 ---
        register(new LoadModule());
        register(new ShareModule());
        // --- FUNCTIONS 模块 ---
        register(new HideArmorModule());
        register(new GlowColorModule());
        register(new OnlineModule());
        register(new ImportantPlayerModule());
        register(new PlayerFinderModule());
        register(new ChannelModule());
        register(new ModelScaleModule());
        register(new CustomCapeModule());
        register(new NickModule());

        // --- STYLE 模块 ---
        register(new RankModule());
        register(new NameStyleModule());
        register(new MessageStyleModule());
        register(new ChatModule());
        register(new DynamicIslandModule());
        register(new FontModule());
        register(new GuiColorModule());
        register(new GuiScaleModule());
        register(new HudEditorModule());
        // --- PLAY 模块 ---
        register(new PlayModule());
    }

    public static void register(Module module) {
        MODULES.put(module.getName().toLowerCase(Locale.ROOT), module);
    }

    public static Module getModule(String name) {
        if (name == null) return null;
        return MODULES.get(name.toLowerCase(Locale.ROOT));
    }

    public static List<Module> getModulesByCategory(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : MODULES.values()) {
            if (m.getCategory() == category) {
                list.add(m);
            }
        }
        return list;
    }

    public static boolean updateOnlinePlayers(List<String> players) {
        return OnlineModule.updateOnlinePlayers(players);
    }

    public static void setPendingGuiQuery(boolean pending) {
        OnlineModule.setPendingGuiQuery(pending);
    }

    public static List<String> getCachedOnlinePlayers() {
        return OnlineModule.getCachedOnlinePlayers();
    }
}