package me.cookie.kwrg.window.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.sun.jna.Platform;
import me.cookie.kwrg.CookieMain;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Coordinates passive Win32 keyboard interceptors on top of {@link WindowKeyboardInput},
 * with automatic fallback to GLFW polling on non-Windows platforms or when native hooking fails.
 */
public final class WindowKeyInterceptManager {
    private static final CopyOnWriteArrayList<WindowKeyboardInput.KeyInterceptor> INTERCEPTORS = new CopyOnWriteArrayList<>();
    private static final Map<Integer, WindowKeyboardInput.KeyInterceptor> OWNERS = new ConcurrentHashMap<>();
    private static long attachedWindow;
    private static boolean attached;

    private static final WindowKeyboardInput.KeyInterceptor DISPATCHER = new WindowKeyboardInput.KeyInterceptor() {
        @Override
        public boolean key(int key, int action, int modifiers) {
            if (action == GLFW.GLFW_PRESS) {
                for (WindowKeyboardInput.KeyInterceptor interceptor : INTERCEPTORS) {
                    if (interceptor.key(key, action, modifiers)) {
                        OWNERS.put(key, interceptor);
                        return true;
                    }
                }
                return false;
            }

            WindowKeyboardInput.KeyInterceptor owner = action == GLFW.GLFW_RELEASE
                    ? OWNERS.remove(key)
                    : OWNERS.get(key);
            if (owner != null) {
                owner.key(key, action, modifiers);
                return true;
            }
            return false;
        }

        @Override
        public void reset() {
            OWNERS.clear();
            for (WindowKeyboardInput.KeyInterceptor interceptor : INTERCEPTORS) {
                interceptor.reset();
            }
        }
    };

    private WindowKeyInterceptManager() { }

    public static synchronized boolean register(long window, WindowKeyboardInput.KeyInterceptor interceptor) {
        if (interceptor == null) return false;
        if (!INTERCEPTORS.contains(interceptor)) INTERCEPTORS.add(interceptor);
        return attach(window);
    }

    public static synchronized void unregister(WindowKeyboardInput.KeyInterceptor interceptor) {
        if (interceptor == null) return;
        INTERCEPTORS.remove(interceptor);
        OWNERS.values().removeIf(owner -> owner == interceptor);
        interceptor.reset();
        if (INTERCEPTORS.isEmpty() && attached) {
            long window = attachedWindow;
            attached = false;
            attachedWindow = 0;
            try {
                WindowKeyboardInput.setPassiveInterceptor(window, null);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
    }

    private static boolean attach(long window) {
        if (!Platform.isWindows() || window == 0) return false;
        if (attached && attachedWindow == window) return true;
        try {
            WindowKeyboardInput.setPassiveInterceptor(window, DISPATCHER);
            attachedWindow = window;
            attached = true;
            return true;
        } catch (RuntimeException | LinkageError failure) {
            attached = false;
            attachedWindow = 0;
            CookieMain.LOGGER.warn("Window key interception initialization failed", failure);
            return false;
        }
    }

    public static KeyHoldGuard holdGuard(int... keys) {
        return new KeyHoldGuard(keys);
    }

    public static final class KeyHoldGuard {
        private final int[] keys;
        private final Set<Integer> downKeys = ConcurrentHashMap.newKeySet();
        private volatile boolean active;
        private boolean registered;
        private boolean nativeIntercepted;

        private final WindowKeyboardInput.KeyInterceptor interceptor = new WindowKeyboardInput.KeyInterceptor() {
            @Override
            public boolean key(int key, int action, int modifiers) {
                if (!matches(key)) return false;
                if (action == GLFW.GLFW_PRESS) {
                    if (!active) return false;
                    downKeys.add(key);
                    return true;
                }
                if (action == GLFW.GLFW_RELEASE) {
                    downKeys.remove(key);
                    return true;
                }
                return true;
            }

            @Override
            public void reset() {
                downKeys.clear();
            }
        };

        private KeyHoldGuard(int... keys) {
            this.keys = keys.clone();
        }

        public void enable(long window, boolean active) {
            this.active = active;
            downKeys.clear();
            if (!registered) {
                registered = true;
                nativeIntercepted = register(window, interceptor);
            }
        }

        public void setActive(boolean active) {
            this.active = active;
            if (!active) downKeys.clear();
        }

        public void disable() {
            active = false;
            downKeys.clear();
            if (registered) {
                registered = false;
                nativeIntercepted = false;
                unregister(interceptor);
            }
        }

        public boolean isDown(Window window) {
            if (!active) return false;
            if (nativeIntercepted) return !downKeys.isEmpty();
            if (window == null) return false;
            for (int key : keys) {
                if (InputConstants.isKeyDown(window, key)) return true;
            }
            return false;
        }

        private boolean matches(int key) {
            for (int target : keys) {
                if (target == key) return true;
            }
            return false;
        }
    }
}
