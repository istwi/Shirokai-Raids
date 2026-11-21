package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaidArenaInteractionTest {
    @Test void facilitiesAllowCaptureOutsideBattleButKeepLegacyAndForeignBattleRestrictions() {
        assertTrue(RaidCapture.facilityAllowed(false,false,false));
        assertTrue(RaidCapture.facilityAllowed(false,true,false));
        for(boolean capture:new boolean[]{false,true}) {
            assertFalse(RaidCapture.facilityAllowed(true,capture,false));
            assertFalse(RaidCapture.facilityAllowed(false,capture,true));
        }
    }
}
