package com.shirokai.raids.core;

import java.util.*;

/**
 * In-memory authoritative ledger used by the traditional-battle adapter.
 * Callbacks and disk IO must occur outside this monitor.
 * The adapter must deliver private damage events in order, with a sequence generated
 * by the server (never a client or a hash of the message text).
 */
public final class RaidCombatCoordinator {
    public enum State { ACTIVE, DEFEATED, CANCELLED }
    public enum Rejection { NONE, CLOSED, UNKNOWN_SESSION, REPLAY }
    public record Session(UUID token, UUID player) {}
    public record Snapshot(UUID raid, State state, long health, long maximum, long version,
                           Map<UUID, Long> damage, int activeSessions) {
        public double contribution(UUID player) {
            long total = maximum - health;
            return total == 0 ? 0 : (double) damage.getOrDefault(player, 0L) / total;
        }
    }
    public record Result(Rejection rejection, long applied, Snapshot snapshot,
                         List<Session> sessionsToClose, boolean terminalTransition) {}
    private static final class Entry {
        final Session session;
        long sequence;
        Entry(Session session) { this.session = session; }
    }
    private final UUID raid;
    private final long maximum;
    private final int capacity;
    private final Map<UUID, Entry> sessions = new HashMap<>();
    private final Map<UUID, UUID> players = new HashMap<>();
    private final Map<UUID, Long> damage = new HashMap<>();
    private State state = State.ACTIVE;
    private long health, version;

    public RaidCombatCoordinator(UUID raid, long maximum, int capacity) {
        this.raid = Objects.requireNonNull(raid);
        if (maximum <= 0 || capacity < 1 || capacity > 100) throw new IllegalArgumentException("Limites inválidos");
        this.maximum = this.health = maximum;
        this.capacity = capacity;
    }

    public synchronized Optional<Session> join(UUID player) {
        Objects.requireNonNull(player);
        if (state != State.ACTIVE || sessions.size() >= capacity || players.containsKey(player)) return Optional.empty();
        Session session = new Session(UUID.randomUUID(), player);
        sessions.put(session.token(), new Entry(session));
        players.put(player, session.token());
        version++;
        return Optional.of(session);
    }

    public synchronized boolean leave(Session session) {
        Entry entry = sessions.get(session.token());
        if (entry == null || !entry.session.equals(session)) return false;
        sessions.remove(session.token());
        players.remove(session.player());
        version++;
        return true;
    }

    public synchronized Result applyDamage(Session session, long sequence, long requested) {
        Objects.requireNonNull(session);
        if (sequence < 1 || requested < 0) throw new IllegalArgumentException("Evento inválido");
        if (state != State.ACTIVE) return rejected(Rejection.CLOSED);
        Entry entry = sessions.get(session.token());
        if (entry == null || !entry.session.equals(session)) return rejected(Rejection.UNKNOWN_SESSION);
        if (sequence <= entry.sequence) return rejected(Rejection.REPLAY);
        // Gaps are permitted: non-damage engine events may consume sequence numbers.
        entry.sequence = sequence;
        long applied = Math.min(requested, health);
        health -= applied;
        if (applied > 0) damage.merge(session.player(), applied, Long::sum);
        version++;
        boolean defeated = health == 0;
        List<Session> closing = defeated ? close(State.DEFEATED) : List.of();
        return new Result(Rejection.NONE, applied, snapshot(), closing, defeated);
    }

    public synchronized Result cancel() {
        if (state != State.ACTIVE) return rejected(Rejection.CLOSED);
        List<Session> closing = close(State.CANCELLED);
        version++;
        return new Result(Rejection.NONE, 0, snapshot(), closing, true);
    }

    private List<Session> close(State terminal) {
        state = terminal;
        List<Session> closing = sessions.values().stream().map(e -> e.session).toList();
        sessions.clear();
        players.clear();
        return closing;
    }

    private Result rejected(Rejection rejection) {
        return new Result(rejection, 0, snapshot(), List.of(), false);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(raid, state, health, maximum, version, Map.copyOf(damage), sessions.size());
    }
    public synchronized long current() { return health; }
    public long maximum() { return maximum; }
    public synchronized double fraction() { return (double)health/maximum; }
}
