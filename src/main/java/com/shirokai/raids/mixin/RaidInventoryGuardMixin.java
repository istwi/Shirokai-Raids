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
    private void shirokaiAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) { if (shirokai$guard()) ci.cancel(); }
    @Inject(method="handleSetCreativeModeSlot", at=@At("HEAD"), cancellable=true)
    private void shirokaiCreative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) { if (shirokai$guard()) ci.cancel(); }
    @Inject(method="handlePlaceRecipe", at=@At("HEAD"), cancellable=true)
    private void shirokaiRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci) { if (shirokai$guard()) ci.cancel(); }
    @Inject(method="handlePickItem", at=@At("HEAD"), cancellable=true)
    private void shirokaiPick(ServerboundPickItemPacket packet, CallbackInfo ci) { if (shirokai$guard()) ci.cancel(); }
}
