package com.shirokai.raids;

import net.minecraft.server.level.ServerPlayer;

/** Optional quiz admission check; does not change raid reward redemption. */
public final class RaidQuizGuard {
    private RaidQuizGuard() {}
    public static boolean blocked(ServerPlayer player) {
        if (!ShirokaiRaids.participating(player)) return false;
        player.sendSystemMessage(RaidMessages.chat(RaidMessages.msg("quiz.bloqueado_na_raid")));
        return true;
    }
}
