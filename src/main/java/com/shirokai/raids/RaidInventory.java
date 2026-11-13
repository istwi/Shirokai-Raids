package com.shirokai.raids;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Admission check only. Personal inventory, armor and accessory storage are never copied or cleared. */
public final class RaidInventory {
    private RaidInventory() { }
    public static boolean locked(ServerPlayer player) { return ShirokaiRaids.participating(player) || RaidLegacyInventory.pending(player); }
    public static boolean emptyForEntry(ServerPlayer player) {
        return player.getInventory().items.stream().allMatch(ItemStack::isEmpty)
            && player.getOffhandItem().isEmpty() && player.containerMenu.getCarried().isEmpty();
    }
    public static void requireEmpty(ServerPlayer player) {
        if (RaidLegacyInventory.pending(player)) throw new IllegalStateException("Recupere o inventário do build anterior antes de entrar na raid.");
        if (!emptyForEntry(player)) throw new IllegalStateException(RaidMessages.msg("raid.inventario_vazio"));
        if (player.isCreative() || player.isSpectator()) throw new IllegalStateException(RaidMessages.msg("captura.sobrevivencia"));
    }
    static void removeRaidBalls(ServerPlayer player) {
        for (int i=0;i<player.getInventory().items.size();i++)
            if (RaidCapture.isBall(player.getInventory().getItem(i))) player.getInventory().setItem(i,ItemStack.EMPTY);
        if (RaidCapture.isBall(player.getOffhandItem())) player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
        if (RaidCapture.isBall(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
        sync(player);
    }
    static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.sendAllDataToRemote();
    }
}
