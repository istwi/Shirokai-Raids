package com.shirokai.raids;

import com.shirokai.raids.core.RaidSchedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationsTest {
    @TempDir Path directory;
    @Test void warningAfterPauseOnlyEmitsNearestThresholdAndDoesNotRepeat() {
        var warned = new HashSet<Integer>(); var thresholds = List.of(900,300,60);
        assertEquals(60, RaidSchedule.warning(30_000, thresholds, warned).orElseThrow());
        assertEquals(Set.of(900,300,60), warned);
        assertTrue(RaidSchedule.warning(29_000, thresholds, warned).isEmpty());
        assertTrue(RaidSchedule.warning(0, thresholds, new HashSet<>()).isEmpty());
    }
    @Test void ordinaryWarningsFollowTheConfiguredThresholds() {
        var warned = new HashSet<Integer>(); var thresholds = List.of(60,900,300);
        assertTrue(RaidSchedule.warning(901_000, thresholds, warned).isEmpty());
        assertEquals(900, RaidSchedule.warning(900_000, thresholds, warned).orElseThrow());
        assertTrue(RaidSchedule.warning(899_000, thresholds, warned).isEmpty());
        assertEquals(300, RaidSchedule.warning(299_000, thresholds, warned).orElseThrow());
        assertEquals(60, RaidSchedule.warning(59_000, thresholds, warned).orElseThrow());
    }
    @Test void shorteningSchedulePreservesLongWarningsAsInactiveInsteadOfRejectingTheEdit() {
        var config = new RaidConfig(); config.intervalSeconds = 120; config.validate();
        assertEquals(List.of(900,300,60), config.warningsSeconds);
        var effective = config.warningsSeconds.stream().filter(w -> w <= config.intervalSeconds).toList();
        assertTrue(RaidSchedule.warning(120_000,effective,new HashSet<>()).isEmpty());
        assertEquals(60,RaidSchedule.warning(59_000,effective,new HashSet<>()).orElseThrow());
    }
    @Test void editingConfigurationUsesIndependentCopyAndRejectsInvalidEntry() {
        var original = new RaidConfig(); original.intervalSeconds = 10_000;
        var next = JsonFiles.copy(original, RaidConfig.class);
        next.entry.configured = true; next.entry.dimension = "missing dimension";
        assertThrows(IllegalArgumentException.class, next::validate);
        assertFalse(original.entry.configured); assertEquals(10_000, original.intervalSeconds);
        next.entry.dimension = "minecraft:the_nether"; next.entry.pitch = 91;
        assertThrows(IllegalArgumentException.class, next::validate);
        next.entry.pitch = 30; next.entry.x = Double.NaN;
        assertThrows(IllegalArgumentException.class, next::validate);
        next.entry.x = 0; next.validate();
    }
    private String legacy(RaidState state, String owner) {
        String id = owner + ":" + UUID.randomUUID() + ":" + UUID.randomUUID();
        state.uncertain.put(id, new RaidState.Pending("raid", "minecraft:diamond", 3)); return id;
    }
    @Test void refundIsAuditedPersistentAndCannotBeReplayed() throws Exception {
        var state = new RaidState(); String owner = UUID.randomUUID().toString(); String id = legacy(state, owner);
        LegacyReservationReview.resolve(state, id, LegacyReservationReview.Action.REFUND, "console", "Inventário conferido com backup", 123);
        assertEquals(3, state.pending.get(owner).getFirst().count); assertTrue(state.uncertain.isEmpty());
        Path file = directory.resolve("state.json"); JsonFiles.write(file, state);
        var restored = JsonFiles.read(file, RaidState.class); restored.validate();
        assertEquals("Inventário conferido com backup", restored.reviews.get(id).reason());
        assertThrows(IllegalArgumentException.class, () -> LegacyReservationReview.resolve(restored, id,
            LegacyReservationReview.Action.REFUND, "console", "repetição", 124));
        assertEquals(3, restored.pending.get(owner).getFirst().count);
    }
    @Test void confirmingAlreadyReceivedReservationDoesNotCreateNewItems() {
        var state = new RaidState(); String id = legacy(state, UUID.randomUUID().toString());
        LegacyReservationReview.resolve(state, id, LegacyReservationReview.Action.CONFIRM, "console", "Recebimento comprovado", 123);
        assertTrue(state.pending.isEmpty()); assertTrue(state.uncertain.isEmpty());
        assertEquals(LegacyReservationReview.Action.CONFIRM, state.reviews.get(id).action()); state.validate();
    }
    @Test void unknownOwnerAndMissingReasonCannotAccidentallyReissueItems() {
        var state = new RaidState(); state.uncertain.put("unknown", new RaidState.Pending("raid", "minecraft:apple", 2));
        assertThrows(IllegalArgumentException.class, () -> LegacyReservationReview.resolve(state,"unknown",
            LegacyReservationReview.Action.REFUND,"console","checked",1));
        assertTrue(state.pending.isEmpty()); assertTrue(state.uncertain.containsKey("unknown"));
        String id = legacy(state, UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class, () -> LegacyReservationReview.resolve(state,id,
            LegacyReservationReview.Action.REFUND,"console","",1));
        assertTrue(state.uncertain.containsKey(id)); assertTrue(state.pending.isEmpty());
    }
}
