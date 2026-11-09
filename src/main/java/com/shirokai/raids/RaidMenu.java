package com.shirokai.raids;

import static com.shirokai.raids.RaidMessages.msg;

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
        String title = (global ? msg("textos.ranking") + label(metric) : msg("textos.suas_ultimas_raids")) + " | " + (page + 1) + "/" + pages;
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
            if (entries.isEmpty()) container.setItem(4, icon(Items.PAPER, msg("textos.ranking_ainda_vazio"), List.of(text(msg("textos.os_totais_aparecem_apos_encerrar_uma_raid")))));
        } else {
            var entries = state.archive.recent(viewer.getUUID().toString());
            for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < entries.size(); i++) {
                var entry = entries.get(page * PAGE_SIZE + i);
                var lore = new ArrayList<Component>();
                var format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
                lore.add(text(msg("textos.data") + format.format(Instant.ofEpochMilli(entry.ended()))));
                lore.add(text(msg("textos.fuso") + zone));
                lore.add(text(msg("textos.resultado") + outcome(entry.outcome())));
                lore.add(text(msg("textos.duracao_2") + RaidTime.format((entry.ended() - entry.started()) / 1000)));
                lore.add(text(msg("textos.seu_dano") + number(entry.damage()) + " / " + number(entry.maximum()) + " HP"));
                lore.add(text(msg("historico.contribuicao", "porcentagem", String.format(Locale.ROOT,"%.2f",entry.contribution()))));
                lore.add(text(msg("textos.sua_posicao") + (entry.rank() == 0 ? msg("textos.sem_dano") : "#" + entry.rank()) + msg("textos.participantes") + entry.participants()));
                lore.add(text(msg("textos.drops_sorteados")));
                if (entry.drops().isEmpty()) lore.add(text(msg("textos.nenhum_drop_neste_evento")));
                for (var drop : new TreeMap<>(entry.drops()).entrySet()) {
                    var location = ResourceLocation.tryParse(drop.getKey());
                    Component name = location != null && BuiltInRegistries.ITEM.containsKey(location)
                        ? Component.translatable(BuiltInRegistries.ITEM.get(location).getDescriptionId()) : text(drop.getKey());
                    lore.add(Component.literal(drop.getValue() + "x ").append(name));
                }
                String owner = viewer.getUUID().toString();
                int pending = state.pending.getOrDefault(owner, List.of()).stream().filter(p -> p.raid.equals(entry.raid())).mapToInt(p -> p.count).sum()
                    + state.deliveries.values().stream().filter(d -> d.player.equals(owner) && d.reward.raid.equals(entry.raid())).mapToInt(d -> d.reward.count).sum();
                lore.add(text(msg("textos.itens_pendentes_de_entrega") + pending));
                lore.add(text(msg("textos.lista_informativa_nao_entrega_itens_ao_clicar")));
                container.setItem(i, icon(entry.outcome() == RaidArchive.Outcome.VICTORY ? Items.NETHER_STAR : Items.CLOCK, entry.boss(), lore));
            }
            if (entries.isEmpty()) container.setItem(4, icon(Items.BOOK, msg("textos.seu_historico_esta_vazio"), List.of(text(msg("textos.participe_de_uma_batalha_de_raid")), text(msg("textos.o_registro_aparece_quando_o_evento_termina")))));
        }
        if (page > 0) container.setItem(18, icon(Items.ARROW, msg("textos.pagina_anterior"), List.of()));
        container.setItem(19, icon(Items.BOOK, msg("textos.suas_ultimas_raids"), List.of(text(msg("textos.ate_20_eventos_por_jogador")))));
        container.setItem(20, icon(Items.CLOCK, msg("textos.global_participacoes"), List.of(text(msg("textos.uma_participacao_por_evento_mesmo_ao_sair_e_voltar")))));
        container.setItem(21, icon(Items.IRON_SWORD, msg("textos.global_dano_acumulado"), List.of(text(msg("textos.dano_efetivo_incluindo_eventos_cancelados")))));
        var own = state.archive.totals.get(viewer.getUUID().toString());
        var ownLore = new ArrayList<Component>(own == null ? List.of(text(msg("textos.nenhum_evento_encerrado_ainda"))) : totalsLore(own));
        ownLore.add(text(msg("textos.clique_para_atualizar_esta_pagina")));
        container.setItem(22, icon(Items.PAPER, msg("textos.seus_totais"), ownLore));
        container.setItem(23, icon(Items.GOLD_INGOT, msg("textos.global_vitorias"), List.of(text(msg("textos.eventos_vencidos_em_que_voce_participou")))));
        container.setItem(24, icon(Items.CHEST, msg("textos.sobre_os_drops"), List.of(text(msg("textos.entrega_automatica_no_inventario")), text(msg("textos.para_tentar_pendencias_raid_recompensas")), text(msg("textos.reservas_antigas_incertas_exigem_administracao")))));
        container.setItem(25, icon(Items.BARRIER, msg("textos.fechar"), List.of()));
        if (page + 1 < pages) container.setItem(26, icon(Items.ARROW, msg("textos.proxima_pagina"), List.of()));
    }
    private static String label(RaidArchive.Metric metric) {
        return switch (metric) { case PARTICIPATIONS -> msg("textos.participacoes"); case DAMAGE -> msg("textos.dano"); case VICTORIES -> msg("textos.vitorias"); };
    }
    private static String outcome(RaidArchive.Outcome outcome) {
        return switch (outcome) { case VICTORY -> msg("textos.boss_derrotado"); case TIMEOUT -> msg("textos.tempo_esgotado");
            case CANCELLED -> msg("textos.cancelada"); case INTERRUPTED -> msg("textos.interrompida_por_reinicio"); };
    }
    private static String number(long number) { return String.format(Locale.forLanguageTag("pt-BR"), "%,d", number); }
    private static List<Component> totalsLore(RaidArchive.Totals totals) {
        return List.of(text(msg("textos.participacoes_2") + number(totals.participations())), text(msg("textos.dano_acumulado") + number(totals.damage())), text(msg("textos.vitorias_2") + number(totals.victories())));
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
