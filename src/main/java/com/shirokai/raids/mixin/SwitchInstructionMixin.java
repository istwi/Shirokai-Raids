package com.shirokai.raids.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.SwitchInstruction;
import com.shirokai.raids.TraditionalRaidBattles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SwitchInstruction.class,remap=false)
public abstract class SwitchInstructionMixin {
    @Inject(method="invoke",at=@At("HEAD"),cancellable=true,remap=false)
    private void keepSinglePublicBoss(PokemonBattle battle,CallbackInfo ci) {
        var instruction=(SwitchInstruction)(Object)this;
        if(TraditionalRaidBattles.isBossActor(instruction.getBattleActor())) ci.cancel();
    }
}
