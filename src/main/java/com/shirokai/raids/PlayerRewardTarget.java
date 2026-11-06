package com.shirokai.raids;

import com.shirokai.raids.mixin.PlayerListAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.util.*;

/** Vanilla entity Tags are saved in the same player NBT as Inventory. No client mod required. */
final class PlayerRewardTarget implements RewardDelivery.Target {
    private static final String PREFIX = "shirokai_delivery:";
    private final MinecraftServer server;
    private final ServerPlayer player;

    PlayerRewardTarget(MinecraftServer server, ServerPlayer player) {
        this.server = server;
        this.player = player;
    }
    public String player() { return player.getUUID().toString(); }
    public boolean received(String id) { return player.getTags().contains(PREFIX + id); }
    public void forget(String id) { player.removeTag(PREFIX + id); }
    public void prune(Set<String> outstanding) {
        for (String tag : Set.copyOf(player.getTags())) {
            if (!tag.startsWith(PREFIX)) continue;
            String id = tag.substring(PREFIX.length());
            try { UUID.fromString(id); } catch (IllegalArgumentException ignored) { continue; }
            if (!outstanding.contains(id)) player.removeTag(tag);
        }
    }

    public RewardDelivery.Transfer plan(String item, int limit) {
        var id = ResourceLocation.tryParse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || player.getTags().size() >= 1024) return null;
        var template = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (template.isEmpty()) return null;
        var inventory = player.getInventory();
        var before = new LinkedHashMap<Integer, ItemStack>();
        var after = new LinkedHashMap<Integer, ItemStack>();
        int remaining = limit;
        // Main inventory only, existing compatible stacks first. Never touch armor/offhand or drop items.
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < inventory.items.size() && remaining > 0; slot++) {
                var current = inventory.getItem(slot);
                if (pass == 0 ? current.isEmpty() || !ItemStack.isSameItemSameComponents(current, template) : !current.isEmpty()) continue;
                int space = Math.min(template.getMaxStackSize(), inventory.getMaxStackSize()) - current.getCount();
                int count = Math.min(remaining, space);
                if (count <= 0) continue;
                before.put(slot, current.copy());
                var next = current.isEmpty() ? template.copy() : current.copy();
                next.setCount(current.getCount() + count);
                after.put(slot, next);
                remaining -= count;
            }
        }
        int count = limit - remaining;
        if (count == 0) return null;
        return new RewardDelivery.Transfer() {
            public int count() { return count; }
            public void apply(String receipt) {
                // Planning and insertion run synchronously on the server thread, with no callbacks between.
                try {
                    after.forEach((slot, stack) -> inventory.setItem(slot, stack.copy()));
                    if (!player.addTag(PREFIX + receipt)) throw new IllegalStateException("Não foi possível registrar a entrega");
                    inventory.setChanged();
                    player.inventoryMenu.broadcastChanges();
                } catch (RuntimeException error) {
                    before.forEach((slot, stack) -> inventory.setItem(slot, stack.copy()));
                    player.removeTag(PREFIX + receipt);
                    throw error;
                }
            }
        };
    }

    public void saveAndVerify(String id) throws IOException {
        if (!received(id)) throw new IOException("Entrega sem recibo em memória");
        ((PlayerListAccessor) server.getPlayerList()).shirokaiSavePlayer(player);
        var path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player() + ".dat");
        var saved = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        var tags = saved.getList("Tags", Tag.TAG_STRING);
        for (int i = 0; i < tags.size(); i++) if (tags.getString(i).equals(PREFIX + id)) return;
        throw new IOException("Salvamento do inventário não confirmou o recibo; reserva preservada");
    }
}
