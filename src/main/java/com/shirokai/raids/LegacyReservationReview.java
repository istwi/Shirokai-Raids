package com.shirokai.raids;

import java.util.*;

/** Operator reconciliation only. Absence of a legacy receipt is never proof of nondelivery. */
final class LegacyReservationReview {
    enum Action { CONFIRM, REFUND }
    record Review(String reservation, String actor, String player, Action action, String reason, long reviewedAt,
                  String raid, String item, int count) {
        Review {
            if (reservation == null || reservation.isBlank() || actor == null || actor.isBlank() || player == null || action == null
                || reason == null || reason.isBlank() || reason.length() > 500 || reason.chars().anyMatch(Character::isISOControl)
                || reviewedAt < 0 || raid == null || item == null || count < 1 || count > 64)
                throw new IllegalArgumentException("Registro de conferência inválido");
            if (!player.isEmpty()) UUID.fromString(player);
        }
    }
    static void resolve(RaidState state, String id, Action action, String actor, String reason, long now) {
        if (state.reviews.containsKey(id)) throw new IllegalArgumentException("Reserva já conferida; nada foi repetido");
        var reservation = state.uncertain.get(id);
        if (reservation == null) throw new IllegalArgumentException("Reserva antiga não encontrada");
        String player = "";
        int separator = id.indexOf(':');
        if (separator > 0) {
            try { player = UUID.fromString(id.substring(0, separator)).toString(); } catch (IllegalArgumentException ignored) { }
        }
        if (action == Action.REFUND && player.isEmpty())
            throw new IllegalArgumentException("Reserva sem proprietário identificável; confira o arquivo e backups, sem atribuir a outro jogador");
        var review = new Review(id, actor, player, action, reason, now, reservation.raid, reservation.item, reservation.count);
        if (action == Action.REFUND)
            state.pending.computeIfAbsent(player, key -> new ArrayList<>()).add(new RaidState.Pending(reservation.raid, reservation.item, reservation.count));
        state.reviews.put(id, review);
        state.uncertain.remove(id);
    }
}
