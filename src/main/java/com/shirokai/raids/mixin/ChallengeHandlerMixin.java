package com.shirokai.raids.mixin;

import com.cobblemon.mod.common.net.messages.server.BattleChallengePacket;
import com.cobblemon.mod.common.net.serverhandling.ChallengeHandler;
import com.shirokai.raids.ShirokaiRaids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ChallengeHandler.class,remap=false)
public abstract class ChallengeHandlerMixin {
    // Cobblemon's handle is overloaded with a generic bridge. Pin the typed signature;
    // include named development and intermediary 1.21.1 runtime descriptors.
    @Inject(method={
        "handle(Lcom/cobblemon/mod/common/net/messages/server/BattleChallengePacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        "handle(Lcom/cobblemon/mod/common/net/messages/server/BattleChallengePacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/class_3222;)V"
    },at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void shirokaiChallenge(BattleChallengePacket packet,MinecraftServer server,ServerPlayer player,CallbackInfo ci) {
        if(ShirokaiRaids.tryChallenge(packet,player)) ci.cancel();
    }
}
