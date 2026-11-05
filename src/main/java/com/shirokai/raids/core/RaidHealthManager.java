package com.shirokai.raids.core;

/** Server thread owns this state; synchronized also protects independent test callers. */
public final class RaidHealthManager {
    private final long maximum;
    private long current;
    public RaidHealthManager(long maximum) {
        if (maximum <= 0) throw new IllegalArgumentException("HP deve ser positivo");
        this.maximum = this.current = maximum;
    }
    public synchronized long damage(long requested) {
        if (requested <= 0) return 0;
        long applied = Math.min(requested, current);
        current -= applied;
        return applied;
    }
    public synchronized long current() { return current; }
    public long maximum() { return maximum; }
    public synchronized double fraction() { return (double) current / maximum; }
}
