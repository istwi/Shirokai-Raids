package com.shirokai.raids;

import static com.shirokai.raids.RaidMessages.msg;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;

/** Server-only information and navigation menu; no inventory manipulation. */
final class RaidOverviewMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final Runnable join, leave, refresh;
    static void open(ServerPlayer viewer, Supplier<List<String>> info, Runnable join, Runnable leave, Runnable refresh) {
        viewer.openMenu(new SimpleMenuProvider((id, inventory, player) ->
            new RaidOverviewMenu(id, viewer, info.get(), join, leave, refresh), Component.literal(msg("textos.raid_informacoes"))));
    }
    private RaidOverviewMenu(int id, ServerPlayer viewer, List<String> info, Runnable join, Runnable leave, Runnable refresh) {
        super(MenuType.GENERIC_9x3, id, viewer.getInventory(), new SimpleContainer(27), 3);
        this.viewer=viewer; this.join=join; this.leave=leave; this.refresh=refresh;
        for (int slot = 0; slot < 27; slot++)
            icon(slot, Items.GRAY_STAINED_GLASS_PANE, " ", List.of(), ChatFormatting.GRAY);
        icon(10, Items.NETHER_STAR, msg("textos.entrar_na_raid"), List.of(
            msg("textos.teleporte_para_a_arena_do_boss"),
            msg("textos.envie_seu_pokemon_e_desafie_com_r"),
            "", msg("textos.clique_para_entrar")), ChatFormatting.GREEN);
        var details = new java.util.ArrayList<>(info);
        details.add("");
        details.add(msg("textos.clique_para_atualizar_as_informacoes"));
        details.add(msg("textos.fechar_o_menu_nao_sai_da_raid"));
        icon(13, Items.NETHER_STAR, msg("textos.informacoes_da_raid"), details, ChatFormatting.GOLD);
        icon(16, Items.NETHER_STAR, msg("textos.sair_da_raid"), List.of(
            msg("textos.encerra_sua_batalha_atual"),
            msg("textos.retorne_ao_local_anterior_a_entrada"),
            msg("textos.sua_contribuicao_sera_preservada"),
            "", msg("textos.clique_para_sair")), ChatFormatting.RED);
    }
    private void icon(int slot, Item item, String name, List<String> lore, ChatFormatting color) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)
            .withStyle(style -> style.withColor(color).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(line -> (Component) Component.literal(line)
            .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))).toList()));
        getContainer().setItem(slot, stack);
    }
    @Override public boolean stillValid(Player player) {
        return player == viewer && RaidPermissions.check(viewer.createCommandSourceStack(), "shirokai.raids.use", true);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != viewer) return;
        if (!stillValid(player)) { viewer.closeContainer(); return; }
        if (type == ClickType.PICKUP && button == 0) {
            if (slot == 10 || slot == 16 || slot == 13) {
                viewer.closeContainer();
                if (slot == 10) join.run(); else if (slot == 16) leave.run(); else refresh.run();
                return;
            }
        }
        broadcastFullState();
    }
}
