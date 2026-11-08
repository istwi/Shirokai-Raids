package com.shirokai.raids;

import java.util.List;
import java.util.function.Supplier;
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
            new RaidOverviewMenu(id, viewer, info.get(), join, leave, refresh), Component.literal("Raid — Informações")));
    }
    private RaidOverviewMenu(int id, ServerPlayer viewer, List<String> info, Runnable join, Runnable leave, Runnable refresh) {
        super(MenuType.GENERIC_9x3, id, viewer.getInventory(), new SimpleContainer(27), 3);
        this.viewer=viewer; this.join=join; this.leave=leave; this.refresh=refresh;
        icon(4, Items.NETHER_STAR, "Informações da raid", info);
        icon(11, Items.ENDER_PEARL, "Entrar na raid", List.of("Teleporta para a arena configurada.", "Envie seu Pokémon e desafie o boss com R."));
        icon(13, Items.CLOCK, "Atualizar informações", List.of());
        icon(15, Items.OAK_DOOR, "Sair da raid", List.of("Encerra sua batalha e volta ao local anterior."));
        icon(22, Items.BARRIER, "Fechar menu", List.of("Fechar o menu não sai da raid."));
    }
    private void icon(int slot, Item item, String name, List<String> lore) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(line -> (Component) Component.literal(line)).toList()));
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
            if (slot == 22) { viewer.closeContainer(); return; }
            if (slot == 11 || slot == 15 || slot == 13) {
                viewer.closeContainer();
                if (slot == 11) join.run(); else if (slot == 15) leave.run(); else refresh.run();
                return;
            }
        }
        broadcastFullState();
    }
}
