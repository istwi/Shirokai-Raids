package com.shirokai.raids;

import static com.shirokai.raids.RaidMessages.msg;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Only the delivery button invokes the existing journal; displayed stacks never leave the menu. */
final class PendingRewardMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final RaidState state;
    private final ZoneId zone;
    private final int page, pages;
    private final BooleanSupplier available;
    private final Runnable attempt;
    static boolean allowed(ServerPlayer player) {
        var source = player.createCommandSourceStack();
        return RaidPermissions.check(source,"shirokai.raids.use",true) && RaidPermissions.check(source,"shirokai.raids.rewards",true);
    }
    static void open(ServerPlayer player, RaidState state, ZoneId zone, int requestedPage, BooleanSupplier available, Runnable attempt) {
        if (!available.getAsBoolean() || !allowed(player)) return;
        String owner = player.getUUID().toString();
        var entries = new ArrayList<>(state.pending.getOrDefault(owner, List.of()));
        state.deliveries.values().stream().filter(d -> d.player.equals(owner)).forEach(d -> entries.add(d.reward));
        int pages = Math.max(1, (entries.size() + 17) / 18);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
            new PendingRewardMenu(id, player, state, zone, page, pages, available, attempt, entries),
            Component.literal(msg("textos.recompensas_pendentes") + (page+1) + "/" + pages)));
    }
    private PendingRewardMenu(int id, ServerPlayer player, RaidState state, ZoneId zone, int page, int pages,
                              BooleanSupplier available, Runnable attempt, List<RaidState.Pending> entries) {
        super(MenuType.GENERIC_9x3, id, player.getInventory(), new SimpleContainer(27), 3);
        viewer = player; this.state = state; this.zone = zone; this.page = page; this.pages = pages;
        this.available = available; this.attempt = attempt;
        var container = getContainer();
        for (int i = 0; i < 18 && page*18+i < entries.size(); i++) {
            var entry = entries.get(page*18+i);
            var key = ResourceLocation.tryParse(entry.item);
            var item = key != null && BuiltInRegistries.ITEM.containsKey(key) ? BuiltInRegistries.ITEM.get(key) : Items.BARRIER;
            if (item == Items.AIR) item = Items.BARRIER;
            var stack = new ItemStack(item);
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(msg("textos.quantidade_pendente") + entry.count),
                Component.literal(msg("textos.evento") + entry.raid), Component.literal(msg("textos.item") + entry.item),
                Component.literal(msg("textos.use_o_botao_de_entrega_este_icone_e_informativo")))));
            container.setItem(i, stack);
        }
        if (entries.isEmpty()) container.setItem(4, icon(Items.PAPER,msg("textos.nenhuma_recompensa_pendente")));
        if (page > 0) container.setItem(18, icon(Items.ARROW,msg("textos.pagina_anterior")));
        container.setItem(19, icon(Items.BOOK,msg("textos.historico_pessoal")));
        container.setItem(20, icon(Items.GOLD_INGOT,msg("textos.ranking_global")));
        container.setItem(22, icon(Items.LIME_DYE,msg("textos.tentar_entregar_no_inventario")));
        container.setItem(23, icon(Items.CLOCK,msg("textos.atualizar_pendencias")));
        container.setItem(24, icon(Items.PAPER,msg("textos.sem_espaco_saldo_preservado_aguarde_5s_entre_tentativas")));
        container.setItem(25, icon(Items.BARRIER,msg("textos.fechar")));
        if (page+1 < pages) container.setItem(26, icon(Items.ARROW,msg("textos.proxima_pagina")));
    }
    private static ItemStack icon(Item item, String name) {
        var stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    @Override public boolean stillValid(Player player) { return player == viewer && available.getAsBoolean() && allowed(viewer); }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != viewer) return;
        if (!stillValid(player)) { viewer.closeContainer(); return; }
        if (type == ClickType.PICKUP && button == 0) {
            if (slot == 25) { viewer.closeContainer(); return; }
            if (slot == 19 || slot == 20) { RaidMenu.open(viewer, state, zone, slot == 20, RaidArchive.Metric.PARTICIPATIONS, 0, available); return; }
            if (slot == 22) {
                attempt.run();
                if (!available.getAsBoolean()) { viewer.closeContainer(); return; }
            }
            if (slot == 22 || slot == 23 || slot == 18 && page > 0 || slot == 26 && page+1 < pages) {
                open(viewer, state, zone, slot == 18 ? page-1 : slot == 26 ? page+1 : page, available, attempt); return;
            }
        }
        broadcastFullState();
    }
}
