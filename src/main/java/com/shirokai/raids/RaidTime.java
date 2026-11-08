package com.shirokai.raids;

import java.util.Locale;

final class RaidTime {
    private RaidTime() {}
    static String minutes(long seconds) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f min", Math.max(0, seconds) / 60.0);
    }
}
