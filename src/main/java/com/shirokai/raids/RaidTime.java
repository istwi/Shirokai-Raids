package com.shirokai.raids;

import java.util.Locale;

final class RaidTime {
    private RaidTime() {}
    static String format(long seconds) {
        long total = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%dh %02dmin %02ds", total / 3600, (total % 3600) / 60, total % 60);
    }
}
