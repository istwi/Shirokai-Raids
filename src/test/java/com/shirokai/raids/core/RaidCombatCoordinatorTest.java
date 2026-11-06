package com.shirokai.raids.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidCombatCoordinatorTest {
    private RaidCombatCoordinator raid(long hp, int capacity) {
        return new RaidCombatCoordinator(UUID.randomUUID(), hp, capacity);
    }
    @Test void threeSessionsShareEffectiveDamageAndCloseExactlyOnce() {
        var raid = raid(1000, 100);
        var a = raid.join(UUID.randomUUID()).orElseThrow();
        var b = raid.join(UUID.randomUUID()).orElseThrow();
        assertEquals(100, raid.applyDamage(a, 1, 100).applied());
        var c = raid.join(UUID.randomUUID()).orElseThrow();
        assertEquals(300, raid.applyDamage(b, 1, 300).applied());
        var result = raid.applyDamage(c, 1, 800);
        assertEquals(600, result.applied());
        assertEquals(.6, result.snapshot().contribution(c.player()));
        assertEquals(0, result.snapshot().health());
        assertEquals(Set.of(a,b,c), Set.copyOf(result.sessionsToClose()));
        assertTrue(result.terminalTransition());
        assertEquals(RaidCombatCoordinator.State.DEFEATED, result.snapshot().state());
        assertFalse(raid.applyDamage(a, 2, 100).terminalTransition());
        assertTrue(raid.cancel().sessionsToClose().isEmpty());
        assertTrue(raid.join(UUID.randomUUID()).isEmpty());
    }
    @Test void tokensReplayAndReconnectCannotCreditOldEvents() {
        var raid = raid(1000, 3);
        var a = raid.join(UUID.randomUUID()).orElseThrow();
        raid.applyDamage(a, 1, 10);
        assertEquals(RaidCombatCoordinator.Rejection.REPLAY, raid.applyDamage(a, 1, 10).rejection());
        assertEquals(10, raid.applyDamage(a, 2, 10).applied()); // Same text/damage, different hit.
        assertTrue(raid.leave(a));
        var fresh = raid.join(a.player()).orElseThrow();
        assertEquals(RaidCombatCoordinator.Rejection.UNKNOWN_SESSION, raid.applyDamage(a, 3, 100).rejection());
        assertEquals(RaidCombatCoordinator.Rejection.UNKNOWN_SESSION,
            raid.applyDamage(new RaidCombatCoordinator.Session(fresh.token(), UUID.randomUUID()), 1, 100).rejection());
        assertEquals(10, raid.applyDamage(fresh, 1, 10).applied());
        assertEquals(30, raid.snapshot().damage().get(a.player()));
    }
    @Test void capacityCountsActiveSessionsAndDisconnectPreservesContribution() {
        var raid = raid(1000, 1);
        var a = raid.join(UUID.randomUUID()).orElseThrow();
        assertTrue(raid.join(a.player()).isEmpty());
        assertTrue(raid.join(UUID.randomUUID()).isEmpty());
        raid.applyDamage(a, 1, 20);
        assertTrue(raid.leave(a));
        assertFalse(raid.leave(a));
        assertTrue(raid.join(UUID.randomUUID()).isPresent());
        assertEquals(20, raid.snapshot().damage().get(a.player()));
    }
    @Test void oneHundredConcurrentLedgerCallsConserveHealthWithoutProvingGameCapacity() throws Exception {
        var raid = raid(1000, 100);
        var sessions = new ArrayList<RaidCombatCoordinator.Session>();
        for (int i=0; i<100; i++) sessions.add(raid.join(UUID.randomUUID()).orElseThrow());
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Callable<RaidCombatCoordinator.Result>>();
            for (var session : sessions) jobs.add(() -> raid.applyDamage(session, 1, Long.MAX_VALUE));
            var results = pool.invokeAll(jobs);
            long applied=0, transitions=0, closing=0;
            for (var future : results) {
                var result=future.get(); applied+=result.applied();
                if (result.terminalTransition()) transitions++;
                closing+=result.sessionsToClose().size();
            }
            assertEquals(1000, applied); assertEquals(1, transitions); assertEquals(100, closing);
        }
        assertEquals(1000, raid.snapshot().damage().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void invalidEventsAndZeroDamageNeverCreateContribution() {
        var raid = raid(Long.MAX_VALUE, 1);
        var a = raid.join(UUID.randomUUID()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> raid.applyDamage(a, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> raid.applyDamage(a, 1, -1));
        assertEquals(0, raid.applyDamage(a, 1, 0).applied());
        assertTrue(raid.snapshot().damage().isEmpty());
        assertEquals(0, raid.snapshot().contribution(a.player()));
        assertThrows(UnsupportedOperationException.class, () -> raid.snapshot().damage().put(a.player(), 1L));
        assertEquals(Long.MAX_VALUE, raid.applyDamage(a, 2, Long.MAX_VALUE).applied());
    }
    @Test void cancellationClosesRemainingSessionsWithoutClaimingVictory() {
        var raid = raid(1000, 2);
        var a = raid.join(UUID.randomUUID()).orElseThrow();
        raid.applyDamage(a, 1, 10);
        var result=raid.cancel();
        assertEquals(RaidCombatCoordinator.State.CANCELLED, result.snapshot().state());
        assertEquals(990, result.snapshot().health());
        assertEquals(List.of(a), result.sessionsToClose());
        assertTrue(result.terminalTransition());
        assertFalse(raid.cancel().terminalTransition());
    }
    @Test void hardCapIsOneHundredActualSessionsAndOnlyDepartureOpensAPlace() {
        assertThrows(IllegalArgumentException.class, () -> raid(1000, 101));
        assertThrows(IllegalArgumentException.class, () -> raid(1000, 0));
        var raid = raid(1000, 100);
        var admitted = new ArrayList<RaidCombatCoordinator.Session>();
        for (int i=0; i<100; i++) admitted.add(raid.join(UUID.randomUUID()).orElseThrow());
        assertEquals(100, raid.snapshot().activeSessions());
        assertTrue(raid.join(UUID.randomUUID()).isEmpty());
        assertTrue(raid.leave(admitted.get(50)));
        assertEquals(99, raid.snapshot().activeSessions());
        assertTrue(raid.join(UUID.randomUUID()).isPresent());
        assertEquals(100, raid.snapshot().activeSessions());
        assertTrue(raid.join(UUID.randomUUID()).isEmpty());
    }
}
