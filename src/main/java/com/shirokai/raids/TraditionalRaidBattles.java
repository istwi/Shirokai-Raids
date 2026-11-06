package com.shirokai.raids;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.*;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.shirokai.raids.core.RaidCombatCoordinator;
import kotlin.Pair;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;
import org.graalvm.polyglot.HostAccess;

/** Server-side adapter. Uses only ordinary Cobblemon battle packets on the client. */
public final class TraditionalRaidBattles {
    public record Hit(UUID player,String name,UUID pokemon,long applied) {}
    private static final Map<UUID,TraditionalRaidBattles> ROUTES=new HashMap<>();
    private static boolean registered;
    private static final String INSTALL_SCRIPT=installScript();
    private static String installScript() {
        try(var stream=Objects.requireNonNull(TraditionalRaidBattles.class.getResourceAsStream("/shirokai/raid-battle.js"))) {
            return new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace('\n',' ').replace('\r',' ');
        } catch(java.io.IOException error) { throw new IllegalStateException("Adaptador Showdown ausente",error); }
    }
    public static final class EngineCallbacks {
        @HostAccess.Export public long remaining(String id) {
            var owner=ROUTES.get(UUID.fromString(id));
            return owner==null?0:owner.health.current();
        }
        @HostAccess.Export public long damage(String id,long sequence,long requested) {
            UUID battle=UUID.fromString(id);
            var owner=ROUTES.get(battle);
            if(owner==null) return 0;
            var session=owner.sessions.get(battle);
            return session==null?0:owner.damage(session.battle(),sequence,requested);
        }
        @HostAccess.Export public void pp(String id,String pokemon,String move,int pp) {
            UUID battle=UUID.fromString(id);
            var owner=ROUTES.get(battle);
            if(owner==null) return;
            var session=owner.sessions.get(battle);
            if(session!=null) owner.updatePP(session.battle(),UUID.fromString(pokemon),move,pp);
        }
    }
    private static final EngineCallbacks ENGINE_CALLBACKS=new EngineCallbacks();
    private static final class BossActor extends AIBattleActor implements EntityBackedBattleActor<PokemonEntity>, FleeableBattleActor {
        private final PokemonEntity anchor;
        BossActor(Pokemon pokemon,PokemonEntity anchor) {
            super(pokemon.getUuid(),List.of(new BattlePokemon(pokemon,pokemon,p -> Unit.INSTANCE)),new RandomBattleAI());
            this.anchor=anchor;
        }
        // Position anchor for normal send-outs. Scoped mixins prevent a second boss spawn;
        // BattlePokemon.entity remains null, so startShowdown never assigns it a battleId.
        @Override public PokemonEntity getEntity() { return anchor; }
        @Override public Vec3 getInitialPos() { return anchor.position(); }
        @Override public Pair<ServerLevel,Vec3> getWorldAndPosition() { return new Pair<>((ServerLevel)anchor.level(),anchor.position()); }
        @Override public float getFleeDistance() { return 64; }
        @Override public ActorType getType() { return ActorType.WILD; }
        @Override public MutableComponent getName() { return anchor.getDisplayName().copy(); }
        @Override public MutableComponent nameOwned(String name) { return Component.literal(name); }
    }
    private record Session(RaidCombatCoordinator.Session token,ServerPlayer player,PokemonBattle battle,BossActor boss) {}
    private final PokemonEntity anchor;
    private final RaidCombatCoordinator health;
    private final Consumer<Hit> onHit;
    private final Map<UUID,Session> sessions=new LinkedHashMap<>();
    private long lastHealth=-1;

    public TraditionalRaidBattles(PokemonEntity anchor,RaidCombatCoordinator health,Consumer<Hit> onHit) {
        this.anchor=anchor; this.health=health; this.onHit=onHit;
    }
    public static boolean isBossActor(BattleActor actor) { return actor instanceof BossActor; }
    public static void register() {
        if(registered) return;
        registered=true;
        CobblemonEvents.BATTLE_ENDED.subscribe(event -> {
            var owner=ROUTES.get(event.getBattle().getBattleId());
            if(owner!=null) owner.remove(event.getBattle().getBattleId(),false);
        });
    }
    public void challenge(ServerPlayer player,UUID selected) {
        if(BattleRegistry.getBattleByParticipatingPlayer(player)!=null || PlayerExtensionsKt.isPartyBusy(player))
            throw new IllegalStateException("Encerre sua batalha atual primeiro.");
        var party=PlayerExtensionsKt.party(player);
        var lead=party.get(selected);
        if(lead==null || lead.getCurrentHealth()<=0) throw new IllegalStateException("Selecione um Pokémon saudável da sua equipe.");
        var team=party.toBattleTeam(false,false,selected).stream().filter(p -> p.getHealth()>0).toList();
        if(team.isEmpty() || team.stream().anyMatch(p -> p.getEntity()!=null && p.getEntity().isBusy()))
            throw new IllegalStateException("Sua equipe está ocupada ou indisponível.");
        var token=health.join(player.getUUID()).orElseThrow(() -> new IllegalStateException("Raid lotada, encerrada ou você já está participando."));
        boolean[] started={false};
        try {
            if(!(ShowdownService.Companion.getService() instanceof GraalShowdownService graal))
                throw new IllegalStateException("Esta build requer o simulador Graal padrão do Cobblemon 1.8.3.");
            graal.getContext().getBindings("js").putMember("shirokaiRaidBridge",ENGINE_CALLBACKS);
            var copy=anchor.getPokemon().clone(true,player.server.registryAccess());
            copy.setState(new InactivePokemonState());
            var actor=new BossActor(copy,anchor);
            var result=BattleRegistry.startBattle(BattleFormat.Companion.getGEN_9_SINGLES(),
                new BattleSide(new PlayerBattleActor(player.getUUID(),team)),new BattleSide(actor),true);
            result.ifSuccessful(battle -> {
                started[0]=true;
                sessions.put(battle.getBattleId(),new Session(token,player,battle,actor));
                ROUTES.put(battle.getBattleId(),this);
                actor.getActivePokemon().getFirst().setBattlePokemon(actor.getPokemonList().getFirst());
                // Static code only. No configuration/player text is interpolated into the engine.
                // Reserve the effective damage at the native application point, after normal
                // accuracy/type/ability processing. Returning it preserves native drain/recoil.
                // Only this session's boss instance has its HP reduction replaced by the ledger.
                battle.writeShowdownAction(">eval "+INSTALL_SCRIPT+"(battle,'"+battle.getBattleId()+"',shirokaiRaidBridge);");
                sync();
                return Unit.INSTANCE;
            });
            if(!started[0]) throw new IllegalStateException("O Cobblemon recusou a batalha.");
        } catch(RuntimeException error) {
            if(!started[0]) health.leave(token);
            else leave(player.getUUID());
            throw error;
        }
    }
    private long damage(PokemonBattle battle,long sequence,long requested) {
        var session=sessions.get(battle.getBattleId());
        if(session==null) return 0;
        var result=health.applyDamage(session.token(),sequence,requested);
        if(result.applied()>0) {
            var active=battle.getSide1().getActivePokemon().getFirst().getBattlePokemon();
            if(active!=null) onHit.accept(new Hit(session.player().getUUID(),session.player().getGameProfile().getName(),active.getUuid(),result.applied()));
        }
        return result.applied();
    }
    public void tick() {
        for(var session:new ArrayList<>(sessions.values())) {
            if(session.battle().getEnded() || session.player().hasDisconnected() || session.player().level()!=anchor.level())
                remove(session.battle().getBattleId(),true);
        }
        if(health.current()!=lastHealth) sync();
    }
    private void sync() {
        lastHealth=health.current();
        float fraction=(float)health.fraction();
        for(var session:new ArrayList<>(sessions.values())) {
            if(session.battle().getEnded()) continue;
            var pokemon=session.boss().getPokemonList().getFirst().getEffectedPokemon();
            int hp=Math.max(1,(int)Math.ceil(pokemon.getMaxHealth()*fraction));
            pokemon.setCurrentHealth(hp);
            session.battle().writeShowdownAction(">eval battle.sides[1].active[0].hp="+hp+";");
            session.battle().sendSidedUpdate(session.boss(),new BattleHealthChangePacket("p2a",hp,null),
                new BattleHealthChangePacket("p2a",fraction,null),false);
        }
    }
    public void leave(UUID player) {
        for(var session:new ArrayList<>(sessions.values())) if(session.player().getUUID().equals(player)) remove(session.battle().getBattleId(),true);
    }
    private void updatePP(PokemonBattle battle,UUID pokemon,String id,int pp) {
        var session=sessions.get(battle.getBattleId());
        if(session==null) return;
        var target=battle.getSide1().getActors()[0].getPokemonList().stream()
            .filter(p -> p.getUuid().equals(pokemon)).findFirst().orElse(null);
        if(target==null) return;
        for(var move:target.getEffectedPokemon().getMoveSet().getMoves())
            if(move.getName().equalsIgnoreCase(id) && pp>=0 && pp<=move.getMaxPp()) move.setCurrentPp(pp);
    }
    private void remove(UUID battle,boolean stop) {
        var session=sessions.get(battle);
        if(session==null) return;
        // stop() ends Java immediately; its normal queued pp_update can otherwise be lost.
        // Read actual slots while the stream still exists, including BATTLE_ENDED/disconnect.
        session.battle().writeShowdownAction(">eval for(const p of battle.sides[0].pokemon)"+
            "for(const m of p.moveSlots)shirokaiRaidBridge.pp('"+battle+"',p.uuid,m.id,m.pp);");
        sessions.remove(battle); ROUTES.remove(battle);
        health.leave(session.token());
        if(stop && !session.battle().getEnded()) session.battle().stop();
    }
    public void close() {
        health.cancel();
        for(UUID battle:new ArrayList<>(sessions.keySet())) remove(battle,true);
    }
}
