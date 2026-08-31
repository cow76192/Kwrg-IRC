package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;

public class GreetingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg");

    private static boolean hasGreetedInCurrentSession = false;
    private static int delayTicks = -1;

    public static void onSessionJoin() {
        if (!hasGreetedInCurrentSession) {
            delayTicks = 60;
        }
    }

    public static void onSessionDisconnect() {
        hasGreetedInCurrentSession = false;
        delayTicks = -1;
    }

    public static void tick(Minecraft client) {
        if (delayTicks > 0) {
            delayTicks--;
            if (delayTicks == 0) {
                trySendGreeting(client);
            }
        }
    }

    private static void trySendGreeting(Minecraft client) {
        if (client.player == null) {
            delayTicks = 20;
            return;
        }

        String playerName = client.getUser().getName();
        String entityName = client.player.getName().getString();

        if ("kwrg".equalsIgnoreCase(playerName) || "kwrg".equalsIgnoreCase(entityName)) {
            String period = getTimePeriod();

            client.gui.setTimes(20, 80, 20);
            client.gui.setTitle(Component.literal("§d§l宝宝" + period + "好~"));

            hasGreetedInCurrentSession = true;
        }
    }

    private static String getTimePeriod() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 9) {
            return "早上";
        } else if (hour >= 9 && hour < 12) {
            return "上午";
        } else if (hour >= 12 && hour < 14) {
            return "中午";
        } else if (hour >= 14 && hour < 18) {
            return "下午";
        } else {
            return "晚上";
        }
    }
}