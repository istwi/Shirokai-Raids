package com.shirokai.raids;

import com.mojang.authlib.GameProfile;
import com.shirokai.raids.mixin.PlayerListAccessor;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** Fixture only: real vanilla saves and NBT reloads at simulated process-interruption boundaries. */
public final class RewardRecoveryIntegration {
    private enum Cut { AFTER_RESERVATION, AFTER_INSERT, BEFORE_PLAYER_SAVE, AFTER_PLAYER_SAVE, BEFORE_ACK, AFTER_ACK }

    public static void verify(MinecraftServer server) throws Exception {
        int scenarios = 0;
        for (Cut cut : Cut.values()) {
            var player = FakePlayer.get(server.overworld(), new GameProfile(UUID.randomUUID(), "[ReceiptProbe]"));
            ((PlayerListAccessor) server.getPlayerList()).shirokaiSavePlayer(player);
            Path path = Path.of("reward-recovery", cut.name() + ".json");
            var state = new RaidState();
            String owner = player.getUUID().toString();
            state.pending.put(owner, new ArrayList<>(List.of(new RaidState.Pending("probe", "minecraft:apple", 4))));
            JsonFiles.write(path, state);
            var target = new PlayerRewardTarget(server, player);
            var interruptedTarget = new RewardDelivery.Target() {
                public String player() { return owner; }
                public boolean received(String id) { return target.received(id); }
                public void forget(String id) { target.forget(id); }
                public void prune(Set<String> ids) { target.prune(ids); }
                public RewardDelivery.Transfer plan(String item, int limit) {
                    var transfer = target.plan(item, limit);
                    if (transfer == null) return null;
                    return new RewardDelivery.Transfer() {
                        public int count() { return transfer.count(); }
                        public void apply(String id) {
                            transfer.apply(id);
                            if (cut == Cut.AFTER_INSERT) throw new IllegalStateException("simulated interruption");
                        }
                    };
                }
                public void saveAndVerify(String id) throws Exception {
                    if (cut == Cut.BEFORE_PLAYER_SAVE) throw new IllegalStateException("simulated interruption");
                    target.saveAndVerify(id);
                    if (cut == Cut.AFTER_PLAYER_SAVE) throw new IllegalStateException("simulated interruption");
                }
            };
            int[] writes = {0};
            var delivery = new RewardDelivery(state, () -> {
                int write = ++writes[0];
                if (cut == Cut.BEFORE_ACK && write == 2) throw new IllegalStateException("simulated interruption");
                persist(path, state);
                if (cut == Cut.AFTER_RESERVATION && write == 1 || cut == Cut.AFTER_ACK && write == 2)
                    throw new IllegalStateException("simulated interruption");
            });
            boolean interrupted = false;
            try { delivery.deliver(interruptedTarget, 4); } catch (IllegalStateException expected) { interrupted = true; }
            require(interrupted, "Fronteira de interrupção não executou: " + cut);

            // Discard live inventory/receipts and reload the file saved by Minecraft, not a mock.
            var playerFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner + ".dat");
            var saved = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
            player.getInventory().clearContent();
            player.getTags().clear();
            player.load(saved);
            var recovered = JsonFiles.read(path, RaidState.class); recovered.validate();
            var resumed = new RewardDelivery(recovered, () -> persist(path, recovered));
            resumed.deliver(new PlayerRewardTarget(server, player), 4);
            require(resumed.deliver(new PlayerRewardTarget(server, player), 4) == 0, "Replay após recuperação: " + cut);
            int apples = player.getInventory().items.stream().filter(s -> s.is(Items.APPLE)).mapToInt(s -> s.getCount()).sum();
            require(apples == 4, "Itens perdidos ou duplicados após NBT reload: " + cut + ": " + apples);
            var durable = JsonFiles.read(path, RaidState.class); durable.validate();
            require(durable.deliveries.isEmpty() && durable.pending.get(owner).isEmpty(), "Reserva não confirmada: " + cut);
            scenarios++;
        }
        Files.writeString(Path.of("reward-recovery-result.json"), "{\"passed\":true,\"scenarios\":" + scenarios + ",\"realClients\":0}");
        System.out.println("SHIROKAI_REWARD_RECOVERY: " + scenarios + " real playerdata reload scenarios passed; simulated interruptions, no JVM kill");
    }

    private static void persist(Path path, RaidState state) {
        try { JsonFiles.write(path, state); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
