package com.shirokai.raids.integration.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.shirokai.raids.TraditionalRaidBattles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fixture only: inspect the actual normal UI packet when Cobblemon constructs it. */
@Mixin(value=BattleInitializePacket.class,remap=false)
public abstract class BattlePacketCaptureMixin {
    @Inject(method="<init>(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;Lcom/cobblemon/mod/common/battles/BattleSide;)V",
        at=@At("RETURN"),remap=false)
    private void verifyInitialBossDTO(PokemonBattle battle,BattleSide ally,CallbackInfo ci) {
        if(!TraditionalRaidBattles.isBossActor(battle.getSide2().getActors()[0])) return;
        var packet=(BattleInitializePacket)(Object)this;
        if(packet.getSide2().getActors().getFirst().getActivePokemon().getFirst()==null)
            throw new AssertionError("Pacote real de inicialização não inclui boss");
    }
}
