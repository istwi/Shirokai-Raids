package com.shirokai.raids.mixin;
import com.shirokai.raids.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Reads/preserves legacy data only. New admissions never create a backup. */
@Mixin(ServerPlayer.class)
public abstract class RaidInventoryMixin implements RaidInventoryAccess {
    @Unique private CompoundTag shirokai$legacy;
    public CompoundTag shirokaiInventory() { return shirokai$legacy; }
    public void shirokaiInventory(CompoundTag value) { shirokai$legacy=value; }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void shirokaiSave(CompoundTag tag,CallbackInfo ci) {
        if(shirokai$legacy!=null) tag.put(RaidLegacyInventory.KEY,shirokai$legacy.copy());
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void shirokaiLoad(CompoundTag tag,CallbackInfo ci) {
        shirokai$legacy=tag.contains(RaidLegacyInventory.KEY)?tag.getCompound(RaidLegacyInventory.KEY).copy():null;
    }
    @Inject(method="restoreFrom",at=@At("TAIL"))
    private void shirokaiClone(ServerPlayer previous,boolean keepEverything,CallbackInfo ci) {
        var old=((RaidInventoryAccess)previous).shirokaiInventory();shirokai$legacy=old==null?null:old.copy();
    }
}
