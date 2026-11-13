package com.shirokai.raids.mixin;

import com.shirokai.raids.RaidCapture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class RaidCaptureCollisionMixin {
    @Inject(method="canHitEntity", at=@At("HEAD"), cancellable=true)
    private void shirokaiOwnTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        var projectile=(Projectile)(Object)this;
        if (target.getTags().contains(RaidCapture.TARGET_TAG)
            && (!projectile.getTags().contains(RaidCapture.BALL_TAG) || projectile.getOwner()==null
                || !target.getTags().contains(RaidCapture.OWNER_TAG+projectile.getOwner().getUUID()))) cir.setReturnValue(false);
        else if (projectile.getTags().contains(RaidCapture.BALL_TAG) && !target.getTags().contains(RaidCapture.TARGET_TAG)) cir.setReturnValue(false);
    }
}
