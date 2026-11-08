package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path dir;
    @Test void commandLockAllowsOnlyExitAndRaidMenu() {
        for (String command : List.of("raid", "raid sair", "raid menu", "  raid sair  "))
            assertTrue(RaidCommands.allowedDuringRaid(command), command);
        for (String command : List.of("spawn", "home", "tp 0 64 0", "raid entrar", "raid lobby", "raid ranking",
                "raidadmin parar", "execute run raid sair", "minecraft:tp 0 64 0", "raid sair extra", "say teste", "msg player teste"))
            assertFalse(RaidCommands.allowedDuringRaid(command), command);
    }
    @Test void returnPositionSurvivesRestartWithDimensionAndOrientation() throws Exception {
        var state = new RaidState();
        String player = UUID.randomUUID().toString();
        state.returns.put(player, new RaidState.ReturnPosition("minecraft:the_nether", 12.25, 65, -42.5, 90, -20));
        var path = dir.resolve("return-state.json");
        JsonFiles.write(path, state);
        var restored = JsonFiles.read(path, RaidState.class);
        restored.validate();
        var origin = restored.returns.get(player);
        assertEquals("minecraft:the_nether", origin.dimension);
        assertEquals(12.25, origin.x); assertEquals(65, origin.y); assertEquals(-42.5, origin.z);
        assertEquals(90, origin.yaw); assertEquals(-20, origin.pitch);
    }
    @Test void rejectsInvalidReturnCoordinates() {
        var state = new RaidState();
        state.returns.put(UUID.randomUUID().toString(), new RaidState.ReturnPosition("minecraft:overworld", Double.NaN, 64, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, state::validate);
    }
    @Test void displaysMinutesWithoutRoundingShortCountdownsToZero() {
        assertEquals("1,00 min", RaidTime.minutes(60));
        assertEquals("1,50 min", RaidTime.minutes(90));
        assertEquals("0,02 min", RaidTime.minutes(1));
        assertEquals("0,00 min", RaidTime.minutes(-1));
    }
    @Test void concurrentCombatConfigurationDefaultsToOneHundredAndRejectsHigherLimits() {
        var config=new RaidConfig();
        assertEquals(100,config.maxConcurrentPlayers);
        config.validate();
        config.maxConcurrentPlayers=101;
        assertThrows(IllegalArgumentException.class,config::validate);
        config.maxConcurrentPlayers=0;
        assertThrows(IllegalArgumentException.class,config::validate);
    }
    @Test void restartKeepsPendingOfflineRewardsAndCompletedRaidLedger() throws Exception {
        var state=new RaidState(); state.nextRaid=12345;
        state.completed.add("raid-1");
        state.pending.put("offline",new ArrayList<>(List.of(new RaidState.Pending("raid-1","minecraft:diamond",3))));
        state.uncertain.put("interrupted",new RaidState.Pending("raid-1","minecraft:diamond",1));
        var path=dir.resolve("data/shirokai-raids/state.json");
        JsonFiles.write(path,state);
        var restored=JsonFiles.read(path,RaidState.class); restored.validate();
        assertEquals(12345,restored.nextRaid);
        assertEquals(3,restored.pending.get("offline").getFirst().count);
        assertTrue(restored.completed.contains("raid-1"));
        assertTrue(restored.uncertain.containsKey("interrupted"));
    }
    @Test void customConfigsAreNotOverwrittenAndCorruptStateIsNotSilentlyReset() throws Exception {
        Path p=dir.resolve("config.json");
        var config=new RaidConfig();config.intervalSeconds=10000;
        JsonFiles.defaultFile(p,config);JsonFiles.defaultFile(p,new RaidConfig());
        assertEquals(10000,JsonFiles.read(p,RaidConfig.class).intervalSeconds);
        Files.writeString(p,"{bad json");
        assertThrows(java.io.IOException.class,()->JsonFiles.read(p,RaidState.class));
        assertEquals("{bad json",Files.readString(p));
    }
    @Test void rejectsDirectoryAsStateAndWorksWithUnicodePaths() throws Exception {
        assertThrows(java.io.IOException.class,()->JsonFiles.read(dir,RaidState.class));
        Path p=dir.resolve("dados com espaço/ação.json");
        JsonFiles.write(p,new RaidState());
        assertNotNull(JsonFiles.read(p,RaidState.class));
    }
    @Test void invalidConfigurationCannotEscapeSafetyLimits() {
        var config=new RaidConfig();config.validate();config.attackCooldownTicks=0;
        assertThrows(IllegalArgumentException.class,config::validate);
        var boss=new RaidConfig.Boss();boss.id="../escape";
        assertThrows(IllegalArgumentException.class,boss::validate);
        var rewards=new RaidConfig.Rewards();rewards.items.getFirst().maximum=0;
        assertThrows(IllegalArgumentException.class,rewards::validate);
    }
    @Test void legacyStateLoadsWithoutInventingNewDeliveries() throws Exception {
        Path p = dir.resolve("legacy.json");
        Files.writeString(p, "{\"nextRaid\":123,\"pending\":{},\"completed\":[\"old-raid\"],\"uncertain\":{},\"damage\":{}}");
        var state = JsonFiles.read(p, RaidState.class); state.validate();
        assertTrue(state.deliveries.isEmpty()); assertTrue(state.completed.contains("old-raid"));
    }
    @Test void malformedOrDuplicateDeliveryJournalIsRejected() {
        var state = new RaidState();
        var reward = new RaidState.Pending("raid", "minecraft:apple", 3);
        state.deliveries.put(reward.id, new RaidState.Delivery(UUID.randomUUID().toString(), reward));
        state.validate();
        state.pending.put("player", new ArrayList<>(List.of(reward)));
        assertThrows(IllegalArgumentException.class, state::validate);
        state.pending.clear(); reward.count = 0;
        assertThrows(IllegalArgumentException.class, state::validate);
        reward.count = 3; state.deliveries.get(reward.id).player = "invalid-player";
        assertThrows(IllegalArgumentException.class, state::validate);
    }
}
