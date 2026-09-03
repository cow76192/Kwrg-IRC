package me.cookie.kwrg.module;

import me.cookie.kwrg.module.modules.config.*;
import me.cookie.kwrg.module.modules.debug.*;
import me.cookie.kwrg.module.modules.functions.*;
import me.cookie.kwrg.module.modules.play.PlayModule;
import me.cookie.kwrg.module.modules.style.ChatModule;
import me.cookie.kwrg.module.modules.style.MessageStyleModule;
import me.cookie.kwrg.module.modules.style.NameStyleModule;
import me.cookie.kwrg.module.modules.style.RankModule;

import java.util.*;

public class ModuleManager {

    private static final Map<String, Module> MODULES = new LinkedHashMap<>();

    static {
        // --- DEBUG 模块 ---
        register(new ConnectModule());
        register(new DisconnectModule());
        register(new ClearIndexCacheModule());
        register(new ReloadRepoModule());

        // --- CONFIG 模块 ---
        register(new LoadModule());

        // --- FUNCTIONS 模块 ---
        register(new HideArmorModule());
        register(new GlowColorModule());
        register(new OnlineModule());
        register(new ImportantPlayerModule());
        register(new PlayerFinderModule());
        register(new ChannelModule());

        // --- STYLE 模块 ---
        register(new RankModule());
        register(new NameStyleModule());
        register(new MessageStyleModule());
        register(new ChatModule());

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