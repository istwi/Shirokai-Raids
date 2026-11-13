package com.shirokai.raids;

import com.mojang.authlib.GameProfile;
import com.shirokai.raids.core.RaidDamageTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.impactdev.impactor.api.economy.EconomyService;
import net.kyori.adventure.key.Key;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.math.BigDecimal;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Real Impactor + signed Crates items, two JVMs. No transport/client simulation. */
public final class ModularRewardIntegration implements ModInitializer {
    private static final UUID ONLINE=UUID.fromString("92000000-0000-0000-0000-000000000001"),OFFLINE=UUID.fromString("92000000-0000-0000-0000-000000000002");
    private final String raid="92000000-0000-0000-0000-000000000003";
    private MinecraftServer server;
    private RaidRewardJournal.Data data;
    private RaidRewardJournal journal;
    private FakePlayer online,offline;
    private int ticks,phase,checks;
    private boolean second,reading;
    private Path state;
    private BigDecimal initialDollars,initialTokens;
    private void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{server=s;try{tick();}catch(Throwable e){finish(false,e.toString());}});}
    private void tick()throws Exception {
        ticks++;
        if(ticks==40)setup();
        if(journal==null)return;
        if(ticks%10==0)journal.tick(8);
        if(phase==1&&!reading&&ticks%20==0) {
            boolean ready=data.grants.values().stream().allMatch(g->g.status==RaidRewardJournal.Status.DONE || (!second&&g.player.equals(OFFLINE.toString())&&g.choice.reward.type.equals("fragment")) || (second&&g.status==RaidRewardJournal.Status.UNCERTAIN));
            if(ready){reading=true;verifyBalances();}
        }
        if(ticks>2400)throw new AssertionError("Timeout phase="+phase+" statuses="+data.grants.values().stream().map(g->g.choice.reward.reference+":"+g.status).toList());
    }
    @SuppressWarnings("unchecked") private FakePlayer player(UUID id,String name)throws Exception {
        var p=FakePlayer.get(server.overworld(),new GameProfile(id,name));p.setPos(0,200,0);
        var saved=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id+".dat");
        if(Files.exists(saved))p.load(NbtIo.readCompressed(saved,NbtAccounter.unlimitedHeap()));
        for(var f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(f.getGenericType() instanceof ParameterizedType t&&t.getRawType()==Map.class&&Arrays.equals(t.getActualTypeArguments(),new Type[]{UUID.class,net.minecraft.server.level.ServerPlayer.class})){f.setAccessible(true);((Map<UUID,net.minecraft.server.level.ServerPlayer>)f.get(server.getPlayerList())).put(id,p);break;}
        server.getPlayerList().getPlayers().add(p);return p;
    }
    private void setup()throws Exception {
        state=Path.of("reward-probe.json").toAbsolutePath();second=Files.exists(state);online=player(ONLINE,"RewardProbe");if(second)offline=player(OFFLINE,"OfflineProbe");
        data=second?JsonFiles.read(state,RaidRewardJournal.Data.class):new RaidRewardJournal.Data();
        var currency=new ImpactorRaidRewards(()->true);var fragments=new CratesRaidRewards(server,()->true);
        require(currency.available("impactor:dollars")&&currency.available("impactor:tokens"),"Moedas reais não registradas");require(fragments.available("shiny1"),"Crate real indisponível");
        var template=fragments.template("shiny1");require(!template.isEmpty()&&template.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),"Fragmento sem assinatura original");
        journal=new RaidRewardJournal(data,()->{try{JsonFiles.write(state,data);}catch(Exception e){throw new RuntimeException(e);}},server,System.out::println);
        journal.register("currency",currency);journal.register("fragment",fragments);
        // Fixture injection: the production ticker/receipt pruning must observe this same outbox.
        var instanceField=ShirokaiRaids.class.getDeclaredField("instance");instanceField.setAccessible(true);var mod=instanceField.get(null);
        var stateField=ShirokaiRaids.class.getDeclaredField("state");stateField.setAccessible(true);((RaidState)stateField.get(mod)).modularRewards=data;
        var journalField=ShirokaiRaids.class.getDeclaredField("modularRewards");journalField.setAccessible(true);journalField.set(mod,journal);
        if(second){journal.recover();require(data.grants.values().stream().filter(g->g.status==RaidRewardJournal.Status.UNCERTAIN).count()==1,"Pagamento incerto foi repetido automaticamente");phase=1;return;}
        balance(ONLINE,"impactor:dollars").thenCombine(balance(ONLINE,"impactor:tokens"),(d,t)->{initialDollars=d;initialTokens=t;return true;}).whenCompleteAsync((v,error)->{
            try{
                if(error!=null)throw new RuntimeException(error);
                JsonFiles.write(Path.of("reward-baseline.json").toAbsolutePath(),Map.of("dollars",initialDollars,"tokens",initialTokens));
                var c=RaidRewardSettings.defaults();c.general.pools.put("normal",List.of(fixed("dollars","currency","impactor:dollars",25),fixed("tokens","currency","impactor:tokens",3),fixed("fragments","fragment","shiny1",2)));c.top3.chances.put("normal",List.of(0d,0d,0d));
                var tracker=new RaidDamageTracker();tracker.record(ONLINE,"online",UUID.randomUUID(),600,0);tracker.record(OFFLINE,"offline",UUID.randomUUID(),400,0);
                journal.award(raid,"rayquaza","normal",1000,tracker.ranking(),c,new Random(1));JsonFiles.write(state,data);
                journal.award(raid,"rayquaza","normal",1000,tracker.ranking(),c,new Random(2));require(data.grants.size()==6,"Sorteio duplicado");phase=1;
            }catch(Throwable e){finish(false,e.toString());}
        },server);
    }
    private static RaidRewardSettings.Reward fixed(String id,String type,String ref,int amount){var r=new RaidRewardSettings.Reward(id,type,ref,amount,amount);r.baseMultiplier=1;r.damageMultiplier=0;return r;}
    private CompletionStage<BigDecimal> balance(UUID id,String key){var e=EconomyService.instance();return e.account(e.currencies().currency(Key.key(key)).orElseThrow(),id).thenApply(a->a.balance());}
    private void verifyBalances(){
        balance(ONLINE,"impactor:dollars").thenCombine(balance(ONLINE,"impactor:tokens"),(d,t)->List.of(d,t)).thenCombine(balance(OFFLINE,"impactor:dollars"),(values,d)->List.of(values.get(0),values.get(1),d)).whenCompleteAsync((values,error)->{
            try{
                if(error!=null)throw new RuntimeException(error);
                var baseline=JsonFiles.read(Path.of("reward-baseline.json").toAbsolutePath(),com.google.gson.JsonObject.class);
                require(values.get(0).compareTo(baseline.get("dollars").getAsBigDecimal().add(BigDecimal.valueOf(25)))==0,"Dollars duplicados ou não entregues");
                require(values.get(1).compareTo(baseline.get("tokens").getAsBigDecimal().add(BigDecimal.valueOf(3)))==0,"Tokens duplicados ou não entregues");
                require(values.get(2).compareTo(BigDecimal.valueOf(525))==0,"Pagamento offline incorreto");
                require(fragments(online)==2,"Fragmentos online duplicados ou perdidos");
                if(!second){
                    require(data.grants.values().stream().filter(g->g.status==RaidRewardJournal.Status.PENDING).count()==1,"Fragmento offline não preservado");
                    var paid=data.grants.values().stream().filter(g->g.player.equals(ONLINE.toString())&&g.choice.reward.reference.equals("impactor:dollars")).findFirst().orElseThrow();paid.status=RaidRewardJournal.Status.IN_FLIGHT;
                    var item=data.grants.values().stream().filter(g->g.player.equals(ONLINE.toString())&&g.choice.reward.type.equals("fragment")).findFirst().orElseThrow();item.status=RaidRewardJournal.Status.IN_FLIGHT;
                    JsonFiles.write(state,data);finish(true,"phase1: API real, moedas offline, fragmentos assinados, recibo salvo; interrupções preparadas");
                }else{
                    require(fragments(offline)==2,"Fragmento pendente não entregue no login");
                    var uncertain=data.grants.values().stream().filter(g->g.status==RaidRewardJournal.Status.UNCERTAIN).findFirst().orElseThrow();journal.review(uncertain.id,true,"Conferência da conta real após reinício");
                    require(data.grants.values().stream().allMatch(g->g.status==RaidRewardJournal.Status.DONE),"Entregas não encerradas");finish(true,"phase2: novo JVM, saldos preservados, recibo recuperado sem duplicação, fragmentos offline no login, conferência auditada");
                }
            }catch(Throwable e){finish(false,e.toString());}
        },server);
    }
    private int fragments(FakePlayer player){int n=0;for(ItemStack stack:player.getInventory().items)if(stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)){var tag=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getCompound("shirokaicrates");if(tag.getString("kind").equals("fragment")&&tag.getString("id").equals("shiny1")&&!tag.getString("signature").isEmpty())n+=stack.getCount();}return n;}
    private void finish(boolean passed,String message){
        try{JsonFiles.write(Path.of(second?"rewards-integration-result.json":"rewards-phase1-result.json").toAbsolutePath(),Map.of("passed",passed,"realClients",0,"checks",checks,"message",message));}catch(Exception e){e.printStackTrace();}
        System.out.println("SHIROKAI_MODULAR_REWARDS "+passed+" "+message);server.halt(false);
    }
}
