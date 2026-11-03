package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class DanceRoomManager {

    private static final Minecraft MC = Minecraft.getInstance();

    private static final AABB DANCE_ROOM_BOX = new AABB(-267.0, 32.0, -110.0, -260.0, 40.0, -102.0);

    private static final double STOP_TOLERANCE = 0.22;

    private static final List<String> INSTRUCTIONS = List.of(
            "move", // index 0 (准备阶段)
            "move", "move", "move", "move", // index 1 ~ 4 (第 1 圈)
            "sneak", "stand", "sneak", "stand", // index 5 ~ 8 (第 2 圈)
            "sneak", "stand", "sneak", "stand", // index 9 ~ 12 (第 3 圈)
            "sneak jump", "jump", "sneak", "stand", // index 13 ~ 16 (第 4 圈)
            "sneak jump", "jump", "sneak", "stand", // index 17 ~ 20 (第 5 圈)
            "sneak jump", "jump", "sneak", "stand", // index 21 ~ 24 (第 6 圈)
            "sneak jump", "jump", "sneak", "stand", // index 25 ~ 28 (第 7 圈)
            "sneak jump", "jump", "sneak", "stand", // index 29 ~ 32 (第 8 圈)
            "sneak jump punch", "jump punch", "sneak punch", "punch", // index 33 ~ 36 (第 9 圈)
            "sneak jump punch", "jump punch", "sneak punch", "punch", // index 37 ~ 40 (第 10 圈)
            "sneak jump punch", "jump punch", "sneak punch", "punch", // index 41 ~ 44 (第 11 圈)
            "sneak jump punch", "jump punch", "sneak punch", "punch"  // index 45 ~ 48 (第 12 圈)
    );

    private static boolean enabled = false;
    private static boolean inRoom = false;

    // 游戏节拍状态
    private static int index = 0;
    private static int countdownTicks = 0;

    private static Vec3 originPos = null;
    private static boolean primaryMoveDone = false;
    private static boolean crossCorrecting = false;

    private DanceRoomManager() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static void setEnabled(boolean state) {
        enabled = state;
        resetStateAndRelease();
        if (enabled) {
            ChatNotifier.send("§a[DanceRoom] 自动跳舞已开启 | 请站到绿色玻璃中心并固定视角", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§e[DanceRoom] 自动跳舞已关闭", NotificationType.INFO);
        }
    }

    public static boolean isClockwise() {
        return IrcConfig.get().danceRoomClockwise;
    }

    public static void toggleClockwise() {
        IrcConfig config = IrcConfig.get();
        config.danceRoomClockwise = !config.danceRoomClockwise;
        IrcConfig.saveDelayed(500);
    }

    public static int getJumpCountdownTick() {
        return IrcConfig.get().danceRoomJumpTick;
    }

    public static int getPunchCountdownTick() {
        return IrcConfig.get().danceRoomPunchTick;
    }

    public static double getIgnoreDriftDeadzone() {
        return IrcConfig.get().danceRoomDeadzone;
    }

    public static boolean saveTimingSettings(String[] values) {
        if (values == null || values.length < 2) return false;
        try {
            double jRaw = Double.parseDouble(values[0].trim());
            double pRaw = Double.parseDouble(values[1].trim());
            if (!Double.isFinite(jRaw) || !Double.isFinite(pRaw)) return false;

            int j = (int) Math.round(jRaw);
            int p = (int) Math.round(pRaw);

            IrcConfig config = IrcConfig.get();
            config.danceRoomJumpTick = Mth.clamp(j, 1, 18);
            config.danceRoomPunchTick = Mth.clamp(p, 1, 18);
            IrcConfig.save();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean saveDeadzoneSetting(String[] values) {
        if (values == null || values.length < 1) return false;
        try {
            double d = Double.parseDouble(values[0].trim());
            if (!Double.isFinite(d)) return false;

            IrcConfig config = IrcConfig.get();
            config.danceRoomDeadzone = Mth.clamp(d, 0.25, 0.90);
            IrcConfig.save();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean saveSettings(String[] values) {
        if (values == null || values.length < 3) return false;
        return saveTimingSettings(new String[]{values[0], values[1]})
                && saveDeadzoneSetting(new String[]{values[2]});
    }

    public static void resetStateAndRelease() {
        inRoom = false;
        index = 0;
        countdownTicks = 0;
        primaryMoveDone = false;
        crossCorrecting = false;
        originPos = null;
        MovementUtil.releaseAll();
    }

    public static void resetState() {
        index = 0;
        countdownTicks = 0;
        primaryMoveDone = false;
        crossCorrecting = false;
        originPos = MC.player != null ? MC.player.position() : null;
    }

    public static void onClientTick() {
        if (!enabled || MC.player == null || MC.level == null) {
            if (inRoom || index > 0 || countdownTicks > 0 || originPos != null) {
                resetStateAndRelease();
            }
            return;
        }

        boolean currentlyInRoom = DANCE_ROOM_BOX.contains(MC.player.position());
        if (!currentlyInRoom) {
            if (inRoom || index > 0 || countdownTicks > 0) {
                resetStateAndRelease();
            }
            return;
        }
        inRoom = true;

        if (index == 0) {
            originPos = MC.player.position();
            return;
        }

        executeCurrentStepTick();
        if (countdownTicks > 0) {
            countdownTicks--;
        }
    }

    public static void onSoundReceived(String soundPath, float pitch, float volume) {
        if (!enabled || !inRoom || MC.player == null || MC.level == null) return;

        // block.note_block.bass
        if (soundPath.endsWith("block.note_block.bass") && volume == 1.0f
                && (approxEquals(pitch, 0.6984127f) || approxEquals(pitch, 0.52380955f))) {

            if (index == 0) {
                originPos = MC.player.position();
            }
            index++;
            countdownTicks = 20;
            primaryMoveDone = false;
            crossCorrecting = false;
            return;
        }

        // entity.player.burp 或 entity.player.levelup
        boolean isBurp = soundPath.endsWith("entity.player.burp") && approxEquals(volume, 0.8f);
        boolean isLevelUp = soundPath.endsWith("entity.player.levelup") && approxEquals(pitch, 1.8412699f) && volume == 1.0f;

        if ((isBurp || isLevelUp) && (index > 0 || countdownTicks > 0)) {
            resetStateAndRelease();
            inRoom = DANCE_ROOM_BOX.contains(MC.player.position());
            if (isLevelUp) {
                ChatNotifier.send("§a[DanceRoom] 恭喜完成整支舞蹈！", NotificationType.SUCCESS);
            } else {
                ChatNotifier.send("§c[DanceRoom] 节拍失败，已重置等待重新开始", NotificationType.INFO);
            }
        }
    }

    private static void executeCurrentStepTick() {
        if (MC.player == null || index <= 0) return;

        int instructionIdx = Math.min(index, INSTRUCTIONS.size() - 1);
        String instruction = INSTRUCTIONS.get(instructionIdx);

        boolean needSneak = instruction.contains("sneak");
        boolean needJump = instruction.contains("jump");
        boolean needPunch = instruction.contains("punch");

        int jumpTick = getJumpCountdownTick();
        int punchTick = getPunchCountdownTick();

        boolean stillWalking = moveWithDeadzone(index % 4);

        if (needSneak) {
            if (stillWalking && countdownTicks > 13) {
                MovementUtil.setSneak(false);
            } else {
                MovementUtil.setSneak(true);
            }
        } else {
            MovementUtil.setSneak(false);
        }

        if (needJump && (countdownTicks == jumpTick || countdownTicks == jumpTick - 1)) {
            MovementUtil.setJump(true);
        } else {
            MovementUtil.setJump(false);
        }

        if (needPunch && countdownTicks == punchTick) {
            MovementUtil.clickAttack();
        }
    }

    private static boolean moveWithDeadzone(int stepPhase) {
        if (MC.player == null || originPos == null) return false;

        boolean clockwise = isClockwise();
        double deadzone = getIgnoreDriftDeadzone();

        float snappedYaw = Math.round(MC.player.getYRot() / 90.0f) * 90.0f;
        float rad = snappedYaw * ((float) Math.PI / 180F);

        double fwdAxisX = -Mth.sin(rad);
        double fwdAxisZ = Mth.cos(rad);
        double sideMul = clockwise ? -1.0 : 1.0;
        double sideAxisX = Mth.cos(rad) * sideMul;
        double sideAxisZ = Mth.sin(rad) * sideMul;

        double dx = MC.player.getX() - originPos.x;
        double dz = MC.player.getZ() - originPos.z;
        double curFwd = dx * fwdAxisX + dz * fwdAxisZ;
        double curSide = dx * sideAxisX + dz * sideAxisZ;

        double targetFwd = (stepPhase == 1 || stepPhase == 2) ? 2.0 : 0.0;
        double targetSide = (stepPhase == 2 || stepPhase == 3) ? 2.0 : 0.0;

        double errFwd = targetFwd - curFwd;
        double errSide = targetSide - curSide;

        boolean pressW = false;
        boolean pressS = false;
        boolean pressLeftKey = false;
        boolean pressRightKey = false;

        boolean isFwdPrimary = (stepPhase == 1 || stepPhase == 3);

        if (isFwdPrimary) {
            if (!primaryMoveDone) {
                if ((stepPhase == 1 && errFwd > STOP_TOLERANCE) || (stepPhase == 3 && errFwd < -STOP_TOLERANCE)) {
                    pressW = (stepPhase == 1);
                    pressS = (stepPhase == 3);
                } else {
                    primaryMoveDone = true;
                }
            } else if (Math.abs(errFwd) > deadzone) {
                pressW = errFwd > 0;
                pressS = errFwd < 0;
            }

            if (Math.abs(errSide) > deadzone) {
                crossCorrecting = true;
            } else if (Math.abs(errSide) <= STOP_TOLERANCE) {
                crossCorrecting = false;
            }
            if (crossCorrecting) {
                pressLeftKey = errSide > 0;
                pressRightKey = errSide < 0;
            }
        } else {
            if (!primaryMoveDone) {
                if ((stepPhase == 2 && errSide > STOP_TOLERANCE) || (stepPhase == 0 && errSide < -STOP_TOLERANCE)) {
                    pressLeftKey = (stepPhase == 2);
                    pressRightKey = (stepPhase == 0);
                } else {
                    primaryMoveDone = true;
                }
            } else if (Math.abs(errSide) > deadzone) {
                pressLeftKey = errSide > 0;
                pressRightKey = errSide < 0;
            }

            if (Math.abs(errFwd) > deadzone) {
                crossCorrecting = true;
            } else if (Math.abs(errFwd) <= STOP_TOLERANCE) {
                crossCorrecting = false;
            }
            if (crossCorrecting) {
                pressW = errFwd > 0;
                pressS = errFwd < 0;
            }
        }

        boolean pressA = clockwise ? pressRightKey : pressLeftKey;
        boolean pressD = clockwise ? pressLeftKey : pressRightKey;

        if (pressW || pressS || pressA || pressD) {
            MovementUtil.setMove(pressW, pressS, pressA, pressD);
            return true;
        } else {
            MovementUtil.releaseMove();
            return false;
        }
    }

    private static boolean approxEquals(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}