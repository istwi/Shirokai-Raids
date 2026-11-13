package com.shirokai.raids.mixin;

import com.shirokai.raids.RaidCapture;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class RaidCaptureVisibilityMixin {
    @Inject(method="broadcastToPlayer", at=@At("HEAD"), cancellable=true)
    private void shirokaiPrivateTarget(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        var entity=(Entity)(Object)this;
        if (entity.getTags().contains(RaidCapture.TARGET_TAG))
            cir.setReturnValue(entity.getTags().contains(RaidCapture.OWNER_TAG+player.getUUID()));
    }
}
