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
    // New reservations can be reconciled using receipts stored with the player's inventory.
    Map<String, Delivery> deliveries = new HashMap<>();
    RaidArchive archive = new RaidArchive();
    static final class Delivery {
        String player;
        Pending reward;
        Delivery(String player, Pending reward) { this.player = player; this.reward = reward; }
    }
    static final class Pending {
        String id, raid, item;
        int count;
        Pending(String raid, String item, int count) {
            this.id = UUID.randomUUID().toString(); this.raid = raid; this.item = item; this.count = count;
        }
    }
    void validate() {
        if (archive == null) throw new IllegalArgumentException("Histórico persistente inválido");
        archive.validate();
        if (nextRaid < 0 || pending == null || completed == null || uncertain == null || damage == null || deliveries == null)
            throw new IllegalArgumentException("Estado persistente inválido; restaure um backup");
        var ids = new HashSet<String>();
        for (var list : pending.values()) {
            if (list == null) throw new IllegalArgumentException("Lista de recompensas inválida");
            for (var entry : list) {
                validate(entry);
                if (!ids.add(entry.id)) throw new IllegalArgumentException("Recompensa duplicada no estado");
            }
        }
        for (var entry : uncertain.values()) validate(entry);
        for (var entry : deliveries.entrySet()) {
            var delivery = entry.getValue();
            if (delivery == null || delivery.player == null) throw new IllegalArgumentException("Reserva inválida");
            UUID.fromString(delivery.player);
            validate(delivery.reward);
            UUID.fromString(entry.getKey());
            if (!entry.getKey().equals(delivery.reward.id) || !ids.add(entry.getKey()))
                throw new IllegalArgumentException("Reserva duplicada ou inválida");
        }
    }
    private static void validate(Pending entry) {
        if (entry == null || entry.id == null || entry.id.isBlank() || entry.raid == null || entry.item == null
            || entry.count < 1 || entry.count > 64)
            throw new IllegalArgumentException("Recompensa persistente inválida");
    }
}
