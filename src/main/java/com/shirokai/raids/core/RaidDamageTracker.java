package com.shirokai.raids.core;

import java.util.*;

public final class RaidDamageTracker {
    public record Entry(UUID player, String name, long damage, long attacks, long lastParticipation, Set<UUID> pokemon) {}
    private final Map<UUID, Entry> entries = new HashMap<>();
    public void record(UUID player, String name, UUID pokemon, long applied, long now) {
        if (applied <= 0) return;
        Entry previous = entries.get(player);
        Set<UUID> used = new HashSet<>(previous == null ? Set.of() : previous.pokemon());
        used.add(pokemon);
        entries.put(player, new Entry(player, name, applied + (previous == null ? 0 : previous.damage()),
                1 + (previous == null ? 0 : previous.attacks()), now, Set.copyOf(used)));
    }
    public List<Entry> ranking() {
        return entries.values().stream().sorted(Comparator.comparingLong(Entry::damage).reversed()
                .thenComparing(e -> e.player().toString())).toList();
    }
    public long damage(UUID player) { return entries.containsKey(player) ? entries.get(player).damage() : 0; }
    public double contribution(UUID player, long maximum) { return maximum > 0 ? (double) damage(player) / maximum : 0; }
}
