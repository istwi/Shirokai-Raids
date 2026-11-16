package com.shirokai.raids;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;
import java.util.function.*;

/** Read-only live view of the persisted automatic raid cycle. */
final class RaidSpecialMenu extends ChestMenu {
    record Progress(long starts,boolean catalogEnabled,boolean automaticEnabled) {
        int remaining(){return RaidAutomaticCatalog.slotsUntilSpecial(starts);}
        List<String> lines(){
            int remaining=remaining();
            return List.of("Raids automáticas iniciadas: "+starts,
                "Progresso do ciclo: "+(RaidCatalogSettings.SPECIAL_INTERVAL-remaining)+"/20",
                remaining==1?"A próxima raid automática será especial!":"Faltam "+remaining+" raids automáticas até a especial (incluindo ela).",
                "Raids comuns antes da especial: "+(remaining-1),
                catalogEnabled?"Catálogo automático: ativado":"Catálogo desativado: ciclo especial não será usado.",
                automaticEnabled?"Agendamento automático: ativado":"Agendamento automático desativado: contador pausado.",
                "Raids manuais não contam; não é necessário vencer.",
                "Lendários, míticos, Ultra Beasts e Paradox.",
                "Sem especial elegível, o início é adiado.");
        }
    }
    private final ServerPlayer viewer;
    private final Supplier<Progress> progress;
    private final BooleanSupplier available;
    private Progress displayed;
    static void open(ServerPlayer player,Supplier<Progress> progress,BooleanSupplier available){
        if(!available.getAsBoolean()||!RaidAdminRewardMenu.allowed(player))return;
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new RaidSpecialMenu(id,player,progress,available),RaidMessages.component("&6Próxima raid especial")));
    }
    private RaidSpecialMenu(int id,ServerPlayer viewer,Supplier<Progress> progress,BooleanSupplier available){
        super(MenuType.GENERIC_9x3,id,viewer.getInventory(),new SimpleContainer(27),3);
        this.viewer=viewer;this.progress=progress;this.available=available;
        render();
    }
    private void render(){
        displayed=progress.get();
        getContainer().setItem(13,icon(Items.NETHER_STAR,"&6Próxima raid especial",displayed.lines()));
        getContainer().setItem(22,icon(Items.CLOCK,"&aAtualizar",List.of("Atualização automática enquanto o menu está aberto.")));
        getContainer().setItem(26,icon(Items.BARRIER,"&cFechar",List.of()));
    }
    private static ItemStack icon(Item item,String name,List<String> lore){
        var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,RaidMessages.component(name));
        stack.set(DataComponents.LORE,new ItemLore(lore.stream().map(RaidMessages::component).toList()));return stack;
    }
    @Override public void broadcastChanges(){
        if(available.getAsBoolean()&&!Objects.equals(displayed,progress.get()))render();
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player player){return player==viewer&&available.getAsBoolean()&&RaidAdminRewardMenu.allowed(viewer);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canDragTo(Slot slot){return false;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player!=viewer)return;
        if(!stillValid(player)){viewer.closeContainer();return;}
        if(type==ClickType.PICKUP&&button==0){
            if(slot==26){viewer.closeContainer();return;}
            if(slot==22)render();
        }
        broadcastFullState();
    }
}
