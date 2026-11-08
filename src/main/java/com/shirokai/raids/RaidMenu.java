package com.shirokai.raids;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Ordinary chest protocol, entirely read-only, including the visible player inventory. */
final class RaidMenu extends ChestMenu {
    static final int PAGE_SIZE = 18;
    private final ServerPlayer viewer;
    private final RaidState state;
    private final ZoneId zone;
    private final boolean global;
    private final RaidArchive.Metric metric;
    private final int page, pages;
    private final BooleanSupplier available;

    static boolean allowed(ServerPlayer player, boolean global) {
        var source = player.createCommandSourceStack();
        return RaidPermissions.check(source, "shirokai.raids.use", true)
            && RaidPermissions.check(source, global ? "shirokai.raids.ranking" : "shirokai.raids.history", true);
    }
    static void open(ServerPlayer player, RaidState state, ZoneId zone, boolean global, RaidArchive.Metric metric,
                     int requestedPage, BooleanSupplier available) {
        if (!available.getAsBoolean() || !allowed(player, global)) return;
        int size = global ? state.archive.totals.size() : state.archive.recent(player.getUUID().toString()).size();
        int pages = Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        String title = (global ? "Ranking: " + label(metric) : "Suas últimas raids") + " | " + (page + 1) + "/" + pages;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
            new RaidMenu(id, player, state, zone, global, metric, page, pages, available), Component.literal(title)));
    }
    private RaidMenu(int id, ServerPlayer viewer, RaidState state, ZoneId zone, boolean global,
                     RaidArchive.Metric metric, int page, int pages, BooleanSupplier available) {
        super(MenuType.GENERIC_9x3, id, viewer.getInventory(), new SimpleContainer(27), 3);
        this.viewer = viewer; this.state = state; this.zone = zone; this.global = global; this.metric = metric;
        this.page = page; this.pages = pages; this.available = available;
        render();
    }
    private void render() {
        var container = getContainer();
        for (int i = 18; i < 27; i++) container.setItem(i, icon(Items.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        if (global) {
            var entries = state.archive.ranking(metric);
            for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < entries.size(); i++) {
                var row = entries.get(page * PAGE_SIZE + i);
                container.setItem(i, icon(Items.PLAYER_HEAD, "#" + row.rank() + " — " + row.totals().name(), totalsLore(row.totals())));
            }
            if (entries.isEmpty()) container.setItem(4, icon(Items.PAPER, "Ranking ainda vazio", List.of(text("Os totais aparecem após encerrar uma raid."))));
        } else {
            var entries = state.archive.recent(viewer.getUUID().toString());
            for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < entries.size(); i++) {
                var entry = entries.get(page * PAGE_SIZE + i);
                var lore = new ArrayList<Component>();
                var format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
                lore.add(text("Data: " + format.format(Instant.ofEpochMilli(entry.ended()))));
                lore.add(text("Fuso: " + zone));
                lore.add(text("Resultado: " + outcome(entry.outcome())));
                lore.add(text("Duração: " + RaidTime.minutes((entry.ended() - entry.started()) / 1000)));
                lore.add(text("Seu dano: " + number(entry.damage()) + " / " + number(entry.maximum()) + " HP"));
                lore.add(text(String.format(Locale.ROOT, "Contribuição: %.2f%%", entry.contribution())));
                lore.add(text("Sua posição: " + (entry.rank() == 0 ? "sem dano" : "#" + entry.rank()) + " | Participantes: " + entry.participants()));
                lore.add(text("Drops sorteados:"));
                if (entry.drops().isEmpty()) lore.add(text("Nenhum drop neste evento."));
                for (var drop : new TreeMap<>(entry.drops()).entrySet()) {
                    var location = ResourceLocation.tryParse(drop.getKey());
                    Component name = location != null && BuiltInRegistries.ITEM.containsKey(location)
                        ? Component.translatable(BuiltInRegistries.ITEM.get(location).getDescriptionId()) : text(drop.getKey());
                    lore.add(Component.literal(drop.getValue() + "x ").append(name));
                }
                String owner = viewer.getUUID().toString();
                int pending = state.pending.getOrDefault(owner, List.of()).stream().filter(p -> p.raid.equals(entry.raid())).mapToInt(p -> p.count).sum()
                    + state.deliveries.values().stream().filter(d -> d.player.equals(owner) && d.reward.raid.equals(entry.raid())).mapToInt(d -> d.reward.count).sum();
                lore.add(text("Itens pendentes de entrega: " + pending));
                lore.add(text("Lista informativa; não entrega itens ao clicar."));
                container.setItem(i, icon(entry.outcome() == RaidArchive.Outcome.VICTORY ? Items.NETHER_STAR : Items.CLOCK, entry.boss(), lore));
            }
            if (entries.isEmpty()) container.setItem(4, icon(Items.BOOK, "Seu histórico está vazio", List.of(text("Participe de uma batalha de raid."), text("O registro aparece quando o evento termina."))));
        }
        if (page > 0) container.setItem(18, icon(Items.ARROW, "Página anterior", List.of()));
        container.setItem(19, icon(Items.BOOK, "Suas últimas raids", List.of(text("Até 20 eventos por jogador."))));
        container.setItem(20, icon(Items.CLOCK, "Global: participações", List.of(text("Uma participação por evento, mesmo ao sair e voltar."))));
        container.setItem(21, icon(Items.IRON_SWORD, "Global: dano acumulado", List.of(text("Dano efetivo, incluindo eventos cancelados."))));
        var own = state.archive.totals.get(viewer.getUUID().toString());
        var ownLore = new ArrayList<Component>(own == null ? List.of(text("Nenhum evento encerrado ainda.")) : totalsLore(own));
        ownLore.add(text("Clique para atualizar esta página."));
        container.setItem(22, icon(Items.PAPER, "Seus totais", ownLore));
        container.setItem(23, icon(Items.GOLD_INGOT, "Global: vitórias", List.of(text("Eventos vencidos em que você participou."))));
        container.setItem(24, icon(Items.CHEST, "Sobre os drops", List.of(text("Entrega automática no inventário."), text("Para tentar pendências: /raid recompensas"), text("Reservas antigas incertas exigem administração."))));
        container.setItem(25, icon(Items.BARRIER, "Fechar", List.of()));
        if (page + 1 < pages) container.setItem(26, icon(Items.ARROW, "Próxima página", List.of()));
    }
    private static String label(RaidArchive.Metric metric) {
        return switch (metric) { case PARTICIPATIONS -> "participações"; case DAMAGE -> "dano"; case VICTORIES -> "vitórias"; };
    }
    private static String outcome(RaidArchive.Outcome outcome) {
        return switch (outcome) { case VICTORY -> "Boss derrotado"; case TIMEOUT -> "Tempo esgotado";
            case CANCELLED -> "Cancelada"; case INTERRUPTED -> "Interrompida por reinício"; };
    }
    private static String number(long number) { return String.format(Locale.forLanguageTag("pt-BR"), "%,d", number); }
    private static List<Component> totalsLore(RaidArchive.Totals totals) {
        return List.of(text("Participações: " + number(totals.participations())), text("Dano acumulado: " + number(totals.damage())), text("Vitórias: " + number(totals.victories())));
    }
    private static Component text(String text) { return Component.literal(text).withStyle(ChatFormatting.GRAY); }
    private static ItemStack icon(Item item, String name, List<Component> lore) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }
    @Override public boolean stillValid(Player player) {
        return player == viewer && available.getAsBoolean() && allowed(viewer, global);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != viewer) return;
        if (!stillValid(player)) { viewer.closeContainer(); return; }
        // Ignore swaps, shift, drag, clone, drop, double click and all inventory/cursor manipulation.
        if (type == ClickType.PICKUP && button == 0) {
            if (slot == 25) { viewer.closeContainer(); return; }
            if (slot == 18 && page > 0) navigate(global, metric, page - 1);
            else if (slot == 26 && page + 1 < pages) navigate(global, metric, page + 1);
            else if (slot == 19) navigate(false, metric, 0);
            else if (slot == 20) navigate(true, RaidArchive.Metric.PARTICIPATIONS, 0);
            else if (slot == 21) navigate(true, RaidArchive.Metric.DAMAGE, 0);
            else if (slot == 22) navigate(global, metric, page);
            else if (slot == 23) navigate(true, RaidArchive.Metric.VICTORIES, 0);
            else if (slot == 24) { ShirokaiRaids.openPendingMenu(viewer); return; }
        }
        broadcastFullState();
    }
    private void navigate(boolean global, RaidArchive.Metric metric, int page) {
        open(viewer, state, zone, global, metric, page, available);
    }
}
