package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidArchiveTest {
    @TempDir Path directory;
    private final String alice = UUID.randomUUID().toString(), bob = UUID.randomUUID().toString();
    private RaidArchive.Event event(String id) {
        var event = new RaidArchive.Event(id, "Rayquaza", "rayquaza", 1000, 1000);
        event.participants.put(alice, "Alice"); event.participants.put(bob, "Bob");
        return event;
    }
    @Test void victoryRecordsPersonalDamageRankDropsAndUniqueParticipants() {
        var archive = new RaidArchive(); archive.current = event("raid");
        archive.current.participants.put(alice, "Alice"); // Rejoin, same UUID.
        archive.current.damage.put(alice, 750L); archive.current.damage.put(bob, 250L);
        var drops = new HashMap<String, Integer>(); drops.put("minecraft:apple", 4);
        archive.complete(RaidArchive.Outcome.VICTORY, 2000, Map.of(alice, drops));
        drops.clear();
        archive.complete(RaidArchive.Outcome.VICTORY, 3000, Map.of());
        assertEquals(1, archive.totals.get(alice).participations());
        assertEquals(1, archive.totals.get(alice).victories());
        assertEquals(750, archive.totals.get(alice).damage());
        var entry = archive.recent(alice).getFirst();
        assertEquals(75.0, entry.contribution()); assertEquals(1, entry.rank());
        assertEquals(2, entry.participants()); assertEquals(4, entry.drops().get("minecraft:apple"));
        assertEquals(2, archive.recent(bob).getFirst().rank());
        assertTrue(archive.recent(bob).getFirst().drops().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> archive.recent(alice).clear());
        archive.validate();
    }
    @Test void historyIsBoundedWithoutResettingLifetimeTotalsOrRenamedPlayers() {
        var archive = new RaidArchive();
        for (int i = 0; i < 25; i++) {
            archive.current = event("raid-" + i); archive.current.participants.put(alice, "AliceNova");
            archive.current.damage.put(alice, 10L);
            archive.complete(RaidArchive.Outcome.TIMEOUT, 2000 + i, Map.of());
        }
        assertEquals(20, archive.recent(alice).size());
        assertEquals("raid-24", archive.recent(alice).getFirst().raid());
        assertEquals("raid-5", archive.recent(alice).getLast().raid());
        assertEquals(25, archive.totals.get(alice).participations());
        assertEquals(250, archive.totals.get(alice).damage());
        assertEquals("AliceNova", archive.totals.get(alice).name());
        assertEquals(0, archive.totals.get(alice).victories()); archive.validate();
    }
    @Test void publicRankingUsesEachMetricAndDeterministicTieBreaks() {
        var archive = new RaidArchive();
        archive.totals.put(alice, new RaidArchive.Totals("Alice", 10, 1, 100));
        archive.totals.put(bob, new RaidArchive.Totals("Bob", 2, 2, 200));
        assertEquals(alice, archive.ranking(RaidArchive.Metric.PARTICIPATIONS).getFirst().player());
        assertEquals(bob, archive.ranking(RaidArchive.Metric.DAMAGE).getFirst().player());
        assertEquals(bob, archive.ranking(RaidArchive.Metric.VICTORIES).getFirst().player());
        archive.totals.put(alice, new RaidArchive.Totals("Alice", 2, 2, 200));
        assertEquals(List.of(alice, bob).stream().sorted().toList(), archive.ranking(RaidArchive.Metric.DAMAGE).stream().map(RaidArchive.Ranked::player).toList());
        assertEquals(List.of(1, 2), archive.ranking(RaidArchive.Metric.DAMAGE).stream().map(RaidArchive.Ranked::rank).toList());
    }
    @Test void restartCountsTheLastSnapshotOnceAndNeverInventsDrops() throws Exception {
        var state = new RaidState(); state.archive.current = event("interrupted");
        state.archive.current.damage.put(alice, 100L);
        Path path = directory.resolve("state.json"); JsonFiles.write(path, state);
        var restored = JsonFiles.read(path, RaidState.class); restored.validate();
        restored.archive.complete(RaidArchive.Outcome.INTERRUPTED, 2000, Map.of());
        JsonFiles.write(path, restored);
        var again = JsonFiles.read(path, RaidState.class); again.validate();
        again.archive.complete(RaidArchive.Outcome.INTERRUPTED, 3000, Map.of());
        assertEquals(1, again.archive.totals.get(alice).participations());
        assertEquals(100, again.archive.totals.get(alice).damage());
        assertEquals(0, again.archive.totals.get(alice).victories());
        assertEquals(RaidArchive.Outcome.INTERRUPTED, again.archive.recent(alice).getFirst().outcome());
        assertTrue(again.archive.recent(alice).getFirst().drops().isEmpty());
        assertTrue(again.pending.isEmpty());
        assertEquals(0, again.archive.recent(bob).getFirst().rank());
    }
    @Test void oldStateDoesNotFabricateHistoryFromTheLastDamageSnapshot() throws Exception {
        Path path = directory.resolve("old.json");
        Files.writeString(path, "{\"damage\":{\"old-player\":10},\"completed\":[\"old-raid\"]}");
        var restored = JsonFiles.read(path, RaidState.class); restored.validate();
        assertTrue(restored.archive.history.isEmpty()); assertTrue(restored.archive.totals.isEmpty());
    }
    @Test void invalidArchivesAndInvalidMenuTimeZonesAreRejected() {
        var archive = new RaidArchive(); archive.current = event("raid");
        archive.current.damage.put("unknown", 10L);
        assertThrows(IllegalArgumentException.class, archive::validate);
        archive.current.damage.clear(); archive.current.damage.put(alice, 1001L);
        assertThrows(IllegalArgumentException.class, archive::validate);
        assertThrows(IllegalArgumentException.class, () -> new RaidArchive.Totals("Alice", 1, 2, 0));
        var config = new RaidConfig(); config.menuTimeZone = "invalid/zone";
        assertThrows(java.time.DateTimeException.class, config::validate);
    }
}
