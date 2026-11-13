package com.shirokai.raids.mixin;

import com.shirokai.raids.RaidCapture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A missed raid throw consumes an attempt; Cobblemon must not refund a normal ball. */
@Mixin(Entity.class)
public abstract class RaidBallRefundMixin {
    @Inject(method="spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;", at=@At("HEAD"), cancellable=true)
    private void shirokaiRefund(ItemLike item, CallbackInfoReturnable<ItemEntity> cir) {
        if (((Entity)(Object)this).getTags().contains(RaidCapture.BALL_TAG)) cir.setReturnValue(null);
    }
}
