package com.shirokai.raids;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Native server menus and packets; no human client or rendering simulation. */
final class AdminRewardMenuIntegration {
    private int checks;
    private void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static RaidAdminRewardMenu menu(ServerPlayer player){if(!(player.containerMenu instanceof RaidAdminRewardMenu m))throw new AssertionError("Prévia administrativa não abriu");return m;}
    private static String name(RaidAdminRewardMenu menu,int slot){return menu.getContainer().getItem(slot).getHoverName().getString();}
    private static String lore(RaidAdminRewardMenu menu,int slot){return menu.getContainer().getItem(slot).getOrDefault(DataComponents.LORE,net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream().map(c->c.getString()).reduce("",(a,b)->a+"\n"+b);}
    private static Map<String,Integer> owned(ServerPlayer player){var result=new TreeMap<String,Integer>();for(var s:player.getInventory().items)if(!s.isEmpty())result.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),s.getCount(),Integer::sum);var s=player.containerMenu.getCarried();if(!s.isEmpty())result.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),s.getCount(),Integer::sum);return result;}
    static void verify(MinecraftServer server)throws Exception{new AdminRewardMenuIntegration().run(server);}
    private void run(MinecraftServer server)throws Exception{
        var profile=new GameProfile(UUID.randomUUID(),"AdminRewardsProbe");var fake=FakePlayer.get(server.overworld(),profile);
        var player=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());player.connection=fake.connection;player.setPos(0,200,0);
        var dispatcher=server.getCommands().getDispatcher();var source=player.createCommandSourceStack().withPermission(4);
        server.getPlayerList().op(profile);
        require(RaidAdminRewardMenu.allowed(player),"OP não reconhecido pela prévia");
        var statePath=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/shirokai-raids/state.json");String before=Files.readString(statePath);
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,10));
        dispatcher.execute("raidadmin",source);var menu=menu(player);require(lore(menu,9).contains("Dollars")&&lore(menu,9).contains("Fragmentos"),"Resumo dos tipos ausente");
        menu.setCarried(new ItemStack(Items.EMERALD,2));var baseline=owned(player);var cursor=menu.getCarried().copy();
        for(var type:ClickType.values())for(int slot:new int[]{18,44,54,89,-999})for(int button:new int[]{0,1,2,8,40}){
            menu.clicked(slot,button,type,player);require(owned(player).equals(baseline)&&ItemStack.matches(cursor,menu.getCarried()),"Prévia alterou inventário/cursor em "+type+"/"+slot);
        }
        require(menu.quickMoveStack(player,18).isEmpty()&&!menu.canDragTo(menu.getSlot(18)),"Shift/arraste não bloqueado");
        for(int i=0;i<5;i++){menu(player).clicked(i,0,ClickType.PICKUP,player);require(name(menu(player),49).contains("Página"),"Navegação por dificuldade falhou");}
        menu(player).clicked(5,0,ClickType.PICKUP,player);require(name(menu(player),5).contains("TOP 3"),"Categoria especial não abriu");
        for(int i=0;i<3;i++)menu(player).clicked(6,0,ClickType.PICKUP,player);
        require(name(menu(player),6).contains("4º")&&name(menu(player),11).contains("100%"),"Fora do TOP 3 foi mostrada chance de especial");
        menu(player).clicked(7,0,ClickType.PICKUP,player);require(name(menu(player),7).contains("25%"),"Filtro de contribuição falhou");
        menu(player).clicked(5,0,ClickType.PICKUP,player);require(name(menu(player),5).contains("Itens antigos")&&lore(menu(player),10).contains("Comum"),"Raridades tradicionais não apareceram");
        menu(player).clicked(8,0,ClickType.PICKUP,player);require(name(menu(player),0).contains("Padrão"),"Seletor de bosses não abriu");
        menu(player).clicked(0,0,ClickType.PICKUP,player);require(name(menu(player),5).contains("Itens antigos"),"Escolher padrão não retornou à prévia");
        player.closeContainer();require(owned(player).equals(baseline),"Navegação concedeu ou removeu itens");require(Files.readString(statePath).equals(before),"Menu alterou o estado persistente das recompensas");
        dispatcher.execute("raidadmin menu",source);require(player.containerMenu instanceof RaidAdminRewardMenu,"Alias menu falhou");player.closeContainer();
        dispatcher.execute("raidadmin recompensas rayquaza",source);require(name(menu(player),8).contains("Rayquaza"),"Consulta de boss por ID falhou");player.closeContainer();

        // More than one page and a mutable source: refresh must show newly loaded settings.
        var settings=RaidRewardSettings.defaults();settings.integrations.maximumRewardsPerPlayer=64;
        var prizes=new ArrayList<RaidRewardSettings.Reward>();for(int i=0;i<60;i++)prizes.add(new RaidRewardSettings.Reward("preview_"+i,"currency","impactor:dollars",1,1));settings.general.pools.put("normal",prizes);settings.validate();
        var contexts=List.of(new RaidAdminRewardMenu.Context("","Padrão",null,null,null,null,""));
        var current=new AtomicReference<>(new RaidAdminRewardMenu.Source(settings,Map.of("default",RaidConfig.Rewards.defaults()),Map.of("normal","default"),contexts,(type,id)->true));
        RaidAdminRewardMenu.open(player,current::get,()->true,"");require(name(menu(player),49).contains("1/3"),"Primeira página incorreta");menu(player).clicked(53,0,ClickType.PICKUP,player);require(name(menu(player),49).contains("2/3")&&lore(menu(player),18).contains("preview_27"),"Paginação perdeu recompensa");menu(player).clicked(53,0,ClickType.PICKUP,player);require(name(menu(player),49).contains("3/3")&&menu(player).getContainer().getItem(44).isEmpty(),"Última página contém ícones antigos");
        menu(player).clicked(45,0,ClickType.PICKUP,player);menu(player).clicked(45,0,ClickType.PICKUP,player);
        var changed=JsonFiles.copy(settings,RaidRewardSettings.class);changed.general.pools.get("normal").getFirst().chance=.5;
        current.set(new RaidAdminRewardMenu.Source(changed,current.get().legacy(),current.get().defaultTables(),contexts,(type,id)->false));
        require(lore(menu(player),18).contains("Chance inicial: 100%"),"Snapshot mudou sem atualização");menu(player).clicked(16,0,ClickType.PICKUP,player);require(lore(menu(player),18).contains("Chance inicial: 50%")&&lore(menu(player),18).contains("Indisponível"),"Atualização não refletiu chance/integração");player.closeContainer();

        // Real active raid versus a reloaded next-raid configuration.
        Path chances=Path.of("config/shirokai-raids/rewards-v2/top3.json");String original=Files.readString(chances);
        dispatcher.execute("raidadmin iniciar rayquaza",source);
        try{
            dispatcher.execute("raidadmin recompensas",source);require(name(menu(player),8).contains("andamento"),"Raid ativa não foi selecionada");menu(player).clicked(5,0,ClickType.PICKUP,player);
            require(lore(menu(player),18).contains("Chance inicial: 10%"),"Snapshot inicial do TOP 1 incorreto");
            var next=JsonFiles.read(chances,RaidRewardSettings.Top3.class);next.chances.put("normal",List.of(.6,.18,.08));JsonFiles.write(chances,next);dispatcher.execute("raidadmin rewardreload",source);
            menu(player).clicked(16,0,ClickType.PICKUP,player);require(lore(menu(player),18).contains("Chance inicial: 10%"),"Reload alterou a prévia da raid já iniciada");menu(player).clicked(0,0,ClickType.PICKUP,player);require(lore(menu(player),18).contains("Chance inicial: 20%"),"Perfil das próximas raids não atualizou");
        }finally{player.closeContainer();Files.writeString(chances,original);dispatcher.execute("raidadmin rewardreload",source);dispatcher.execute("raidadmin parar",source);}

        // Opening and clicking after a permission revocation must fail closed.
        dispatcher.execute("raidadmin",source);menu=menu(player);server.getPlayerList().deop(profile);require(!menu.stillValid(player),"Revogar OP não invalidou a prévia");menu.clicked(18,0,ClickType.PICKUP,player);require(player.containerMenu==player.inventoryMenu,"Menu permaneceu aberto após revogação");
        boolean denied=false;try{dispatcher.execute("raidadmin",player.createCommandSourceStack().withPermission(0));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}require(denied,"Jogador sem permissão abriu menu administrativo");
        require(owned(player).equals(baseline),"Prévia emitiu itens após todos os testes");
        JsonFiles.write(Path.of("admin-rewards-menu-result.json").toAbsolutePath(),Map.of("passed",true,"checks",checks,"realClients",0));System.out.println("SHIROKAI_ADMIN_REWARDS_MENU "+checks+" checks passed");
    }
}
