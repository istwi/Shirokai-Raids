package com.shirokai.raids.core;

/** Wall-clock scheduling: missed occurrences never accumulate. */
public final class RaidSchedule {
    private RaidSchedule() {}
    public static long next(long now, long intervalSeconds) {
        if (intervalSeconds < 60 || intervalSeconds > 31_536_000) throw new IllegalArgumentException("Intervalo inválido");
        return Math.addExact(now, Math.multiplyExact(intervalSeconds, 1000));
    }
    public static boolean due(long now, long scheduled, boolean active) { return !active && now >= scheduled; }
}
