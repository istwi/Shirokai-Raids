package com.shirokai.raids.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardAndTypesTest {
    @Test void contributionTiersHaveExactBoundaries() {
        assertEquals(0,RaidRewardManager.rolls(.0099,.01));
        assertEquals(1,RaidRewardManager.rolls(.01,.01));
        assertEquals(2,RaidRewardManager.rolls(.05,.01));
        assertEquals(3,RaidRewardManager.rolls(.15,.01));
        assertEquals(4,RaidRewardManager.rolls(.30,.01));
        assertEquals(0,RaidRewardManager.rolls(Double.NaN,.01));
    }
    @Test void weightedRewardsAreDeterministicWithFixedSeedAndNeverOutOfBounds() {
        var a = new Random(42); var b = new Random(42);
        var weights = List.of(1,5,20);
        var seen = new HashSet<Integer>();
        for(int i=0;i<1000;i++) {
            int index=RaidRewardManager.select(weights,a);
            assertEquals(index,RaidRewardManager.select(weights,b));
            assertTrue(index>=0 && index<3); seen.add(index);
        }
        assertEquals(Set.of(0,1,2),seen);
        assertThrows(IllegalArgumentException.class,()->RaidRewardManager.select(List.of(0),a));
    }
    @Test void dualTypesImmunitiesAndResistanceAreApplied() {
        assertEquals(4,TypeChart.effectiveness("ice",List.of("dragon","flying")));
        assertEquals(0,TypeChart.effectiveness("ground",List.of("dragon","flying")));
        assertEquals(.25,TypeChart.effectiveness("fire",List.of("water","dragon")));
        assertEquals(1,TypeChart.effectiveness("electric",List.of("dragon","flying")));
        assertEquals(0,TypeChart.effectiveness("dragon",List.of("fairy")));
    }
}
