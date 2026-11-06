package com.shirokai.raids;

import com.mojang.authlib.GameProfile;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Operational commands and actual region-ticket/inventory behavior in a disposable world. */
public final class OperationsIntegration {
    public static void verify(MinecraftServer server) throws Exception {
        ServerPlayer player = FakePlayer.get(server.overworld(), new GameProfile(UUID.randomUUID(), "[OpsProbe]"));
        var source = player.createCommandSourceStack().withPermission(4);
        var dispatcher = server.getCommands().getDispatcher();
        var configPath = Path.of("config/shirokai-raids/config.json");
        String original = Files.readString(configPath);
        var originalConfig = JsonFiles.read(configPath, RaidConfig.class);
        Field singleton = ShirokaiRaids.class.getDeclaredField("instance"); singleton.setAccessible(true);
        var mod = singleton.get(null);
        Field activeField = ShirokaiRaids.class.getDeclaredField("active"); activeField.setAccessible(true);
        Field stateField = ShirokaiRaids.class.getDeclaredField("state"); stateField.setAccessible(true);
        var state = (RaidState) stateField.get(mod);
        dispatcher.execute("raidadmin automatico false", source);
        dispatcher.execute("raidadmin intervalo 120", source);
        dispatcher.execute("raidadmin duracao 30", source);
        dispatcher.execute("raidadmin limite 3", source);
        var settings = JsonFiles.read(configPath, RaidConfig.class);
        require(!settings.automaticRaids && settings.intervalSeconds == 120 && settings.durationSeconds == 30 && settings.maxConcurrentPlayers == 3, "Controles administrativos não persistiram");
        player.setPos(20, 200, 20); player.setYRot(73); player.setXRot(11);
        require(dispatcher.execute("raidadmin setlobby", source) == 1, "Lobby não configurado");
        player.setPos(1000, 200, 1000);
        require(dispatcher.execute("raid lobby", source) == 1 && player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(20.5,200,20.5)) < 1,
            "Teleporte seguro ao lobby falhou");
        require(player.getYRot() == 73 && player.getXRot() == 11, "Orientação do lobby perdida");
        require(dispatcher.execute("raid lobby", source) == 0, "Cooldown do lobby ignorado");
        boolean denied = false;
        try { dispatcher.execute("raidadmin iniciar rayquaza", player.createCommandSourceStack().withPermission(0)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        require(denied && activeField.get(mod) == null, "Jogador sem permissão iniciou raid");
        player.setPos(0,200,0);
        dispatcher.execute("raidadmin setspawn", source);
        String valid = Files.readString(configPath);
        var invalid = JsonFiles.read(configPath, RaidConfig.class); invalid.dimension = "missing:dimension";
        JsonFiles.write(configPath, invalid);
        dispatcher.execute("raidadmin reload", source);
        Files.writeString(configPath, valid);
        var forced = new HashSet<Long>(server.overworld().getForcedChunks());
        require(dispatcher.execute("raidadmin iniciar rayquaza", source) == 1, "Configuração válida não preservada após reload rejeitado");
        Object active = activeField.get(mod);
        require(active != null, "Raid não iniciou após reload inválido");
        Field chunks = active.getClass().getDeclaredField("chunks"); chunks.setAccessible(true);
        var lease = (RaidChunkLease) chunks.get(active);
        require(!lease.closed() && hasRaidTicket(server.overworld()), "Ticket temporário ausente");
        dispatcher.execute("raidadmin parar", source);
        require(lease.closed() && !hasRaidTicket(server.overworld()), "Ticket não liberado ao cancelar");
        lease.close();
        require(forced.equals(new HashSet<Long>(server.overworld().getForcedChunks())), "Estado externo de /forceload foi alterado");
        require(RaidChunkLease.radiusFor(128) == 9 && RaidChunkLease.radiusFor(8) == 2, "Limites de carregamento incorretos");
        var orphan = PokemonProperties.Companion.parse("rayquaza level=10").createEntity(server.overworld());
        orphan.addTag("shirokai_raid_boss"); orphan.setPos(0,200,0);
        server.overworld().addFreshEntity(orphan);
        require(orphan.isRemoved(), "Boss órfão com tag atual não foi removido");
        var ordinary = PokemonProperties.Companion.parse("rayquaza level=10").createEntity(server.overworld());
        ordinary.setPos(0,200,0); server.overworld().addFreshEntity(ordinary);
        require(!ordinary.isRemoved(), "Pokémon comum sem tag foi removido"); ordinary.discard();

        // FakePlayer's own connection is required for teleportation, but it suppresses openMenu.
        // Switch to a vanilla player with the same profile only for the menu/inventory checks.
        var operator = player;
        player = new ServerPlayer(server, server.overworld(), operator.getGameProfile(), ClientInformation.createDefault());
        player.connection = operator.connection;
        source = player.createCommandSourceStack().withPermission(4);
        String owner = player.getUUID().toString();
        state.pending.computeIfAbsent(owner, key -> new ArrayList<>()).add(new RaidState.Pending("ops-menu", "minecraft:apple", 4));
        JsonFiles.write(Path.of("world/data/shirokai-raids/state.json"), state);
        dispatcher.execute("raid recompensas", source);
        require(player.containerMenu instanceof PendingRewardMenu, "Menu de pendências não abriu");
        AbstractContainerMenu menu = player.containerMenu;
        menu.setCarried(new ItemStack(Items.EMERALD,2));
        var inventory = player.getInventory().items.stream().map(ItemStack::copy).toList();
        var cursor = menu.getCarried().copy();
        int checks = 0;
        for (ClickType type : ClickType.values()) for (int slot : new int[]{0,27,-999}) for (int button : new int[]{0,1,2,8,40}) {
            menu.clicked(slot, button, type, player);
            require(ItemStack.matches(cursor, menu.getCarried()), "Ícone de pendência alterou cursor");
            for (int i=0; i<inventory.size(); i++) require(ItemStack.matches(inventory.get(i),player.getInventory().items.get(i)), "Ícone de pendência alterou inventário");
            checks++;
        }
        player.closeContainer();
        dispatcher.execute("raid recompensas", source);
        player.containerMenu.clicked(22,0,ClickType.PICKUP,player);
        int apples = player.getInventory().items.stream().filter(s -> s.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum();
        require(apples == 4 && state.pending.get(owner).isEmpty(), "Botão não usou a entrega persistente");
        player.containerMenu.clicked(22,0,ClickType.PICKUP,player);
        require(player.getInventory().items.stream().filter(s -> s.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum() == 4, "Repetição do botão duplicou itens");
        player.closeContainer();
        String id = owner + ":" + UUID.randomUUID() + ":" + UUID.randomUUID();
        state.uncertain.put(id, new RaidState.Pending("ops-legacy", "minecraft:diamond",3));
        JsonFiles.write(Path.of("world/data/shirokai-raids/state.json"), state);
        String refund = "raidadmin reservas devolver \"" + id + "\" comprovado no backup de teste";
        require(dispatcher.execute(refund, source) == 1 && !state.uncertain.containsKey(id), "Conferência administrativa falhou");
        require(dispatcher.execute(refund, source) == 0 && state.pending.get(owner).stream().mapToInt(p -> p.count).sum() == 3, "Conferência repetida emitiu itens adicionais");
        require(state.reviews.containsKey(id), "Auditoria da conferência não persistiu");
        dispatcher.execute("raidadmin intervalo " + originalConfig.intervalSeconds, source);
        Files.writeString(configPath, original); dispatcher.execute("raidadmin reload", source);
        Files.writeString(Path.of("operations-result.json"), "{\"passed\":true,\"pendingClickChecks\":"+checks+",\"realClients\":0}");
        System.out.println("SHIROKAI_OPERATIONS: lobby, orientation, permissions, configuration, ticket release, orphan cleanup, pending menu and legacy review passed");
    }
    private static boolean hasRaidTicket(ServerLevel world) throws Exception {
        Field field = Arrays.stream(ServerChunkCache.class.getDeclaredFields()).filter(f -> f.getType() == DistanceManager.class).findFirst().orElseThrow();
        field.setAccessible(true); Object manager = field.get(world.getChunkSource());
        Method method = Arrays.stream(DistanceManager.class.getDeclaredMethods()).filter(m -> Arrays.equals(m.getParameterTypes(),new Class<?>[]{long.class})
            && m.getReturnType() == net.minecraft.util.SortedArraySet.class).findFirst().orElseThrow();
        method.setAccessible(true);
        var tickets = (Iterable<?>) method.invoke(manager, new net.minecraft.world.level.ChunkPos(0,0).toLong());
        for (Object ticket : tickets) if (ticket.toString().contains("shirokai_raids")) return true;
        return false;
    }
    private static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
