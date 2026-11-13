package com.shirokai.raids.mixin;

import com.shirokai.raids.RaidInventory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class RaidPickupMixin {
    @Inject(method="playerTouch", at=@At("HEAD"), cancellable=true)
    private void shirokaiPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && RaidInventory.locked(serverPlayer)) ci.cancel();
    }
}
