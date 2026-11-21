package com.shirokai.raids.mixin;

import com.shirokai.raids.RaidQuizGuard;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional CRZquiz integration; no quiz code or runtime dependency is bundled. */
@Pseudo
@Mixin(targets="it.carrozza.crzquiz.managers.QuizManager", remap=false)
public abstract class RaidQuizGuardMixin {
    @Inject(method="processAnswer", at=@At("HEAD"), cancellable=true, remap=false)
    private void shirokaiSkipRaidAnswer(ServerPlayer player, String answer, CallbackInfo ci) {
        // Check at execution time too, so an answer queued before entry cannot award a raid participant.
        if (RaidQuizGuard.blocked(player)) ci.cancel();
    }
}
