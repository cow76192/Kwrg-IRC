package me.cookie.kwrg.util;

import java.lang.reflect.InvocationTargetException;

public final class WindowThreadUtil {
    private WindowThreadUtil() {
    }

    public static void run(Runnable action) {
        Class<?> api;
        try {
            api = Class.forName("me.decce.ixeris.api.IxerisApi");
        } catch (ClassNotFoundException absent) {
            action.run();
            return;
        }

        try {
            // Native window lifecycles must finish on the owner thread before control continues.
            Object instance = api.getMethod("getInstance").invoke(null);
            api.getMethod("runNowOnMainThread", Runnable.class).invoke(instance, action);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Window thread task failed", cause);
        } catch (ReflectiveOperationException incompatible) {
            throw new IllegalStateException("Ixeris window thread API unavailable", incompatible);
        }
    }
}
