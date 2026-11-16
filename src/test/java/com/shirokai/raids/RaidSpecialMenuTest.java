package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaidSpecialMenuTest {
    @Test void countdownIncludesSpecialAndRestartsEachCycle(){
        for(long starts=0;starts<1000;starts++){
            var progress=new RaidSpecialMenu.Progress(starts,true,true);
            assertEquals(20-starts%20,progress.remaining());
            assertEquals(RaidAutomaticCatalog.specialDue(starts),progress.remaining()==1);
        }
        assertTrue(new RaidSpecialMenu.Progress(19,true,true).lines().contains("A próxima raid automática será especial!"));
        assertEquals(20,new RaidSpecialMenu.Progress(20,true,true).remaining());
    }
    @Test void disabledModesAreExplicitAndAdminCommandIsAllowedDuringRaid(){
        var lines=new RaidSpecialMenu.Progress(19,false,false).lines();
        assertTrue(lines.stream().anyMatch(s->s.contains("ciclo especial não será usado")));
        assertTrue(lines.stream().anyMatch(s->s.contains("contador pausado")));
        assertTrue(RaidAdminRewardMenu.allowedCommand("raidadmin especial"));
        assertFalse(RaidAdminRewardMenu.allowedCommand("raidadmin especial reset"));
        assertFalse(RaidAdminRewardMenu.allowedCommand("raidadmin iniciar rayquaza"));
    }
}
