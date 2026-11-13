package com.shirokai.raids.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.shirokai.raids.RaidInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Cobblemon interaction wheel can exchange held/cosmetic items without a vanilla inventory click. */
@Mixin(value=PokemonEntity.class,remap=false)
public abstract class RaidPokemonItemGuardMixin {
    @Inject(method={"offerHeldItem","offerCosmeticItem"}, at=@At("HEAD"), cancellable=true, remap=false)
    private void shirokaiKeepPokemonItems(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer serverPlayer && RaidInventory.locked(serverPlayer)) cir.setReturnValue(false);
    }
}
