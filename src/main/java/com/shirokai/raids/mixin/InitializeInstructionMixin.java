package com.shirokai.raids.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.InitializeInstruction;
import com.shirokai.raids.TraditionalRaidBattles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=InitializeInstruction.class,remap=false)
public abstract class InitializeInstructionMixin {
    @Inject(method="invoke",at=@At("HEAD"),remap=false)
    private void initializeDetachedBoss(PokemonBattle battle,CallbackInfo ci) {
        for(var actor:battle.getActors()) if(TraditionalRaidBattles.isBossActor(actor) || com.shirokai.raids.ShirokaiRaids.isCaptureActor(actor))
            actor.getActivePokemon().getFirst().setBattlePokemon(actor.getPokemonList().getFirst());
    }
}
