package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidCaptureTest {
    @Test void failedCaptureHasFullAnimationAndDistinctResults() {
        var fail=RaidCapture.animation(false);var win=RaidCapture.animation(true);
        assertEquals(3,fail.getNumberOfShakes());assertFalse(fail.isSuccessfulCapture());
        assertEquals(3,win.getNumberOfShakes());assertTrue(win.isSuccessfulCapture());
        assertNotEquals(RaidMessages.msg(RaidCapture.Outcome.CAPTURED.message,"boss","Mew"),RaidMessages.msg(RaidCapture.Outcome.NO_BALLS.message,"boss","Mew"));
        assertTrue(RaidMessages.msg(RaidCapture.Outcome.NO_BALLS.message,"boss","Mew").contains("acabaram"));
        assertTrue(RaidMessages.msg(RaidCapture.Outcome.TIMEOUT.message,"boss","Mew").contains("2 minutos"));
    }
    @Test void entryPointIsOptionalAndItsCoordinatesAreValidated() {
        var legacy=new com.google.gson.Gson().fromJson("{}",RaidConfig.class);legacy.validate();assertFalse(legacy.entry.configured);
        var config=new RaidConfig();config.entry.configured=true;config.entry.x=120.25;config.entry.yaw=73;config.entry.pitch=11;
        var copy=JsonFiles.copy(config,RaidConfig.class);copy.validate();assertEquals(120.25,copy.entry.x);
        copy.entry.x=Double.NaN;assertThrows(IllegalArgumentException.class,copy::validate);
        copy.entry.x=0;copy.entry.pitch=91;assertThrows(IllegalArgumentException.class,copy::validate);
    }
    @Test void automaticReturnUsesTheVictoryDeadlineAndOnlyTheArena() {
        var exit=new RaidState.ArenaExit();exit.deadline=1000+600_000L;exit.dimension="minecraft:overworld";
        exit.x=0;exit.y=200;exit.z=0;exit.radius=48;
        assertFalse(exit.due(600999,"minecraft:overworld",0,200,0));
        assertTrue(exit.due(601000,"minecraft:overworld",0,200,0));
        assertFalse(exit.due(601000,"minecraft:the_nether",0,200,0));
        assertFalse(exit.due(601000,"minecraft:overworld",100,200,0));
        assertTrue(exit.due(9999999,"minecraft:overworld",0,200,0));
        var state=new RaidState();state.arenaExits.put(UUID.randomUUID().toString(),exit);
        var restored=JsonFiles.copy(state,RaidState.class);restored.validate();
        assertEquals(601000,restored.arenaExits.values().iterator().next().deadline);
        assertTrue(restored.arenaExits.values().iterator().next().due(700000,"minecraft:overworld",0,200,0));
    }
    @Test void rankBudgetsAndConfigBounds() {
        var config = new RaidConfig();
        config.validate();
        assertEquals(5,config.capture.ballsForRank(1));
        assertEquals(3,config.capture.ballsForRank(2));
        assertEquals(3,config.capture.ballsForRank(3));
        assertEquals(1,config.capture.ballsForRank(4));
        assertEquals(1,config.capture.ballsForRank(100));
        assertThrows(IllegalArgumentException.class,()->config.capture.ballsForRank(0));
        config.capture.chancePerThrow=Double.NaN;
        assertThrows(IllegalArgumentException.class,config::validate);
        config.capture.chancePerThrow=1.01;
        assertThrows(IllegalArgumentException.class,config::validate);
        config.capture.chancePerThrow=.02; config.capture.participantBalls=65;
        assertThrows(IllegalArgumentException.class,config::validate);
    }
    @Test void captureRightsSurviveSerializationButDuplicatesAndInvalidBudgetsAreRejected() {
        var state = new RaidState();
        var grant = new RaidState.CaptureGrant();
        grant.id=UUID.randomUUID().toString(); grant.raid=UUID.randomUUID().toString(); grant.player=UUID.randomUUID().toString();
        grant.boss=new RaidConfig.Boss();
        grant.arena=new RaidState.ReturnPosition("minecraft:overworld",0,200,0,0,0);
        grant.radius=48; grant.chance=.02; grant.remaining=5; grant.durationSeconds=120;
        state.captures.put(grant.id,grant);
        var restored=JsonFiles.copy(state,RaidState.class);
        restored.validate();
        assertEquals(5,restored.captures.get(grant.id).remaining);
        grant.remaining=-1;
        assertThrows(IllegalArgumentException.class,state::validate);
        grant.remaining=5;
        var duplicate=JsonFiles.copy(grant,RaidState.CaptureGrant.class); duplicate.id=UUID.randomUUID().toString();
        state.captures.put(duplicate.id,duplicate);
        assertThrows(IllegalArgumentException.class,state::validate);
    }
    @Test void legacyStateLoadsAndRemovedCaptureCommandIsNotAllowed() {
        var legacy=new com.google.gson.Gson().fromJson("{}",RaidState.class);
        legacy.validate(); assertTrue(legacy.captures.isEmpty());
        assertFalse(RaidCommands.allowedDuringRaid("raid capturar"));
        assertFalse(RaidCommands.allowedDuringRaid("raid capturar extra"));
        assertFalse(RaidCommands.allowedDuringRaid("raidadmin iniciar rayquaza"));
    }
}
