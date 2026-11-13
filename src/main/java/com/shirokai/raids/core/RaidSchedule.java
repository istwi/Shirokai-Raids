package com.shirokai.raids.core;

/** Wall-clock scheduling: missed occurrences never accumulate. */
public final class RaidSchedule {
    private RaidSchedule() {}
    public static long next(long now, long intervalSeconds) {
        if (intervalSeconds < 60 || intervalSeconds > 31_536_000) throw new IllegalArgumentException("Intervalo inválido");
        return Math.addExact(now, Math.multiplyExact(intervalSeconds, 1000));
    }
    /** Remaining fraction of one interval, safe when a reload shortens the interval. */
    public static float countdownProgress(long remainingMillis, long intervalSeconds) {
        if (intervalSeconds <= 0) throw new IllegalArgumentException("Intervalo inválido");
        return (float) Math.clamp(remainingMillis / (intervalSeconds * 1000.0), 0.0, 1.0);
    }
    public static boolean due(long now, long scheduled, boolean active) { return !active && now >= scheduled; }
    /** Pick only the nearest relevant threshold after a pause/restart, rather than a burst of stale warnings. */
    public static java.util.OptionalInt warning(long remainingMillis, java.util.List<Integer> thresholds, java.util.Set<Integer> warned) {
        if (remainingMillis <= 0) return java.util.OptionalInt.empty();
        var next = thresholds.stream().filter(t -> remainingMillis <= t * 1000L && !warned.contains(t)).mapToInt(Integer::intValue).min();
        if (next.isPresent()) thresholds.stream().filter(t -> t >= next.getAsInt()).forEach(warned::add);
        return next;
    }
}
