package com.shirokai.raids.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SafeTeleportSearchTest {
    @Test void findsOnlyAnApprovedLocationInsideArenaAndAwayFromCenter() {
        var result=SafeTeleportSearch.find(10,80,20,48,0,p -> p.feetY()==79 && p.x()>15);
        assertTrue(result.isPresent());
        var p=result.orElseThrow();
        assertEquals(79,p.feetY());
        double distance=Math.hypot(p.x()+.5-10,p.z()+.5-20);
        assertTrue(distance>=5 && distance<48);
    }
    @Test void noSafeLocationNeverFallsBackToBossOrInvalidCoordinates() {
        var count=new AtomicInteger();
        assertTrue(SafeTeleportSearch.find(0,80,0,48,0,p -> {count.incrementAndGet();return false;}).isEmpty());
        assertTrue(count.get()>0 && count.get()<=520);
        assertTrue(SafeTeleportSearch.find(Double.NaN,80,0,48,0,p -> true).isEmpty());
    }
    @Test void offsetDistributesSimultaneousArrivalsAndHandlesNegativeHash() {
        var a=SafeTeleportSearch.find(0,80,0,48,0,p -> true).orElseThrow();
        var b=SafeTeleportSearch.find(0,80,0,48,Integer.MIN_VALUE+1,p -> true).orElseThrow();
        assertNotEquals(a,b);
    }
    @Test void existingPlayerOrUnloadedPositionCanBeRejectedAndSearchContinues() {
        var occupied=SafeTeleportSearch.find(0,80,0,48,0,p -> true).orElseThrow();
        var alternative=SafeTeleportSearch.find(0,80,0,48,0,p -> !p.equals(occupied));
        assertTrue(alternative.isPresent());assertNotEquals(occupied,alternative.get());
    }
}
