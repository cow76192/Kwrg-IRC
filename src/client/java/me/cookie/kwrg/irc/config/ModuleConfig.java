package me.cookie.kwrg.irc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.Set;
import java.util.function.Consumer;

/** Describes the fields a module contributes to a profile. */
public final class ModuleConfig<T> {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final Class<T> type;
    private final Consumer<T> sanitize;

    public ModuleConfig(Class<T> type) {
        this(type, settings -> {});
    }

    public ModuleConfig(Class<T> type, Consumer<T> sanitize) {
        this.type = type;
        this.sanitize = sanitize;
    }

    /** Returns the active profile's settings. Read again after a profile change. */
    public T get() {
        return IrcConfig.current().settings(this);
    }

    /** Applies a change to the active profile and saves it before a profile switch can intervene. */
    public boolean update(Consumer<T> change) {
        synchronized (IrcConfig.class) {
            change.accept(get());
            return IrcConfig.save();
        }
    }

    /** Applies a change immediately and batches the disk write with other pending changes. */
    public void updateDelayed(long delayMs, Consumer<T> change) {
        synchronized (IrcConfig.class) {
            change.accept(get());
            IrcConfig.saveDelayed(delayMs);
        }
    }

    Set<String> keys() {
        return GSON.toJsonTree(read(null)).getAsJsonObject().keySet();
    }

    T read(JsonObject json) {
        T settings = GSON.fromJson(json == null ? new JsonObject() : json, type);
        if (json != null) sanitize.accept(settings);
        return settings;
    }

    T cast(Object value) {
        return type.cast(value);
    }
}
