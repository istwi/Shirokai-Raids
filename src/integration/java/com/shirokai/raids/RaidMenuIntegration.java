package com.shirokai.raids;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;

/** Server-side vanilla menu interactions; does not validate rendering in a real client. */
public final class RaidMenuIntegration {
    public static void verify(MinecraftServer server) throws Exception {
        // Fabric FakePlayer suppresses openMenu. Use vanilla ServerPlayer with an inert test connection
        // so the real openMenu/createMenu/closeContainer methods execute without a network client.
        var player = new net.minecraft.server.level.ServerPlayer(server, server.overworld(),
            new GameProfile(UUID.randomUUID(), "[MenuProbe]"), net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = FakePlayer.get(server.overworld()).connection;
        server.getCommands().getDispatcher().execute("raid historico", player.createCommandSourceStack());
        require(player.containerMenu instanceof RaidMenu, "Comando de histórico não abriu o menu vanilla");
        player.closeContainer();
        server.getCommands().getDispatcher().execute("raid ranking", player.createCommandSourceStack());
        require(player.containerMenu instanceof RaidMenu, "Comando de ranking não abriu o menu vanilla");
        player.closeContainer();
        String owner = player.getUUID().toString();
        var state = new RaidState();
        for (int i = 0; i < 20; i++) {
            var event = new RaidArchive.Event("menu-" + i, "Boss " + i, "rayquaza", 1000, 1000);
            event.participants.put(owner, "MenuProbe"); event.damage.put(owner, 400L);
            state.archive.current = event;
            state.archive.complete(RaidArchive.Outcome.VICTORY, 2000 + i, Map.of(owner, Map.of("minecraft:apple", 4)));
        }
        String other = UUID.randomUUID().toString();
        state.archive.totals.put(other, new RaidArchive.Totals("DamageLeader", 1, 1, 100_000));
        state.archive.history.put(other, new ArrayList<>(List.of(new RaidArchive.History("private", "PrivateBoss", "mew", 1000, 2000,
            RaidArchive.Outcome.CANCELLED, 0, 1000, 0, 1, Map.of()))));
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        var before = player.getInventory().items.stream().map(ItemStack::copy).toList();
        String initialState = new com.google.gson.Gson().toJson(state);
        boolean[] available = {true};
        RaidMenu.open(player, state, ZoneId.of("America/Sao_Paulo"), false, RaidArchive.Metric.PARTICIPATIONS, 0, () -> available[0]);
        require(player.containerMenu instanceof RaidMenu, "Menu vanilla não abriu");
        var menu = (RaidMenu) player.containerMenu;
        require(name(menu, 0).equals("Boss 19"), "Histórico não começa pelo evento mais recente");
        require(name(menu, 26).equals("Próxima página"), "Paginação do histórico ausente");
        var lore = menu.getSlot(0).getItem().get(DataComponents.LORE).lines().toString();
        require(lore.contains("40.00%") && lore.contains("4x") && lore.contains("#1"), "Informações de contribuição/ranking/drops ausentes");
        require(!lore.contains("PrivateBoss"), "Histórico de outro jogador vazou");
        menu.setCarried(new ItemStack(Items.EMERALD, 3));
        var cursor = menu.getCarried().copy();
        int checks = 0;
        for (ClickType type : ClickType.values()) for (int slot : new int[]{0, 27, -999}) for (int button : new int[]{0, 1, 2, 8, 40}) {
            menu.clicked(slot, button, type, player);
            require(ItemStack.matches(cursor, menu.getCarried()), "Clique alterou o cursor: " + type);
            require(menu.getSlot(0).getItem().getCount() == 1, "Ícone removido do menu: " + type);
            for (int i = 0; i < before.size(); i++) require(ItemStack.matches(before.get(i), player.getInventory().items.get(i)), "Clique alterou inventário: " + type);
            checks++;
        }
        require(menu.quickMoveStack(player, 0).isEmpty() && !menu.canDragTo(menu.getSlot(0)), "Transferência direta permitida");
        require(initialState.equals(new com.google.gson.Gson().toJson(state)), "Inspeção do menu alterou registro/recompensas");
        // Closing must return the carried stack through vanilla, rather than losing it.
        player.closeContainer();
        require(player.getInventory().items.stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() == 3, "Cursor perdido ao fechar");
        RaidMenu.open(player, state, ZoneId.of("UTC"), false, RaidArchive.Metric.PARTICIPATIONS, 0, () -> available[0]);
        ((RaidMenu) player.containerMenu).clicked(26, 0, ClickType.PICKUP, player);
        require(name((RaidMenu) player.containerMenu, 0).equals("Boss 1"), "Próxima página incorreta");
        ((RaidMenu) player.containerMenu).clicked(18, 0, ClickType.PICKUP, player);
        require(name((RaidMenu) player.containerMenu, 0).equals("Boss 19"), "Página anterior incorreta");
        ((RaidMenu) player.containerMenu).clicked(20, 0, ClickType.PICKUP, player);
        require(name((RaidMenu) player.containerMenu, 0).contains("MenuProbe"), "Ordenação por participações incorreta");
        ((RaidMenu) player.containerMenu).clicked(21, 0, ClickType.PICKUP, player);
        require(name((RaidMenu) player.containerMenu, 0).contains("DamageLeader"), "Ordenação por dano incorreta");
        ((RaidMenu) player.containerMenu).clicked(23, 0, ClickType.PICKUP, player);
        require(name((RaidMenu) player.containerMenu, 0).contains("MenuProbe"), "Ordenação por vitórias incorreta");
        available[0] = false;
        require(!player.containerMenu.stillValid(player), "Menu continuou válido com persistência suspensa");
        player.containerMenu.clicked(19, 0, ClickType.PICKUP, player);
        require(player.containerMenu == player.inventoryMenu, "Menu não fechou quando indisponível");
        Files.writeString(Path.of("raid-menu-result.json"), "{\"passed\":true,\"clickChecks\":" + checks + ",\"realClients\":0}");
        System.out.println("SHIROKAI_RAID_MENU: history privacy, pagination, global metrics and " + checks + " read-only click checks passed");
    }
    private static String name(RaidMenu menu, int slot) { return menu.getSlot(slot).getItem().getHoverName().getString(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
