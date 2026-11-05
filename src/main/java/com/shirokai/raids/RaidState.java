package com.shirokai.raids;

import java.util.*;

final class RaidState {
    long nextRaid;
    String activeRaid, bossEntity;
    long hp;
    Map<String, Long> damage = new HashMap<>();
    Map<String, List<Pending>> pending = new HashMap<>();
    Set<String> completed = new HashSet<>();
    // Reserved deliveries after a crash require operator reconciliation, never automatic replay.
    Map<String, Pending> uncertain = new HashMap<>();
    static final class Pending {
        String id, raid, item;
        int count;
        Pending(String raid, String item, int count) {
            this.id = UUID.randomUUID().toString(); this.raid = raid; this.item = item; this.count = count;
        }
    }
    void validate() {
        if (nextRaid < 0 || pending == null || completed == null || uncertain == null || damage == null)
            throw new IllegalArgumentException("Estado persistente inválido; restaure um backup");
        for (var list : pending.values()) for (var entry : list)
            if (entry.id == null || entry.raid == null || entry.item == null || entry.count < 1 || entry.count > 64)
                throw new IllegalArgumentException("Recompensa persistente inválida");
    }
}
