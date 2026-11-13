package com.shirokai.raids;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import com.shirokai.raids.core.RaidDamageTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Native private PvE capture battle. Timer fast-forwards exercise the production wall-clock checks. */
public final class CaptureIntegration implements ModInitializer {
    private int ticks, checks, stage;
    private boolean sawFailedShake;
    private static final class Probe extends ServerPlayer {
        final List<String> messages=new ArrayList<>();
        Probe(MinecraftServer server,GameProfile profile) { super(server,server.overworld(),profile,ClientInformation.createDefault()); }
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component text) { messages.add(text.getString());super.sendSystemMessage(text); }
    }
    private ServerPlayer player, other;
    private RaidCapture manager;
    private RaidState state;
    private RaidState.CaptureGrant grant;
    private EmptyPokeBallEntity flight, deadlineFlight;
    private RaidState.CaptureGrant deadlineGrant;
    private int finishAt;
    private RaidState.CaptureGrant exhaustedGrant;
    private EmptyPokeBallEntity exhaustedBall;
    private net.minecraft.server.level.ServerBossEvent exhaustedBar;
    private Map<UUID,ServerPlayer> lookup;
    private long victory;
    private int initialHealth, initialPP;
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(stage==9) return;
            try {
                ticks++;
                if(ticks==20) setup(server);
                if(stage==10 && ticks%20==0) {
                    if(BattleRegistry.getBattleByParticipatingPlayer(player)==null) pressR(server,player);
                    if(BattleRegistry.getBattleByParticipatingPlayer(other)==null) pressR(server,other);
                }
                if(stage==8 && ticks%20==0 && BattleRegistry.getBattleByParticipatingPlayer(other)==null) pressR(server,other);
                if(stage==10 && ready(player)) {
                    initialHealth=target(server,player).getPokemon().getCurrentHealth();
                    var party=Cobblemon.INSTANCE.getStorage().getParty(player);
                    initialPP=party.get(0).getMoveSet().getMoves().stream().filter(m->m.getName().equals("falseswipe")).findFirst().orElseThrow().getCurrentPp();
                    ((PlayerBattleActor)BattleRegistry.getBattleByParticipatingPlayer(player).getSide1().getActors()[0]).setActionResponses(List.of(new com.cobblemon.mod.common.battles.MoveActionResponse("falseswipe","p2a",null)));
                    stage=11;
                }
                if(stage==11 && ready(player) && target(server,player).getPokemon().getCurrentHealth()<initialHealth) {
                    require(target(server,player).getPokemon().getCurrentHealth()>0,"Movimento derrotou a cópia de captura");
                    var live=(com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService)com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService();
                    int pp=live.getContext().eval("js","battleMap.get('"+BattleRegistry.getBattleByParticipatingPlayer(player).getBattleId()+"').battle.sides[0].pokemon[0].moveSlots.find(m=>m.id==='falseswipe').pp").asInt();
                    require(pp<initialPP,"Movimento de captura não consumiu PP no motor: inicial="+initialPP+", atual="+pp);
                    stage=1;
                }
                if(stage==1 && ready(player)) throwFirst(server);
                if(stage==2 && flight.getCaptureState()==EmptyPokeBallEntity.CaptureState.SHAKE) sawFailedShake=true;
                if(stage==2 && flight.getCaptureFuture().isDone()) afterFailure(server);
                if(stage==3 && ready(player)) throwSecond(server);
                if(stage==4 && flight.getCaptureFuture().isDone()) { require(flight.getCaptureFuture().join(),"Captura de 100% falhou");stage=5; }
                else if(stage==5 && !manager.active(player.getUUID()) && ready(other)) verify(server);
                if(stage==6 && deadlineGrant.resolved) {
                    manager.tick(victory+119999);require(manager.active(other.getUUID()),"Captura encerrou antes dos dois minutos");
                    manager.tick(victory+120000);
                    require(!manager.active(other.getUUID())&&BattleRegistry.getBattleByParticipatingPlayer(other)==null,"Prazo não encerrou a batalha em captura");
                    require(!deadlineFlight.getCaptureFuture().join(),"Prazo não cancelou a tentativa em animação");
                    finishAt=ticks+200;stage=7;
                }
                if(stage==7 && ticks>=finishAt) verifyDeadlineAndReturn(server);
                if(stage==8 && ready(other)) {
                    manager.throwBall(other,InteractionHand.MAIN_HAND);exhaustedBall=ball(server,other);
                    hit(exhaustedBall,target(server,other));acknowledgeCapture(server,other);stage=13;
                }
                if(stage==13 && !manager.active(other.getUUID())) {
                    require(!exhaustedBall.getCaptureFuture().join(),"Última bola deveria falhar no controle negativo");
                    require(((Probe)other).messages.contains(RaidMessages.chat(RaidMessages.msg("captura.esgotadas","boss",exhaustedGrant.boss.name)).getString()),"Chat não informou que as bolas acabaram");
                    require(exhaustedBar.getPlayers().isEmpty(),"BossBar permaneceu após esgotar as bolas");
                    complete(server,true,"R manual, cópia brilhante privada, BossBar individual, animação de falha, sucesso/esgotamento/timeout no chat, prazos e entrada validados.");
                }
                if(ticks%100==0 && player!=null) {
                    var battle=BattleRegistry.getBattleByParticipatingPlayer(player);
                    System.out.println("CAPTURE_BATTLE_DIAGNOSTIC stage="+stage+" messages="+((Probe)player).messages+" active="+manager.active(player.getUUID())+" battle="+battle+" ball="+(flight==null?"none":flight.getCaptureState()+"/removed="+flight.isRemoved()+"/ticks="+flight.tickCount+"/target="+flight.getCapturingPokemon()));
                    if(battle!=null) {
                        System.out.println("CAPTURE_DISPATCH "+battle.getDispatchResult()+" queue="+battle.getDispatches().size());
                        var npc=battle.getSide2().getActors()[0];System.out.println("CAPTURE_NPC must="+npc.getMustChoose()+" req="+npc.getRequest()+" active="+npc.getActivePokemon()+" moves="+npc.getPokemonList().getFirst().getEffectedPokemon().getMoveSet().getMoves());
                        var graal=(com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService)com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService();
                        System.out.println("CAPTURE_ENGINE "+graal.getContext().eval("js","JSON.stringify((b=>({turn:b.turn,request:b.requestState,sides:b.sides.map(s=>({error:s.choice.error,actions:s.choice.actions.map(a=>a.choice),hp:s.active[0]?.hp}))}))(battleMap.get('"+battle.getBattleId()+"').battle))"));
                        var actor=(PlayerBattleActor)battle.getSide1().getActors()[0];System.out.println("CAPTURE_BATTLE_DIAGNOSTIC ended="+battle.getEnded()+" mustChoose="+actor.getMustChoose()+" request="+actor.getRequest()+" active="+actor.getActivePokemon()); }
                }
                if(ticks>1800) throw new AssertionError("Timeout da batalha de captura, stage="+stage);
            } catch(Throwable error) { error.printStackTrace(); complete(server,false,error.toString()); }
        });
    }
    @SuppressWarnings("unchecked")
    private ServerPlayer player(MinecraftServer server,String name) throws Exception {
        if(lookup==null) for(var field:PlayerList.class.getDeclaredFields())
            if(field.getGenericType() instanceof ParameterizedType type && type.getRawType()==Map.class
                && Arrays.equals(type.getActualTypeArguments(),new Type[]{UUID.class,ServerPlayer.class})) {
                field.setAccessible(true);lookup=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());break;
            }
        var p=new Probe(server,new GameProfile(UUID.randomUUID(),name));
        p.connection=FakePlayer.get(server.overworld(),new GameProfile(UUID.randomUUID(),"ConnectionProbe")).connection;
        p.connection.player=p;
        p.setPos(8,200,8);lookup.put(p.getUUID(),p);server.getPlayerList().getPlayers().add(p);
        var pokemon=PokemonProperties.Companion.parse("mewtwo level=100 moves=falseswipe").create();
        Cobblemon.INSTANCE.getStorage().getParty(p).add(pokemon);
        pokemon.sendOut(server.overworld(),p.position().add(0,0,2),null,e -> kotlin.Unit.INSTANCE);
        return p;
    }
    private void setup(MinecraftServer server) throws Exception {
        server.overworld().setChunkForced(0,0,true);
        for(int x=-25;x<=25;x++) for(int z=-25;z<=25;z++) server.overworld().setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3);
        require(server.getCommands().getDispatcher().getRoot().getChild("raid").getChild("capturar")==null,"Comando removido ainda registrado");
        var legacy=FakePlayer.get(server.overworld(),new GameProfile(UUID.randomUUID(),"LegacyProbe"));
        legacy.getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));
        var old=new net.minecraft.nbt.CompoundTag();old.putInt("Version",1);old.putInt("Selected",0);old.put("Items",legacy.getInventory().save(new net.minecraft.nbt.ListTag()));
        legacy.getInventory().setItem(0,ItemStack.EMPTY);((RaidInventoryAccess)legacy).shirokaiInventory(old);
        RaidLegacyInventory.recover(legacy);
        require(legacy.getInventory().getItem(0).is(Items.DIAMOND)&&legacy.getInventory().getItem(0).getCount()==3&&!RaidLegacyInventory.pending(legacy),"Migração do build anterior perdeu itens");
        player=player(server,"CaptureProbe");other=player(server,"OtherProbe");player.setPos(0,200,2.5);other.setPos(0,200,-2.5);
        player.getInventory().armor.set(0,new ItemStack(Items.DIAMOND_BOOTS));
        require(RaidInventory.emptyForEntry(player),"Armadura bloqueou entrada");
        var savedPlayer=new net.minecraft.nbt.CompoundTag();player.addAdditionalSaveData(savedPlayer);
        require(!savedPlayer.contains(RaidLegacyInventory.KEY),"Nova entrada criou backup de inventário");
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
        require(!RaidInventory.emptyForEntry(player),"Inventário com item aceito");
        boolean denied=false;try{RaidInventory.requireEmpty(player);}catch(IllegalStateException expected){denied=true;}
        require(denied&&player.getInventory().getItem(0).is(Items.DIAMOND),"Validação removeu itens pessoais");
        player.getInventory().setItem(0,ItemStack.EMPTY);player.getInventory().offhand.set(0,new ItemStack(Items.SHIELD));
        require(!RaidInventory.emptyForEntry(player),"Mão secundária não foi validada");player.getInventory().offhand.set(0,ItemStack.EMPTY);
        manager=ShirokaiRaids.captureManager();var field=RaidCapture.class.getDeclaredField("state");field.setAccessible(true);state=(RaidState)field.get(manager);
        var config=new RaidConfig();config.spawnConfigured=true;config.x=0;config.y=200;config.z=0;
        var tracker=new RaidDamageTracker();var absent=UUID.randomUUID();
        tracker.record(player.getUUID(),"Probe",UUID.randomUUID(),1000,0);
        tracker.record(absent,"Offline",UUID.randomUUID(),750,0);
        tracker.record(other.getUUID(),"Other",UUID.randomUUID(),500,0);
        String raid=UUID.randomUUID().toString();victory=System.currentTimeMillis();
        for(var p:List.of(player,other)) { var origin=new RaidState.ReturnPosition("minecraft:overworld",1000,200,1000,0,0);origin.raid=raid;state.returns.put(p.getUUID().toString(),origin); }
        var definition=new RaidConfig.Boss();definition.level=50; // Keep the native capture test deterministic; the world-boss combat suite covers its own level/damage.
        manager.award(raid,definition,config,tracker.ranking(),Set.of(player.getUUID(),other.getUUID()),victory);
        require(state.captures.size()==2&&state.captures.values().stream().noneMatch(g->g.player.equals(absent.toString())),"Jogador ausente recebeu captura");
        grant=state.captures.values().stream().filter(g->g.player.equals(player.getUUID().toString())).findFirst().orElseThrow();
        require(grant.remaining==5&&grant.expiresAt==victory+120000,"Quantidade ou prazo a partir da vitória incorretos");
        manager.award(raid,definition,config,tracker.ranking(),Set.of(player.getUUID(),other.getUUID()),victory);
        require(state.captures.size()==2,"Direito duplicado");
        var rng=RaidCapture.class.getDeclaredField("random");rng.setAccessible(true);((Random)rng.get(manager)).setSeed(7);
        manager.start(player);manager.start(other);
        require(manager.active(player.getUUID())&&manager.active(other.getUUID()),"Sessões não iniciadas");
        require(target(server,player).broadcastToPlayer(player)&&!target(server,player).broadcastToPlayer(other),"Cópia não é privada");
        require(player.getInventory().armor.get(0).is(Items.DIAMOND_BOOTS),"Armadura foi removida");
        require(BattleRegistry.getBattleByParticipatingPlayer(player)==null&&BattleRegistry.getBattleByParticipatingPlayer(other)==null,"Cópia iniciou batalha sem R");
        require(target(server,player).isCurrentlyGlowing()&&target(server,player).isCustomNameVisible(),"Cópia sem brilho ou nome visível");
        require(target(server,player).getTeam()!=null && target(server,player).getTeam().getColor()==net.minecraft.ChatFormatting.AQUA,"Cópia sem contorno ciano");
        var ownerBar=bar(player);var otherBar=bar(other);
        require(ownerBar.getPlayers().contains(player)&&!ownerBar.getPlayers().contains(other)&&otherBar.getPlayers().contains(other),"BossBar não é individual");
        manager.tick(victory+60000);require(Math.abs(ownerBar.getProgress()-.5f)<.001,"BossBar não acompanha o prazo da vitória");
        server.overworld().setBlock(new BlockPos(0,201,1),Blocks.STONE.defaultBlockState(),3);
        pressR(server,player);require(BattleRegistry.getBattleByParticipatingPlayer(player)==null,"R atravessou uma parede");
        server.overworld().setBlock(new BlockPos(0,201,1),Blocks.AIR.defaultBlockState(),3);
        pressR(server,player);pressR(server,other);
        stage=10;
    }
    private boolean ready(ServerPlayer p) {
        var battle=BattleRegistry.getBattleByParticipatingPlayer(p);
        return battle!=null&&!battle.getEnded()&&battle.getSide1().getActors()[0] instanceof PlayerBattleActor actor&&actor.canFitForcedAction();
    }
    private void throwFirst(MinecraftServer server) throws Exception {
        var battle=BattleRegistry.getBattleByParticipatingPlayer(player);
        require(battle!=null&&battle!=BattleRegistry.getBattleByParticipatingPlayer(other),"Batalhas individuais não foram iniciadas");
        require(battle.getSide2().getActors()[0].getPokemonList().getFirst().getUuid().equals(target(server,player).getPokemon().getUuid()),"Batalha não usa sua cópia");
        manager.throwBall(player,InteractionHand.MAIN_HAND);flight=ball(server,player);
        require(grant.remaining==4,"Tentativa não debitada");hit(flight,target(server,player));acknowledgeCapture(server,player);System.out.println("CAPTURE_HIT state="+flight.getCaptureState()+" removed="+flight.isRemoved()+" targetBattle="+target(server,player).getBattleId());stage=2;
    }
    private void afterFailure(MinecraftServer server) {
        require(sawFailedShake,"Falha não apresentou animação de shake");
        require(!flight.getCaptureFuture().join()&&!grant.resolved&&grant.remaining==4,"Falha não respeitou chance/orçamento");
        grant.chance=1;stage=3;
    }
    private void throwSecond(MinecraftServer server) throws Exception {
        manager.throwBall(player,InteractionHand.MAIN_HAND);
        var balls=server.overworld().getEntitiesOfClass(EmptyPokeBallEntity.class,new AABB(-50,180,-50,50,240,50),e->e.getOwner()==player&&e.getTags().contains(RaidCapture.BALL_TAG));
        if(balls.isEmpty()) return;
        flight=balls.getFirst();hit(flight,target(server,player));acknowledgeCapture(server,player);stage=4;
    }
    private void verify(MinecraftServer server) throws Exception {
        require(Cobblemon.INSTANCE.getStorage().getParty(player).get(0).getMoveSet().getMoves().stream().filter(m->m.getName().equals("falseswipe")).findFirst().orElseThrow().getCurrentPp()<initialPP,"Encerrar a captura restaurou PP gastos");
        require(((Probe)player).messages.contains(RaidMessages.chat(RaidMessages.msg("captura.sucesso","boss",grant.boss.name)).getString()),"Chat não informou captura bem-sucedida");
        require(!state.captures.containsKey(grant.id),"Direito permaneceu após sucesso");
        require(player.getInventory().items.stream().allMatch(ItemStack::isEmpty),"Bolas restantes não foram removidas");
        require(player.getInventory().armor.get(0).is(Items.DIAMOND_BOOTS),"Armadura perdida após captura");
        int count=0;for(var pokemon:Cobblemon.INSTANCE.getStorage().getParty(player)) if(pokemon.getSpecies().getResourceIdentifier().getPath().equals("rayquaza")) count++;
        require(count==1,"Captura não inseriu exatamente um Pokémon: "+count);
        require(BattleRegistry.getBattleByParticipatingPlayer(other)!=null,"Captura encerrou batalha de outro jogador");
        deadlineGrant=state.captures.values().stream().filter(g->g.player.equals(other.getUUID().toString())).findFirst().orElseThrow();
        deadlineGrant.chance=1;
        manager.throwBall(other,InteractionHand.MAIN_HAND);deadlineFlight=ball(server,other);
        hit(deadlineFlight,target(server,other));acknowledgeCapture(server,other);stage=6;
    }
    private void verifyDeadlineAndReturn(MinecraftServer server) throws Exception {
        require(state.captures.values().stream().noneMatch(g->g.player.equals(other.getUUID().toString())),"Timeout deixou chance pendente");
        int caught=0;for(var pokemon:Cobblemon.INSTANCE.getStorage().getParty(other)) if(pokemon.getSpecies().getResourceIdentifier().getPath().equals("rayquaza")) caught++;
        require(caught==0,"Animação concluiu captura depois do prazo");
        require(((Probe)other).messages.contains(RaidMessages.chat(RaidMessages.msg("captura.tempo_esgotado","boss",deadlineGrant.boss.name)).getString()),"Chat não informou tempo esgotado");
        boolean denied=false;try{manager.start(other);}catch(IllegalStateException expected){denied=true;}
        require(denied,"Timeout permitiu retomar captura");
        var exit=new RaidState.ArenaExit();exit.deadline=victory+600000;exit.dimension="minecraft:overworld";exit.y=200;exit.radius=48;state.arenaExits.put(player.getUUID().toString(),exit);
        var f=ShirokaiRaids.class.getDeclaredField("instance");f.setAccessible(true);var mod=f.get(null);
        var method=ShirokaiRaids.class.getDeclaredMethod("returnExpiredPlayers",long.class);method.setAccessible(true);
        method.invoke(mod,victory+599999);require(player.getX()!=1000,"Retorno aconteceu antes de 10 minutos");
        method.invoke(mod,victory+600000);require(player.getX()==1000&&player.getZ()==1000&&!state.returns.containsKey(player.getUUID().toString()),"Retorno não restaurou a origem aos 10 minutos");
        require(flight.spawnAtLocation(Items.APPLE)==null,"Projétil reembolsou item");
        var config=new RaidConfig();config.x=0;config.y=200;config.z=0;config.capture.firstPlaceBalls=1;
        var definition=new RaidConfig.Boss();definition.level=50;
        String raid=UUID.randomUUID().toString();state.returns.get(other.getUUID().toString()).raid=raid;
        var damage=new RaidDamageTracker();damage.record(other.getUUID(),"Other",UUID.randomUUID(),1000,0);
        manager.award(raid,definition,config,damage.ranking(),Set.of(other.getUUID()),System.currentTimeMillis());
        exhaustedGrant=state.captures.values().stream().filter(g->g.player.equals(other.getUUID().toString())).findFirst().orElseThrow();
        var rng=RaidCapture.class.getDeclaredField("random");rng.setAccessible(true);((Random)rng.get(manager)).setSeed(7);
        manager.start(other);exhaustedBar=bar(other);pressR(server,other);stage=8;
    }
    private net.minecraft.server.level.ServerBossEvent bar(ServerPlayer owner) throws Exception {
        var field=RaidCapture.class.getDeclaredField("sessions");field.setAccessible(true);var session=((Map<?,?>)field.get(manager)).get(owner.getUUID());
        var b=session.getClass().getDeclaredField("bar");b.setAccessible(true);return (net.minecraft.server.level.ServerBossEvent)b.get(session);
    }
    private void pressR(MinecraftServer server,ServerPlayer owner) {
        var selected=Cobblemon.INSTANCE.getStorage().getParty(owner).get(0).getUuid();
        var target=target(server,owner);
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,target.getBoundingBox().getCenter());
        com.cobblemon.mod.common.net.serverhandling.ChallengeHandler.INSTANCE.handle(new com.cobblemon.mod.common.net.messages.server.BattleChallengePacket(target.getId(),selected,com.cobblemon.mod.common.battles.BattleFormat.Companion.getGEN_9_SINGLES()),server,owner);
    }
    private void acknowledgeCapture(MinecraftServer server,ServerPlayer owner) {
        var battle=BattleRegistry.getBattleByParticipatingPlayer(owner);
        var actor=(PlayerBattleActor)battle.getSide1().getActors()[0];
        require(!actor.getExpectingPassActions().isEmpty(),"Lançamento não usou o protocolo de turno nativo");
        // A vanilla Cobblemon client answers BattleApplyPassResponsePacket with this standard packet.
        com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(
            new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),List.of(new com.cobblemon.mod.common.battles.ForcePassActionResponse())),server,owner);
    }
    private static void hit(EmptyPokeBallEntity ball,PokemonEntity target) throws Exception {
        var method=Arrays.stream(EmptyPokeBallEntity.class.getDeclaredMethods()).filter(m->m.getParameterCount()==1&&m.getParameterTypes()[0]==EntityHitResult.class).findFirst().orElseThrow();
        method.setAccessible(true);method.invoke(ball,new EntityHitResult(target));
    }
    private PokemonEntity target(MinecraftServer server,ServerPlayer owner) {
        return server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-50,180,-50,50,240,50),e->e.getTags().contains(RaidCapture.OWNER_TAG+owner.getUUID())).getFirst();
    }
    private EmptyPokeBallEntity ball(MinecraftServer server,ServerPlayer owner) {
        return server.overworld().getEntitiesOfClass(EmptyPokeBallEntity.class,new AABB(-50,180,-50,50,240,50),e->e.getOwner()==owner&&e.getTags().contains(RaidCapture.BALL_TAG)).getFirst();
    }
    private void require(boolean condition,String message) { if(!condition) throw new AssertionError(message);checks++; }
    private void complete(MinecraftServer server,boolean passed,String message) {
        stage=9;
        try {
            if(manager!=null) manager.shutdown();
            for(var p:Arrays.asList(player,other)) if(p!=null) {lookup.remove(p.getUUID());server.getPlayerList().getPlayers().remove(p);}
            Files.writeString(Path.of("capture-integration-result.json"),new com.google.gson.Gson().toJson(Map.of("passed",passed,"checks",checks,"message",message,"realClients",0)));
        }catch(Exception error){throw new RuntimeException(error);}
        server.overworld().setChunkForced(0,0,false);server.halt(false);
    }
}
