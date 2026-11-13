package com.shirokai.raids;
import net.minecraft.nbt.CompoundTag;
/** Compatibility only: preserves inventories saved by the earlier, unpublished development build. */
public interface RaidInventoryAccess {
    CompoundTag shirokaiInventory();
    void shirokaiInventory(CompoundTag value);
}
