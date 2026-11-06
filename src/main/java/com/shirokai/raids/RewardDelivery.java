package com.shirokai.raids;

import java.util.*;

/** Server-thread journal. Inventory and receipt must be saved in the same player file. */
final class RewardDelivery {
    interface Target {
        String player();
        boolean received(String id);
        Transfer plan(String item, int limit);
        void saveAndVerify(String id) throws Exception;
        void forget(String id);
        void prune(Set<String> outstanding);
    }
    interface Transfer {
        int count();
        // Apply all items and the receipt together, or leave both unchanged on an exception.
        void apply(String id);
    }
    private final RaidState state;
    private final Runnable persist;
    private boolean failed;

    RewardDelivery(RaidState state, Runnable persist) {
        this.state = state;
        this.persist = persist;
    }

    int deliver(Target target, int budget) throws Exception {
        if (failed) throw new IllegalStateException("Entregas suspensas; reinicie após conferir a persistência");
        if (budget < 1) throw new IllegalArgumentException("Orçamento de entregas inválido");
        try {
            var outstanding = new HashSet<String>();
            state.deliveries.forEach((id, delivery) -> {
                if (delivery.player.equals(target.player())) outstanding.add(id);
            });
            target.prune(Set.copyOf(outstanding));
            int delivered = 0, used = 0;
            // A loaded receipt proves the items were saved. Never insert that transaction again.
            for (String id : outstanding.stream().sorted().toList()) {
                if (used++ >= budget) return delivered;
                var reservation = state.deliveries.get(id);
                if (!target.received(id)) {
                    var transfer = target.plan(reservation.reward.item, reservation.reward.count);
                    if (transfer == null || transfer.count() != reservation.reward.count) continue;
                    transfer.apply(id);
                }
                acknowledge(target, id);
                delivered += reservation.reward.count;
            }
            var pending = state.pending.getOrDefault(target.player(), List.of());
            for (var reward : List.copyOf(pending)) {
                if (used >= budget) break;
                var transfer = target.plan(reward.item, reward.count);
                if (transfer == null) continue; // Missing item or full inventory: preserve the entry.
                if (transfer.count() < 1 || transfer.count() > reward.count)
                    throw new IllegalStateException("Plano de entrega inválido");
                var reserved = new RaidState.Pending(reward.raid, reward.item, transfer.count());
                state.deliveries.put(reserved.id, new RaidState.Delivery(target.player(), reserved));
                reward.count -= reserved.count;
                if (reward.count == 0) pending.remove(reward);
                persist.run(); // Must precede any inventory mutation.
                transfer.apply(reserved.id);
                acknowledge(target, reserved.id);
                delivered += reserved.count;
                used++;
            }
            return delivered;
        } catch (Exception error) {
            // Never continue with memory that may be ahead of either durable store.
            failed = true;
            throw error;
        }
    }

    private void acknowledge(Target target, String id) throws Exception {
        target.saveAndVerify(id); // A void vanilla save can swallow IO errors; require read-back.
        state.deliveries.remove(id);
        persist.run();
        target.forget(id); // Orphan receipts after a crash are pruned on the next attempt.
    }
}
