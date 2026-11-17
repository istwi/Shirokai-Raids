package com.shirokai.raids.mixin;

import com.shirokai.raids.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RaidInventoryGuardMixin {
    @Shadow public ServerPlayer player;
    @Unique private boolean shirokai$guard() {
        if (!player.server.isSameThread() || !RaidInventory.locked(player)) return false;
        player.server.execute(() -> player.containerMenu.sendAllDataToRemote());
        return true;
    }
    @Inject(method="handleContainerClick", at=@At("HEAD"), cancellable=true)
    private void shirokaiClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (ShirokaiRaids.readOnlyRaidMenu(player)) return;
        if (shirokai$guard()) ci.cancel();
    }
    @Inject(method="handlePlayerAction", at=@At("HEAD"), cancellable=true)
    private void shirokaiAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!shirokai$guard()) return;
        switch (packet.getAction()) {
            case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> {
                // Vanilla normally acknowledges predictions; cancelling at HEAD skips that step.
                player.connection.ackBlockChangesUpTo(packet.getSequence());
                var pos=packet.getPos();
                var world=player.serverLevel();
                // Never load arbitrary chunks supplied by a client packet.
                if (pos.distSqr(player.blockPosition())<=4096 && world.hasChunkAt(pos)) {
                    player.connection.send(new ClientboundBlockUpdatePacket(world,pos));
                    player.connection.send(new ClientboundBlockDestructionPacket(player.getId(),pos,-1));
                }
            }
            default -> { }
        }
        ci.cancel();
    }
    @Inject(method="handleSetCreativeModeSlot", at=@At("HEAD"), cancellable=true)
    private void shirokaiCreative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) { if (shirokai$guard()) ci.cancel(); }
    @Inject(method="handlePlaceRecipe", at=@At("HEAD"), cancellable=true)
    private void shirokaiRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci) { if (shirokai$guard()) ci.cancel(); }
    @Inject(method="handlePickItem", at=@At("HEAD"), cancellable=true)
    private void shirokaiPick(ServerboundPickItemPacket packet, CallbackInfo ci) { if (shirokai$guard()) ci.cancel(); }
}
