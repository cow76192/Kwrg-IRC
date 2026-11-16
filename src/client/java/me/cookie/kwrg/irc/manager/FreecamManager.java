package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.module.modules.cheat.FreecamModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class FreecamManager {
    private static final ClientInput CAMERA_INPUT = new ClientInput() {
        @Override
        public void tick() {
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        }

        @Override
        public void makeJump() {
            // A pending vanilla auto-jump must not turn camera movement into a body jump.
        }
    };

    private static LocalPlayer player;
    private static ClientLevel level;
    private static ClientInput savedInput;
    private static Vec3 position = Vec3.ZERO;
    private static float yaw;
    private static float pitch;
    private static long lastFrame;

    private FreecamManager() {}

    public static boolean isActive() {
        return player != null;
    }

    public static boolean enable() {
        Minecraft client = Minecraft.getInstance();
        if (isActive()) return true;

        if (client.player == null || client.level == null || !client.player.isAlive() || client.getCameraEntity() != client.player) return false;

        Camera camera = client.gameRenderer.getMainCamera();
        player = client.player;
        level = client.level;
        position = camera.isInitialized() ? camera.position() : player.getEyePosition();
        yaw = camera.isInitialized() ? camera.yRot() : player.getYRot();
        pitch = camera.isInitialized() ? camera.xRot() : player.getXRot();
        savedInput = player.input;
        CAMERA_INPUT.tick();
        player.input = CAMERA_INPUT;
        lastFrame = System.nanoTime();

        client.levelRenderer.allChanged();
        return true;
    }

    public static void disable() {
        if (!isActive()) return;

        if (player.input == CAMERA_INPUT) player.input = savedInput;
        player = null;
        level = null;
        savedInput = null;
        lastFrame = 0;
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) client.levelRenderer.allChanged();
    }


     public static void validateSession(Minecraft client) {
        if (isActive() && (client.player != player || client.level != level || !player.isAlive()
                || client.getCameraEntity() != player || player.input != CAMERA_INPUT)) {
            disable();
        }
    }

    public static void turn(double horizontal, double vertical) {
        yaw = Mth.wrapDegrees(yaw + (float) (horizontal * 0.15));
        pitch = Mth.clamp(pitch + (float) (vertical * 0.15), -90.0f, 90.0f);
    }

    public static void updateCamera() {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        double seconds = Math.min((now - lastFrame) / 1_000_000_000.0, 0.1);
        lastFrame = now;

        if (client.screen != null || client.getOverlay() != null || client.isPaused()
                || !client.isWindowActive() || !client.mouseHandler.isMouseGrabbed()) return;

        var options = client.options;
        double forward = axis(options.keyUp.isDown(), options.keyDown.isDown());
        double left = axis(options.keyLeft.isDown(), options.keyRight.isDown());
        double up = axis(options.keyJump.isDown(), options.keyShift.isDown());
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);

        Vec3 motion = new Vec3(
                -Math.sin(yawRadians) * Math.cos(pitchRadians) * forward + Math.cos(yawRadians) * left,
                -Math.sin(pitchRadians) * forward + up,
                Math.cos(yawRadians) * Math.cos(pitchRadians) * forward + Math.sin(yawRadians) * left);
        if (motion.lengthSqr() > 1.0) motion = motion.normalize();
        double speed = FreecamModule.CONFIG.get().freecamSpeed;

        if (options.keySprint.isDown()) speed *= 3.0;
        position = position.add(motion.scale(speed * seconds));
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1.0 : 0.0) - (negative ? 1.0 : 0.0);
    }

    public static Vec3 position() { return position; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
}
