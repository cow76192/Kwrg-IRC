package me.cookie.kwrg.skyblock.route;

import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.util.ChatNotifier;

import java.util.regex.Matcher;

final class ChatWaypointParser {

    private ChatWaypointParser() {
    }

    static int[] parse(String message) {
        if (message == null || message.isEmpty()) return null;

        String text = ChatNotifier.FORMATTING_PATTERN.matcher(message).replaceAll("");
        if (text.length() > ChatWaypointManager.MAX_MESSAGE_LENGTH) return null;

        Matcher matcher = ChatWaypointManager.LABELED_PATTERN.matcher(text);
        if (!matcher.find()) {
            matcher = ChatWaypointManager.BARE_PATTERN.matcher(text.trim());
            if (!matcher.matches()) return null;
        }
        return blockPos(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    static int[] blockPos(String x, String y, String z) {
        try {
            double dx = Double.parseDouble(x);
            double dy = Double.parseDouble(y);
            double dz = Double.parseDouble(z);
            if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return null;
            if (Math.abs(dx) > ChatWaypointManager.MAX_COORD || Math.abs(dy) > ChatWaypointManager.MAX_COORD || Math.abs(dz) > ChatWaypointManager.MAX_COORD) return null;
            return new int[]{(int) Math.floor(dx), (int) Math.floor(dy), (int) Math.floor(dz)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String label(String rank, String username) {
        String formatted = IrcMessageHandler.formatRankWithBrackets(rank);
        String raw = username == null ? "" : username;
        String name = formatted.isEmpty() ? raw : formatted + " " + raw;
        String clean = ChatWaypointManager.UNSAFE_PATTERN.matcher(ChatNotifier.FORMATTING_PATTERN.matcher(name).replaceAll("")).replaceAll("").trim();
        if (clean.isEmpty()) return "Unknown";
        return clean.length() > WaypointManager.MAX_NAME_LENGTH ? clean.substring(0, WaypointManager.MAX_NAME_LENGTH) : clean;
    }
}
