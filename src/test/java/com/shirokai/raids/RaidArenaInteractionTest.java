package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaidArenaInteractionTest {
    @Test void facilitiesRequireNoBattleCaptureOrLegacyRecovery() {
        assertTrue(RaidCapture.facilityAllowed(false,false,false));
        for(int flags=1;flags<8;flags++) {
            assertFalse(RaidCapture.facilityAllowed((flags&1)!=0,(flags&2)!=0,(flags&4)!=0),"flags="+flags);
        }
    }
}
