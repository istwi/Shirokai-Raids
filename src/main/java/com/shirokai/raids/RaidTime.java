package com.shirokai.raids;

import static com.shirokai.raids.RaidMessages.msg;

import java.util.Locale;

final class RaidTime {
    private RaidTime() {}
    static String nextRaidWarning(long seconds) {
        long total = Math.max(0, seconds);
        var parts = new java.util.ArrayList<String>();
        if (total >= 3600) parts.add(total / 3600 + "h");
        if (total % 3600 >= 60) parts.add((total % 3600) / 60 + "min");
        if (total % 60 > 0 || parts.isEmpty()) parts.add(total % 60 + "s");
        String key = total == 1 || total == 60 || total == 3600 ? "raid.aviso_singular" : "raid.aviso";
        return msg(key, "tempo", String.join(" ", parts));
    }
    static String format(long seconds) {
        long total = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%dh %02dmin %02ds", total / 3600, (total % 3600) / 60, total % 60);
    }
}
