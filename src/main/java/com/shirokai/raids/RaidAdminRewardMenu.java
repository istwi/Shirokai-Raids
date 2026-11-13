package com.shirokai.raids;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;
import java.util.function.*;

/** Read-only administrator preview. Every action rechecks permissions; all inventory click paths are blocked. */
final class RaidAdminRewardMenu extends ChestMenu {
    static final List<String> DIFFICULTIES=List.of("normal","raro","epico","lendario","supremo");
    static final List<Integer> RANKS=List.of(1,2,3,4,5,10,25,50,100);
    record Context(String id,String label,String difficulty,String legacyTable,RaidRewardSettings snapshot,RaidConfig.Rewards legacySnapshot,String rewardBossId) {}
    record Source(RaidRewardSettings settings,Map<String,RaidConfig.Rewards> legacy,Map<String,String> defaultTables,List<Context> contexts,BiPredicate<String,String> available) {}
    record View(String difficulty,RaidRewardOdds.Scope scope,int rank,double share,String boss,int page,boolean bosses) {}
    private final ServerPlayer viewer;
    private final Supplier<Source> source;
    private final BooleanSupplier healthy;
    private final View view;
    private final int pages;
    private final List<Context> contexts;

    static boolean allowed(ServerPlayer player){var command=player.createCommandSourceStack();return RaidPermissions.check(command,"shirokai.raids.admin",command.hasPermission(2));}
    static boolean allowedCommand(String command){return command.strip().matches("raidadmin(?: +menu| +recompensas(?: +[a-z0-9_@.-]+)?)?");}
    static void open(ServerPlayer player,Supplier<Source> source,BooleanSupplier healthy,String requested){
        if(!healthy.getAsBoolean()||!allowed(player))return;
        var data=source.get();
        String boss=requested==null?(data.contexts.stream().anyMatch(c->c.id.equals("@active"))?"@active":""):requested;
        if(!boss.isEmpty()&&data.contexts.stream().noneMatch(c->c.id.equals(boss)))throw new IllegalArgumentException("Boss/override não encontrado: "+boss);
        open(player,source,healthy,new View("normal",RaidRewardOdds.Scope.GENERAL,1,.10,boss,0,false),data);
    }
    private static void open(ServerPlayer player,Supplier<Source> source,BooleanSupplier healthy,View view,Source data){
        if(!healthy.getAsBoolean()||!allowed(player))return;
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new RaidAdminRewardMenu(id,player,source,healthy,view,data),RaidMessages.component(RaidMessages.msg("admin.recompensas.titulo"))));
    }
    private RaidAdminRewardMenu(int id,ServerPlayer player,Supplier<Source> source,BooleanSupplier healthy,View requested,Source data){
        super(MenuType.GENERIC_9x6,id,player.getInventory(),new SimpleContainer(54),6);
        viewer=player;this.source=source;this.healthy=healthy;
        contexts=data.contexts;
        if(requested.bosses){
            pages=Math.max(1,(contexts.size()+44)/45);int page=Math.clamp(requested.page,0,pages-1);
            view=new View(requested.difficulty,requested.scope,requested.rank,requested.share,requested.boss,page,true);
            for(int i=0;i<45&&page*45+i<contexts.size();i++){
                var c=contexts.get(page*45+i);getContainer().setItem(i,icon(c.id.equals("@active")?Items.NETHER_STAR:Items.PAPER,"&e"+c.label,List.of("&7"+(c.id.isEmpty()?"Perfil padrão":c.id),c.snapshot!=null?"&bTabelas preservadas da raid ativa":"&7Configuração atual; próximas raids","&fClique para consultar.")));
            }
            if(page>0)getContainer().setItem(45,icon(Items.ARROW,"&eAnterior",List.of()));
            getContainer().setItem(49,icon(Items.BOOK,"&fVoltar",List.of("&7Página "+(page+1)+"/"+pages)));
            getContainer().setItem(50,icon(Items.BARRIER,"&cFechar",List.of()));
            if(page+1<pages)getContainer().setItem(53,icon(Items.ARROW,"&ePróxima",List.of()));
            return;
        }
        var context=contexts.stream().filter(c->c.id.equals(requested.boss)).findFirst().orElse(contexts.getFirst());
        String base=context.difficulty==null?requested.difficulty:context.difficulty;
        var settings=context.snapshot==null?data.settings:context.snapshot;
        String legacyId=context.legacyTable==null?data.defaultTables.get(base):context.legacyTable;
        var legacy=context.legacySnapshot==null?data.legacy.get(legacyId):context.legacySnapshot;
        var odds=RaidRewardOdds.calculate(settings,base,context.rewardBossId,requested.scope,requested.rank,requested.share,legacy);
        pages=Math.max(1,(odds.lines().size()+26)/27);int page=Math.clamp(requested.page,0,pages-1);
        view=new View(odds.difficulty(),requested.scope,requested.rank,requested.share,context.id,page,false);
        for(int i=0;i<5;i++)getContainer().setItem(i,icon(difficultyItem(i),(odds.difficulty().equals(DIFFICULTIES.get(i))?"&f» ":"")+difficultyName(DIFFICULTIES.get(i)),List.of("&7Consultar perfil padrão","&7Selecionar limpa o filtro de boss.")));
        getContainer().setItem(5,icon(Items.CHEST,"&eCategoria: "+scopeName(view.scope),List.of("&7Clique: Gerais → TOP 3 → Itens antigos")));
        getContainer().setItem(6,icon(Items.GOLDEN_HELMET,"&ePosição: "+view.rank+"º",List.of("&7Clique para mudar a posição.","&fTOP 1: "+RaidRewardOdds.percent(odds.topChances().get(0)),"&fTOP 2: "+RaidRewardOdds.percent(odds.topChances().get(1)),"&fTOP 3: "+RaidRewardOdds.percent(odds.topChances().get(2)),"&7Fora do TOP 3: sem sorteio especial.")));
        getContainer().setItem(7,icon(Items.IRON_SWORD,"&eContribuição: "+RaidRewardOdds.percent(view.share),List.of("&7Clique para alterar a simulação.","&7As chances respeitam contribuição e posição.")));
        getContainer().setItem(8,icon(Items.NAME_TAG,"&eBoss: "+context.label,List.of("&7Clique para escolher boss/override.",context.snapshot==null?"&7Configuração atual":"&bSnapshot da raid em andamento")));
        var typeLore=new ArrayList<String>();typeLore.add(view.scope==RaidRewardOdds.Scope.GENERAL?"&7Ao menos um por tipo, por vitória":view.scope==RaidRewardOdds.Scope.LEGACY?"&7Chance por sorteio, não por vitória":"&7Por vitória e posição simulada");
        odds.types().forEach((key,chance)->typeLore.add("&f"+RaidRewardOdds.typeName(key)+": &e"+RaidRewardOdds.percent(chance)));
        getContainer().setItem(9,icon(Items.GOLD_INGOT,"&6&lChances por tipo",typeLore));
        var rarityLore=new ArrayList<String>();rarityLore.add(view.scope==RaidRewardOdds.Scope.GENERAL?"&7Ao menos um por raridade, por vitória":view.scope==RaidRewardOdds.Scope.LEGACY?"&7Chance por sorteio, não por vitória":"&7Por vitória e posição simulada");
        odds.rarities().forEach((key,chance)->rarityLore.add("&f"+RaidRewardOdds.rarityName(key)+": &e"+RaidRewardOdds.percent(chance)));
        rarityLore.add("&7Raridade do prêmio ≠ dificuldade do boss.");
        getContainer().setItem(10,icon(Items.AMETHYST_SHARD,"&d&lChances por raridade",rarityLore));
        getContainer().setItem(11,icon(Items.GLASS_BOTTLE,"&eSem prêmio: "+RaidRewardOdds.percent(odds.none()),List.of("&7Nesta categoria e situação simulada.","&7Itens antigos: chance por sorteio.")));
        getContainer().setItem(12,icon(Items.BOOK,"&fComo ler as porcentagens",List.of("&7"+odds.basis(),view.scope==RaidRewardOdds.Scope.GENERAL?"&7Cada prêmio geral é um sorteio separado.":"&7As chances incluem o filtro selecionado.","&7TOP 3: chance da posição × peso × chance.","&7Duplicatas/compensações ocorrem depois.","&7Disponibilidade não elimina o sorteio:","&7prêmios sem integração ficam pendentes.")));
        getContainer().setItem(13,icon(Items.COMPARATOR,"&fEstado e multiplicadores",List.of(odds.enabled()?"&aCategoria ativada":"&cCategoria desativada","&7Quantidade: multiplicador x"+number(odds.multiplier()),"&7Contribuição mínima: "+RaidRewardOdds.percent(view.scope==RaidRewardOdds.Scope.LEGACY&&legacy!=null?legacy.minimumContribution:settings.integrations.minimumContribution),view.scope==RaidRewardOdds.Scope.LEGACY?"&7Máximo de itens por vitória: "+(legacy==null?0:legacy.maximumItems):"&7Máximo por entrega: "+settings.integrations.maximumAmount,"&7Fragmentos: máximo de 64.","&7Quantidades mostram a contribuição simulada.")));
        getContainer().setItem(14,icon(Items.PAPER,"&fPrévia somente para consulta",List.of("&7Não sorteia nem entrega recompensas.","&7Nenhum item deste menu pode ser retirado.")));
        getContainer().setItem(15,icon(Items.NAME_TAG,"&fPolíticas de cosméticos",List.of("&7Títulos: "+settings.duplicates.titles.policy,"&7Partículas: "+settings.duplicates.particles.policy,"&7Chances mostradas são antes das políticas.")));
        getContainer().setItem(16,icon(Items.CLOCK,"&aAtualizar configurações da prévia",List.of("&7Lê as configurações já carregadas.","&7Para reler JSON: /raidadmin rewardreload")));
        getContainer().setItem(17,icon(Items.BARRIER,"&cFechar",List.of()));
        for(int i=0;i<27&&page*27+i<odds.lines().size();i++){
            var line=odds.lines().get(page*27+i);boolean available=data.available.test(line.type(),line.reference());
            var lore=new ArrayList<String>();
            lore.add("&7"+line.id());lore.add("&7Tipo: &f"+RaidRewardOdds.typeName(RaidRewardOdds.typeKey(line.type(),line.reference())));
            lore.add("&7Raridade: &f"+RaidRewardOdds.rarityName(line.rarity()));lore.add((view.scope==RaidRewardOdds.Scope.LEGACY?"&aChance por sorteio: &f":"&aChance inicial: &f")+RaidRewardOdds.percent(line.chance()));
            lore.add("&7Chance configurada: "+RaidRewardOdds.percent(line.configuredChance()));if(view.scope!=RaidRewardOdds.Scope.GENERAL)lore.add("&7Seleção por peso: "+RaidRewardOdds.percent(line.selectionChance()));
            lore.add((view.scope==RaidRewardOdds.Scope.LEGACY?"&7Quantidade base: &f":"&7Quantidade calculada: &f")+line.minimum()+"–"+line.maximum());
            lore.add("&7Contribuição mínima: "+RaidRewardOdds.percent(line.minimumContribution()));
            lore.add("&7Posição máxima: "+(view.scope==RaidRewardOdds.Scope.SPECIAL?Math.min(3,line.maximumRank()==0?3:line.maximumRank()):line.maximumRank()==0?"Sem restrição":line.maximumRank()));
            lore.add(line.eligible()?"&aElegível nesta simulação":"&cNão elegível nesta simulação");
            lore.add(available?"&aIntegração/item disponível":"&eIndisponível; entrega ficará pendente");
            lore.add("&8"+line.reference());
            var item=itemFor(line.type(),line.reference());
            getContainer().setItem(18+i,icon(item,"&f"+ShirokaiRaids.rewardName(line.type(),line.reference()),lore));
        }
        if(odds.lines().isEmpty())getContainer().setItem(31,icon(Items.PAPER,"&7Nenhuma recompensa neste pool",List.of()));
        if(page>0)getContainer().setItem(45,icon(Items.ARROW,"&eAnterior",List.of()));
        getContainer().setItem(49,icon(Items.PAPER,"&fPágina "+(page+1)+"/"+pages,List.of("&7"+scopeName(view.scope),"&7"+odds.basis())));
        if(page+1<pages)getContainer().setItem(53,icon(Items.ARROW,"&ePróxima",List.of()));
    }
    private static String number(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.',',');}
    static String scopeName(RaidRewardOdds.Scope scope){return switch(scope){case GENERAL->"Gerais";case SPECIAL->"TOP 3";case LEGACY->"Itens antigos";};}
    static String difficultyName(String difficulty){return switch(difficulty){case "normal"->"&aNormal";case "raro"->"&9Raro";case "epico"->"&5Épico";case "lendario"->"&6Lendário";case "supremo"->"&cSupremo";default->difficulty;};}
    private static Item difficultyItem(int i){return List.of(Items.IRON_INGOT,Items.LAPIS_LAZULI,Items.AMETHYST_SHARD,Items.GOLD_INGOT,Items.NETHER_STAR).get(i);}
    private static Item itemFor(String type,String reference){
        if(type.equals("item")){var id=ResourceLocation.tryParse(reference);if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))return BuiltInRegistries.ITEM.get(id);return Items.BARRIER;}
        return switch(type){case "currency"->reference.equals("impactor:tokens")?Items.AMETHYST_SHARD:Items.GOLD_NUGGET;case "fragment"->Items.PRISMARINE_SHARD;case "title"->Items.NAME_TAG;case "particle"->Items.FIREWORK_STAR;default->Items.CHEST;};
    }
    private static ItemStack icon(Item item,String name,List<String> lore){var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,RaidMessages.component(name));stack.set(DataComponents.LORE,new ItemLore(lore.stream().map(RaidMessages::component).toList()));return stack;}
    @Override public boolean stillValid(Player player){return player==viewer&&healthy.getAsBoolean()&&allowed(viewer);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canDragTo(Slot slot){return false;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player!=viewer)return;if(!stillValid(player)){viewer.closeContainer();return;}
        if(type!=ClickType.PICKUP||button!=0){broadcastFullState();return;}
        if((!view.bosses&&slot==17)||(view.bosses&&slot==50)){viewer.closeContainer();return;}
        var next=view;
        if(view.bosses){
            if(slot>=0&&slot<45&&view.page*45+slot<contexts.size()){var c=contexts.get(view.page*45+slot);next=new View(view.difficulty,view.scope,view.rank,view.share,c.id,0,false);}
            else if(slot==49)next=new View(view.difficulty,view.scope,view.rank,view.share,view.boss,0,false);
            else if(slot==45&&view.page>0)next=new View(view.difficulty,view.scope,view.rank,view.share,view.boss,view.page-1,true);
            else if(slot==53&&view.page+1<pages)next=new View(view.difficulty,view.scope,view.rank,view.share,view.boss,view.page+1,true);
        }else{
            if(slot>=0&&slot<5)next=new View(DIFFICULTIES.get(slot),view.scope,view.rank,view.share,"",0,false);
            else if(slot==5)next=new View(view.difficulty,RaidRewardOdds.Scope.values()[(view.scope.ordinal()+1)%3],view.rank,view.share,view.boss,0,false);
            else if(slot==6)next=new View(view.difficulty,view.scope,RANKS.get((RANKS.indexOf(view.rank)+1)%RANKS.size()),view.share,view.boss,0,false);
            else if(slot==7){var shares=new TreeSet<Double>(List.of(.01,.05,.10,.25,.50,.75,1d));shares.add(source.get().settings.integrations.minimumContribution);var values=List.copyOf(shares);double share=values.stream().filter(s->s>view.share+1e-9).findFirst().orElse(values.getFirst());next=new View(view.difficulty,view.scope,view.rank,share,view.boss,0,false);}
            else if(slot==8)next=new View(view.difficulty,view.scope,view.rank,view.share,view.boss,0,true);
            else if(slot==16)next=new View(view.difficulty,view.scope,view.rank,view.share,view.boss,view.page,false);
            else if(slot==45&&view.page>0)next=new View(view.difficulty,view.scope,view.rank,view.share,view.boss,view.page-1,false);
            else if(slot==53&&view.page+1<pages)next=new View(view.difficulty,view.scope,view.rank,view.share,view.boss,view.page+1,false);
        }
        if(!next.equals(view)||slot==16&&!view.bosses){open(viewer,source,healthy,next,source.get());return;}
        broadcastFullState();
    }
}
