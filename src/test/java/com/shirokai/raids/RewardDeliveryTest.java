package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardDeliveryTest {
    @TempDir Path directory;
    private final String owner = UUID.randomUUID().toString();
    private Path ledger() { return directory.resolve("state.json"); }
    private RaidState initial(int count) throws Exception {
        var state = new RaidState();
        state.completed.add("raid");
        state.pending.put(owner, new ArrayList<>(List.of(new RaidState.Pending("raid", "minecraft:apple", count))));
        JsonFiles.write(ledger(), state);
        return state;
    }
    private RewardDelivery service(RaidState state) {
        return new RewardDelivery(state, () -> {
            try { JsonFiles.write(ledger(), state); } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
    private RaidState reload() throws Exception {
        var state = JsonFiles.read(ledger(), RaidState.class); state.validate(); return state;
    }
    private int pending(RaidState state) { return state.pending.getOrDefault(owner, List.of()).stream().mapToInt(p -> p.count).sum(); }

    static class PlayerData {
        int items;
        Set<String> receipts = new HashSet<>();
    }
    enum Cut { BEFORE_RESERVATION, AFTER_RESERVATION, BEFORE_INSERT, AFTER_INSERT, BEFORE_PLAYER_SAVE, AFTER_PLAYER_SAVE, BEFORE_ACK, AFTER_ACK }
    final class Inventory implements RewardDelivery.Target {
        PlayerData data;
        final Path path = directory.resolve("player.json");
        int capacity = 64;
        boolean missingItem;
        Cut cut;
        Inventory() throws Exception { data = new PlayerData(); JsonFiles.write(path, data); }
        void restart() throws Exception { data = JsonFiles.read(path, PlayerData.class); cut = null; }
        public String player() { return owner; }
        public boolean received(String id) { return data.receipts.contains(id); }
        public void prune(Set<String> outstanding) { data.receipts.retainAll(outstanding); }
        public void forget(String id) { data.receipts.remove(id); }
        public RewardDelivery.Transfer plan(String item, int limit) {
            int count = Math.min(limit, capacity - data.items);
            if (missingItem || count <= 0) return null;
            return new RewardDelivery.Transfer() {
                public int count() { return count; }
                public void apply(String id) {
                    if (cut == Cut.BEFORE_INSERT) throw new IllegalStateException("power cut");
                    data.items += count; data.receipts.add(id);
                    if (cut == Cut.AFTER_INSERT) throw new IllegalStateException("power cut");
                }
            };
        }
        public void saveAndVerify(String id) throws Exception {
            assertTrue(received(id));
            if (cut != Cut.BEFORE_PLAYER_SAVE) JsonFiles.write(path, data);
            if (cut == Cut.AFTER_PLAYER_SAVE) throw new IllegalStateException("power cut");
            // Simulate vanilla swallowing an IO error: a normal return is not proof of persistence.
            if (!JsonFiles.read(path, PlayerData.class).receipts.contains(id)) throw new IllegalStateException("receipt missing on disk");
        }
    }

    @Test void onlineDeliveryIsPersistentAndRepeatedAttemptsCannotDuplicate() throws Exception {
        var state = initial(4); var inventory = new Inventory();
        assertEquals(4, service(state).deliver(inventory, 4));
        assertEquals(0, pending(reload())); assertTrue(reload().deliveries.isEmpty());
        inventory.restart();
        assertEquals(0, service(reload()).deliver(inventory, 4));
        assertEquals(4, inventory.data.items);
        assertTrue(inventory.data.receipts.isEmpty(), "orphan receipt is pruned after durable acknowledgement");
    }
    @Test void fullInventoryAndMissingItemsRemainPendingAcrossRestart() throws Exception {
        var state = initial(4); var inventory = new Inventory(); inventory.capacity = 0;
        assertEquals(0, service(state).deliver(inventory, 4));
        assertEquals(4, pending(reload())); assertTrue(state.deliveries.isEmpty());
        inventory.capacity = 64; inventory.missingItem = true;
        assertEquals(0, service(state).deliver(inventory, 4));
        assertEquals(4, pending(reload()));
        inventory.missingItem = false;
        assertEquals(4, service(state).deliver(inventory, 4));
    }
    @Test void partialDeliveryPreservesExactRemainderAndRetryDoesNotRollAgain() throws Exception {
        var state = initial(20); var inventory = new Inventory(); inventory.capacity = 7;
        assertEquals(7, service(state).deliver(inventory, 4));
        assertEquals(13, pending(reload()));
        inventory.restart(); inventory.capacity = 64;
        assertEquals(13, service(reload()).deliver(inventory, 4));
        assertEquals(20, inventory.data.items);
        assertTrue(reload().completed.contains("raid"));
        assertEquals(0, service(reload()).deliver(inventory, 4));
    }
    @Test void offlineContributorsAndOtherPlayersAreNotTouched() throws Exception {
        var state = initial(4); var inventory = new Inventory();
        String offline = UUID.randomUUID().toString();
        state.pending.put(offline, new ArrayList<>(List.of(new RaidState.Pending("raid", "minecraft:apple", 3))));
        JsonFiles.write(ledger(), state);
        assertEquals(4, service(state).deliver(inventory, 4));
        assertEquals(3, reload().pending.get(offline).getFirst().count);
        assertEquals(4, inventory.data.items);
    }
    @ParameterizedTest @EnumSource(Cut.class)
    void restartAtEveryTransactionBoundaryConservesItems(Cut cut) throws Exception {
        var state = initial(4); var inventory = new Inventory(); inventory.cut = cut;
        int[] writes = {0};
        var delivery = new RewardDelivery(state, () -> {
            int write = ++writes[0];
            if (write == 1 && cut == Cut.BEFORE_RESERVATION || write == 2 && cut == Cut.BEFORE_ACK)
                throw new IllegalStateException("power cut before ledger write");
            try { JsonFiles.write(ledger(), state); } catch (Exception e) { throw new IllegalStateException(e); }
            if (write == 1 && cut == Cut.AFTER_RESERVATION || write == 2 && cut == Cut.AFTER_ACK)
                throw new IllegalStateException("power cut after ledger write");
        });
        assertThrows(Exception.class, () -> delivery.deliver(inventory, 4));
        assertThrows(IllegalStateException.class, () -> delivery.deliver(inventory, 4), "failed service must not keep mutating memory");
        inventory.restart();
        var recovered = reload();
        service(recovered).deliver(inventory, 4);
        inventory.restart();
        assertEquals(4, inventory.data.items, cut.toString());
        assertEquals(0, pending(reload())); assertTrue(reload().deliveries.isEmpty());
        assertEquals(0, service(reload()).deliver(inventory, 4));
        assertEquals(4, inventory.data.items);
    }
    @Test void persistedReceiptPreventsReplayEvenIfItemsWereConsumedBeforeRestart() throws Exception {
        var state = initial(4); var inventory = new Inventory(); inventory.cut = Cut.AFTER_PLAYER_SAVE;
        assertThrows(Exception.class, () -> service(state).deliver(inventory, 4));
        inventory.data.items = 0; JsonFiles.write(inventory.path, inventory.data); // Ordinary later player save.
        inventory.restart();
        assertEquals(4, service(reload()).deliver(inventory, 4)); // Acknowledges, does not insert.
        assertEquals(0, inventory.data.items);
        assertTrue(reload().deliveries.isEmpty());
    }
    @Test void recoveredReservationWaitsForEnoughSpaceWithoutLosingOrSplittingIt() throws Exception {
        var state = initial(4); var inventory = new Inventory(); inventory.cut = Cut.BEFORE_INSERT;
        assertThrows(Exception.class, () -> service(state).deliver(inventory, 4));
        inventory.restart(); inventory.capacity = 2;
        assertEquals(0, service(reload()).deliver(inventory, 4));
        assertEquals(1, reload().deliveries.size()); assertEquals(0, inventory.data.items);
        inventory.capacity = 64;
        assertEquals(4, service(reload()).deliver(inventory, 4));
    }
    @Test void legacyUncertainReservationsAreNeverReplayed() throws Exception {
        var state = initial(4); var inventory = new Inventory();
        state.uncertain.put("old-ambiguous", new RaidState.Pending("old-raid", "minecraft:diamond", 3));
        JsonFiles.write(ledger(), state);
        assertEquals(4, service(state).deliver(inventory, 4));
        assertEquals(3, reload().uncertain.get("old-ambiguous").count);
        assertEquals(4, inventory.data.items);
    }
    @Test void workPerPassIsBoundedAndRemainingEntriesAreRetried() throws Exception {
        var state = initial(4); var inventory = new Inventory();
        for (int i = 0; i < 5; i++) state.pending.get(owner).add(new RaidState.Pending("raid", "minecraft:apple", 1));
        JsonFiles.write(ledger(), state);
        assertEquals(5, service(state).deliver(inventory, 2));
        assertEquals(4, pending(reload()));
        assertEquals(4, service(state).deliver(inventory, 4));
    }
}
