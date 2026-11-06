package com.shirokai.raids;

import java.util.*;

/** Durable event summaries and lifetime totals; independent of combat sessions and reward delivery. */
final class RaidArchive {
    static final int HISTORY_LIMIT = 20;
    enum Outcome { VICTORY, TIMEOUT, CANCELLED, INTERRUPTED }
    enum Metric { PARTICIPATIONS, DAMAGE, VICTORIES }
    record Totals(String name, long participations, long victories, long damage) {
        Totals {
            if (name == null || name.isBlank() || participations < 0 || victories < 0 || victories > participations || damage < 0)
                throw new IllegalArgumentException("Totais de raid inválidos");
        }
        long value(Metric metric) {
            return switch (metric) { case PARTICIPATIONS -> participations; case DAMAGE -> damage; case VICTORIES -> victories; };
        }
    }
    record History(String raid, String boss, String species, long started, long ended, Outcome outcome,
                   long damage, long maximum, int rank, int participants, Map<String, Integer> drops) {
        History {
            if (raid == null || boss == null || species == null || outcome == null || started < 0 || ended < started
                || maximum < 1 || damage < 0 || damage > maximum || rank < 0 || rank > participants || participants < 1
                || drops == null || drops.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 1))
                throw new IllegalArgumentException("Histórico de raid inválido");
            drops = Map.copyOf(drops);
        }
        double contribution() { return 100.0 * damage / maximum; }
    }
    record Ranked(String player, Totals totals, int rank) {}
    static final class Event {
        String id, boss, species;
        long started, maximum;
        Map<String, String> participants = new HashMap<>();
        Map<String, Long> damage = new HashMap<>();
        Event(String id, String boss, String species, long started, long maximum) {
            this.id = id; this.boss = boss; this.species = species; this.started = started; this.maximum = maximum;
        }
    }
    Event current;
    Map<String, List<History>> history = new HashMap<>();
    Map<String, Totals> totals = new HashMap<>();

    void complete(Outcome outcome, long now, Map<String, Map<String, Integer>> drops) {
        if (current == null) return; // Clearing current and updating totals share the same atomic state write.
        var event = current;
        var contributors = event.participants.keySet().stream().filter(id -> event.damage.getOrDefault(id, 0L) > 0)
            .sorted(Comparator.<String>comparingLong(id -> event.damage.getOrDefault(id, 0L)).reversed().thenComparing(id -> id)).toList();
        for (var participant : event.participants.entrySet()) {
            String id = participant.getKey();
            long damage = event.damage.getOrDefault(id, 0L);
            var previous = totals.getOrDefault(id, new Totals(participant.getValue(), 0, 0, 0));
            totals.put(id, new Totals(participant.getValue(), Math.addExact(previous.participations(), 1),
                Math.addExact(previous.victories(), outcome == Outcome.VICTORY ? 1 : 0), Math.addExact(previous.damage(), damage)));
            int rank = damage == 0 ? 0 : contributors.indexOf(id) + 1;
            var entry = new History(event.id, event.boss, event.species, event.started, Math.max(event.started, now), outcome,
                damage, event.maximum, rank, event.participants.size(), drops.getOrDefault(id, Map.of()));
            var list = history.computeIfAbsent(id, ignored -> new ArrayList<>());
            list.addFirst(entry);
            if (list.size() > HISTORY_LIMIT) list.subList(HISTORY_LIMIT, list.size()).clear();
        }
        current = null;
    }
    List<History> recent(String player) { return List.copyOf(history.getOrDefault(player, List.of())); }
    List<Ranked> ranking(Metric metric) {
        var sorted = totals.entrySet().stream().sorted(Comparator.<Map.Entry<String, Totals>>comparingLong(e -> e.getValue().value(metric))
            .reversed().thenComparing(Map.Entry::getKey)).toList();
        var result = new ArrayList<Ranked>();
        for (int i = 0; i < sorted.size(); i++) result.add(new Ranked(sorted.get(i).getKey(), sorted.get(i).getValue(), i + 1));
        return List.copyOf(result);
    }
    void validate() {
        if (history == null || totals == null) throw new IllegalArgumentException("Arquivo de histórico inválido");
        for (var entry : history.entrySet()) {
            UUID.fromString(entry.getKey());
            if (entry.getValue() == null || entry.getValue().size() > HISTORY_LIMIT || entry.getValue().stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("Lista de histórico inválida");
            var ids = new HashSet<String>();
            for (var raid : entry.getValue()) if (!ids.add(raid.raid())) throw new IllegalArgumentException("Histórico duplicado");
        }
        for (var entry : totals.entrySet()) {
            UUID.fromString(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("Totais ausentes");
        }
        if (current != null) {
            if (current.id == null || current.boss == null || current.species == null || current.started < 0
                || current.maximum < 1 || current.maximum > 1_000_000_000_000L || current.participants == null || current.damage == null)
                throw new IllegalArgumentException("Evento de histórico inválido");
            long damage = 0;
            for (var entry : current.participants.entrySet()) {
                UUID.fromString(entry.getKey());
                if (entry.getValue() == null || entry.getValue().isBlank()) throw new IllegalArgumentException("Participante inválido");
            }
            for (var entry : current.damage.entrySet()) {
                if (!current.participants.containsKey(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0)
                    throw new IllegalArgumentException("Dano de histórico inválido");
                damage = Math.addExact(damage, entry.getValue());
            }
            if (damage > current.maximum) throw new IllegalArgumentException("Dano acima do HP do evento");
        }
    }
}
