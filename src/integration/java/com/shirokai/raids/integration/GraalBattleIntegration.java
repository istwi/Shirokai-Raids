package com.shirokai.raids.integration;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.FleeableBattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.shirokai.raids.core.RaidCombatCoordinator;
import kotlin.Unit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Disposable runtime probe. Actors are controlled by the fixture, NOT Minecraft players. */
public final class GraalBattleIntegration implements ModInitializer {
    private static final class ProbeActor extends AIBattleActor implements FleeableBattleActor {
        private final ServerLevel world;
        ProbeActor(Pokemon pokemon,ServerLevel world) {
            super(UUID.randomUUID(), List.of(new BattlePokemon(pokemon, pokemon, p -> Unit.INSTANCE)), new RandomBattleAI());
            this.world=world;
        }
        @Override public float getFleeDistance() { return -1; }
        @Override public kotlin.Pair<ServerLevel,Vec3> getWorldAndPosition() { return new kotlin.Pair<>(world,new Vec3(0,200,0)); }
        @Override public ActorType getType() { return ActorType.WILD; }
        @Override public MutableComponent getName() { return Component.literal("Probe"); }
        @Override public MutableComponent nameOwned(String name) { return Component.literal(name); }
        @Override public void onChoiceRequested() { /* The fixture submits ordinary action responses on ticks. */ }
    }
    private static final class Session {
        final RaidCombatCoordinator.Session token;
        final PokemonBattle battle;
        final ProbeActor player, boss;
        long sequence;
        int turns;
        Session(RaidCombatCoordinator.Session token, PokemonBattle battle, ProbeActor player, ProbeActor boss) {
            this.token=token; this.battle=battle; this.player=player; this.boss=boss;
        }
    }
    private final RaidCombatCoordinator ledger=new RaidCombatCoordinator(UUID.randomUUID(),2000,3);
    private final Map<UUID,Session> sessions=new LinkedHashMap<>();
    private PokemonEntity publicBoss;
    private MinecraftServer server;
    private int ticks, terminalTransitions, hits;
    private boolean finished, departed;

    @Override public void onInitialize() {
        ShowdownInterpreter.registerUpdateInstructionParser("-shirokaiprobe", (battle,set,message,iterator) -> b -> {
            Session session=sessions.get(b.getBattleId());
            if(session==null) throw new IllegalStateException("Evento fora de sessão controlada");
            long amount=Long.parseLong(Objects.requireNonNull(message.argumentAt(1)));
            var result=ledger.applyDamage(session.token,++session.sequence,amount);
            if(result.applied()>0) hits++;
            if(result.terminalTransition()) terminalTransitions++;
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if(finished) return;
            server=s;
            try { tick(); }
            catch(Throwable error) { error.printStackTrace(); complete(false,error.toString()); }
        });
    }
    private static Pokemon pokemon(String properties) {
        return PokemonProperties.Companion.parse(properties," ","=").create();
    }
    private void join() {
        var token=ledger.join(UUID.randomUUID()).orElseThrow();
        var attacker=pokemon("garchomp level=100 moves=dragonclaw");
        require(attacker.sendOut(server.overworld(),new Vec3(8+sessions.size()*3,200,0),null, entity -> {
            entity.setNoAi(true); entity.setNoGravity(true); entity.setPersistenceRequired();
            return Unit.INSTANCE;
        })!=null,"Envio do atacante falhou");
        var player=new ProbeActor(attacker,server.overworld());
        var boss=new ProbeActor(pokemon("rayquaza level=100 moves=splash uncatchable=true"),server.overworld());
        var result=BattleRegistry.startBattle(BattleFormat.Companion.getGEN_9_SINGLES(),
            new BattleSide(player),new BattleSide(boss),false);
        result.ifSuccessful(battle -> {
            var session=new Session(token,battle,player,boss);
            sessions.put(battle.getBattleId(),session);
            // Detached actors never send out extra world entities. Initialize their active slots
            // before the dispatch queue builds the ordinary battle DTOs/requests.
            player.getActivePokemon().getFirst().setBattlePokemon(player.getPokemonList().getFirst());
            boss.getActivePokemon().getFirst().setBattlePokemon(boss.getPokemonList().getFirst());
            battle.writeShowdownAction(">eval battle.dex.data.Conditions.shirokaiprobe = {name:'shirokaiprobe',"+
                "onDamage:function(amount,target){this.add('-shirokaiprobe',target,amount);return 0;}};"+
                "if(!battle.sides[1].active[0].addVolatile('shirokaiprobe'))throw new Error('Probe hook unavailable');");
            return Unit.INSTANCE;
        });
        require(sessions.size()==ledger.snapshot().activeSessions(),"Falha ao criar batalha no registry");
    }
    private void tick() {
        ticks++;
        if(ticks==20) {
            publicBoss=PokemonProperties.Companion.parse("rayquaza level=100 uncatchable=true"," ","=")
                .createEntity(server.overworld());
            publicBoss.setPos(0,200,0); publicBoss.setNoAi(true); publicBoss.setNoGravity(true);
            publicBoss.setPersistenceRequired(); publicBoss.addTag("shirokai_raid_probe_boss");
            require(server.overworld().addFreshEntity(publicBoss),"Spawn da entidade pública falhou");
            join(); join();
        }
        if(publicBoss==null) return;
        if(ticks%100==0) {
            System.out.println("COMBAT_PROBE ticks="+ticks+" hits="+hits+" hp="+ledger.snapshot().health());
            for(var session:sessions.values()) System.out.println("COMBAT_SESSION turn="+session.battle.getTurn()+
                " started="+session.battle.getStarted()+" ended="+session.battle.getEnded()+
                " choose="+session.player.getMustChoose()+","+session.boss.getMustChoose()+
                " request="+(session.player.getRequest()!=null)+","+(session.boss.getRequest()!=null));
        }
        if(ticks==300) require(hits>0,"Gancho não entregou dano ao coordenador em 15 segundos");
        require(publicBoss.getBattleId()==null,"A entidade pública ganhou battleId de uma sessão");
        require(server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-48,150,-48,48,250,48),
            e -> e.getPokemon().getSpecies().getName().equalsIgnoreCase("rayquaza")).size()==1,"Clones físicos do boss apareceram");
        if(hits>=2 && sessions.size()==2 && ledger.snapshot().state()==RaidCombatCoordinator.State.ACTIVE) join();
        if(sessions.size()==3 && ledger.snapshot().damage().size()==3 && !departed) {
            Session leaving=new ArrayList<>(sessions.values()).get(1);
            require(ledger.leave(leaving.token),"Saída falhou");
            leaving.battle.stop(); departed=true;
        }
        if(ledger.snapshot().state()==RaidCombatCoordinator.State.DEFEATED) {
            for(var session:sessions.values()) {
                if(!session.battle.getEnded()) session.battle.stop();
                var entity=session.player.getPokemonList().getFirst().getEntity();
                if(entity!=null) entity.discard();
            }
            require(terminalTransitions==1 && departed,"Derrota/saída não validadas");
            require(ledger.snapshot().damage().size()==3,"Contribuição não preservada");
            require(ledger.snapshot().damage().values().stream().mapToLong(Long::longValue).sum()==2000,"HP/crédito divergiram");
            require(sessions.values().stream().allMatch(session -> session.battle.getEnded()),"Sessão não encerrou");
            complete(true,"3 sessões reais Java/Graal com atores controlados: dano compartilhado, entrada tardia, saída isolada, boss físico único e término único. Sem clientes, UI ou desafio padrão.");
            return;
        }
        if(ticks%20==0) for(var session:new ArrayList<>(sessions.values())) {
            if(!session.battle.getEnded() && session.player.getMustChoose() && session.boss.getMustChoose()
                && session.player.getRequest()!=null && session.boss.getRequest()!=null) {
                require(session.turns++<14,"Batalha esgotou PP sem concluir raid");
                session.player.setActionResponses(List.of(new MoveActionResponse("dragonclaw","p2a",null)));
                session.boss.setActionResponses(List.of(new MoveActionResponse("splash",null,null)));
            }
        }
        require(ticks<2000,"Tempo limite da fixture");
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private void complete(boolean passed,String message) {
        finished=true;
        try {
            for(var session:sessions.values()) if(!session.battle.getEnded()) session.battle.stop();
            if(publicBoss!=null) publicBoss.discard();
            var result=Map.of("passed",passed,"message",message,"minecraft","1.21.1","cobblemon","1.8.3",
                "realClients",0,"controlledSessions",sessions.size(),"effectiveDamage",2000-ledger.snapshot().health());
            Files.writeString(Path.of("combat-integration-result.json"),new com.google.gson.Gson().toJson(result));
            System.out.println("SHIROKAI_COMBAT_INTEGRATION "+result);
        } catch(Exception error) { throw new IllegalStateException(error); }
        server.halt(false);
    }
}
