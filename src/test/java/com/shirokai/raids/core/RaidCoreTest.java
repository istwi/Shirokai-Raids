package com.shirokai.raids.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidCoreTest {
    @Test void sharedHealthClampsOverkillAndRejectsInvalidDamage() {
        var hp = new RaidHealthManager(100);
        assertEquals(0, hp.damage(-1));
        assertEquals(30, hp.damage(30));
        assertEquals(70, hp.damage(100));
        assertEquals(0, hp.damage(1));
        assertEquals(0, hp.current());
    }
    @Test void simultaneousAttacksCannotLoseOrDuplicateDamage() throws Exception {
        var hp = new RaidHealthManager(10000);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new java.util.ArrayList<Future<Long>>();
            for (int i = 0; i < 1000; i++) jobs.add(pool.submit(() -> hp.damage(17)));
            long total = 0;
            for (var job : jobs) total += job.get();
            assertEquals(10000, total);
            assertEquals(0, hp.current());
        }
    }
    @Test void contributionUsesAppliedDamageAndPreservesOfflinePlayers() {
        var tracker = new RaidDamageTracker();
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var p = UUID.randomUUID();
        tracker.record(a, "A", p, 75, 1); tracker.record(b, "B", p, 25, 2);
        tracker.record(b, "B", p, 0, 3);
        assertEquals(a, tracker.ranking().getFirst().player());
        assertEquals(.75, tracker.contribution(a, 100));
        assertEquals(1, tracker.ranking().get(1).attacks());
        assertTrue(tracker.ranking().getFirst().pokemon().contains(p));
    }
    @Test void scheduleDoesNotAccumulateAndPreventsSecondRaid() {
        assertEquals(7_200_000, RaidSchedule.next(0, 7200));
        assertFalse(RaidSchedule.due(10_000_000, 7_200_000, true));
        assertTrue(RaidSchedule.due(10_000_000, 7_200_000, false));
        assertEquals(17_200_000, RaidSchedule.next(10_000_000, 7200));
    }
    @Test void cooldownOwnershipRangeAndBattleAreAuthoritative() {
        assertTrue(CombatRules.eligible(10, 10, 100, 10, true, true, false, false));
        assertFalse(CombatRules.eligible(9, 10, 100, 10, true, true, false, false));
        assertFalse(CombatRules.eligible(10, 10, 101, 10, true, true, false, false));
        assertFalse(CombatRules.eligible(10, 10, 100, 10, false, true, false, false));
        assertFalse(CombatRules.eligible(10, 10, 100, 10, true, true, true, false));
        assertFalse(CombatRules.eligible(10, 10, 100, 10, true, true, false, true));
    }
    @Test void phasesAndDamageHaveExplicitBoundaries() {
        assertEquals(1, CombatRules.phase(.7)); assertEquals(2, CombatRules.phase(.3));
        assertEquals(3, CombatRules.phase(.29));
        assertEquals(0, CombatRules.damage(100,100,100,100,1,0));
        assertEquals(86, CombatRules.damage(100,100,100,100,1,1));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damage(100,1,0,100,1,1));
    }
}
