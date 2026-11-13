package com.shirokai.raids;

import static com.shirokai.raids.RaidMessages.msg;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.shirokai.raids.core.RaidDamageTracker;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import java.util.*;

/** Native Cobblemon capture, with an authoritative, durable per-player throw budget. */
public final class RaidCapture {
    public static final String BALL_TAG = "shirokai_raid_throw";
    public static final String OWNER_TAG = "shirokai_capture_owner:";
    public static final String TARGET_TAG = "shirokai_raid_capture";
    enum Outcome {
        CAPTURED("captura.sucesso"), NO_BALLS("captura.esgotadas"), TIMEOUT("captura.tempo_esgotado"),
        LEFT("captura.saiu"), BATTLE_ENDED("captura.derrota"), CANCELLED("captura.cancelada");
        final String message;
        Outcome(String message) { this.message=message; }
    }
    static CaptureContext animation(boolean success) { return new CaptureContext(3,success,false); }
    private static final String ITEM_KEY = "shirokai_capture_grant";
    private final MinecraftServer server;
    private final RaidState state;
    private final Runnable persist;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Random random = new Random();
    private static final class Session {
        final ServerPlayer player;
        final RaidState.CaptureGrant grant;
        final PokemonEntity target;
        final RaidChunkLease chunks;
        final long ends;
        final net.minecraft.server.level.ServerBossEvent bar;
        Outcome result;
        long finishAt;
        PokemonBattle battle;
        boolean starting;
        EmptyPokeBallEntity flight;
        long nextThrow;
        Session(ServerPlayer player, RaidState.CaptureGrant grant, PokemonEntity target, long now) {
            this.player=player; this.grant=grant; this.target=target; ends=grant.expiresAt;
            bar=new net.minecraft.server.level.ServerBossEvent(net.minecraft.network.chat.Component.empty(),net.minecraft.world.BossEvent.BossBarColor.BLUE,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            bar.addPlayer(player);
            chunks=new RaidChunkLease(player.server.getLevel(target.level().dimension()),new net.minecraft.world.level.ChunkPos(target.blockPosition()),grant.radius);
        }
    }
    RaidCapture(MinecraftServer server, RaidState state, Runnable persist) {
        this.server=server; this.state=state; this.persist=persist;
    }
    static boolean isBall(ItemStack stack) {
        var data=stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(ITEM_KEY);
    }
    static boolean protectedEntity(Entity entity) { return entity.getTags().contains(TARGET_TAG); }
    static boolean locked(net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer p && RaidInventory.locked(p);
    }
    static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack=player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.pass(stack);
            if (!isBall(stack) && !RaidInventory.locked(p)) return InteractionResultHolder.pass(stack);
            if (isBall(stack)) ShirokaiRaids.throwCaptureBall(p, hand);
            return InteractionResultHolder.fail(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, face) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(event -> {
            var manager=ShirokaiRaids.captureManager();
            if (protectedEntity(event.getPokemon()) || event.getPokeBall().getTags().contains(BALL_TAG)) {
                if (manager == null || !manager.allowed(event.getPokeBall(), event.getPokemon())) {
                    event.cancel(); event.getPokeBall().discard();
                }
            }
        });
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(event -> {
            if (!protectedEntity(event.getPokemonEntity()) && !event.getPokeBallEntity().getTags().contains(BALL_TAG)) return;
            var manager=ShirokaiRaids.captureManager();
            event.setCaptureResult(new CaptureContext(0,false,false));
            boolean success = manager != null && manager.decide(event.getPokeBallEntity(), event.getPokemonEntity());
            event.setCaptureResult(animation(success));
        });
    }
    void award(String raid, RaidConfig.Boss boss, RaidConfig config, List<RaidDamageTracker.Entry> ranking, Set<UUID> present, long victoryTime) {
        if (!config.capture.enabled) return;
        int rank=0;
        for (var entry : ranking) {
            rank++;
            if (!present.contains(entry.player()) || entry.damage() <= 0 || state.captures.values().stream().anyMatch(g -> g.raid.equals(raid) && g.player.equals(entry.player().toString()))) continue;
            var grant=new RaidState.CaptureGrant();
            grant.id=UUID.randomUUID().toString(); grant.raid=raid; grant.player=entry.player().toString();
            grant.boss=JsonFiles.copy(boss, RaidConfig.Boss.class);
            grant.arena=new RaidState.ReturnPosition(config.dimension, config.x, config.y, config.z, config.spawnYaw, config.spawnPitch);
            grant.radius=config.arenaRadius; grant.remaining=config.capture.ballsForRank(rank);
            grant.durationSeconds=120; grant.expiresAt=victoryTime+120_000L; grant.chance=config.capture.chancePerThrow;
            state.captures.put(grant.id,grant);
        }
    }
    boolean anyActive() { return !sessions.isEmpty(); }
    boolean active(UUID player) { return sessions.containsKey(player); }
    boolean hasGrant(ServerPlayer player) {
        return state.captures.values().stream().anyMatch(g -> g.player.equals(player.getUUID().toString()) && !g.resolved && g.remaining > 0);
    }
    void start(ServerPlayer player) {
        if (active(player.getUUID())) throw new IllegalStateException(msg("captura.ja_ativa"));
        var grant=state.captures.values().stream().filter(g -> g.player.equals(player.getUUID().toString()) && !g.resolved && g.remaining>0 && System.currentTimeMillis()<g.expiresAt).findFirst()
            .orElseThrow(() -> new IllegalStateException(msg("captura.sem_direito")));
        var world=server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(grant.arena.dimension)));
        if (world == null) throw new IllegalStateException(msg("captura.arena_indisponivel"));
        if (!player.isAlive() || player.isPassenger() || player.isCreative() || player.isSpectator())
            throw new IllegalStateException(msg("captura.sobrevivencia"));
        if (com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player)!=null)
            throw new IllegalStateException(msg("captura.em_batalha"));
        if (!state.returns.containsKey(player.getUUID().toString()) || !grant.raid.equals(state.returns.get(player.getUUID().toString()).raid)) throw new IllegalStateException(msg("captura.sem_direito"));
        if (player.serverLevel()!=world || player.distanceToSqr(grant.arena.x,grant.arena.y,grant.arena.z)>grant.radius*grant.radius)
            throw new IllegalStateException(msg("captura.sem_direito"));
        RaidInventory.requireEmpty(player);
        var boss=grant.boss;
        var entity=PokemonProperties.Companion.parse(boss.species+" level="+boss.level+" shiny="+boss.shiny+" uncatchable=no"+(boss.form.isEmpty()?"":" form="+boss.form)).createEntity(world);
        entity.addTag(TARGET_TAG); entity.addTag(OWNER_TAG+player.getUUID()); entity.setNoAi(true); entity.setInvulnerable(true); entity.setPersistenceRequired();
        entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(),false);
        entity.setCustomName(RaidMessages.component(msg("captura.nome","boss",boss.name,"jogador",player.getGameProfile().getName())));
        entity.setCustomNameVisible(true);
        entity.moveTo(grant.arena.x,grant.arena.y,grant.arena.z,grant.arena.yaw,0);
        var session=new Session(player,grant,entity,System.currentTimeMillis());
        sessions.put(player.getUUID(),session);
        try {
            RaidPresentation.glow(entity, true);
            world.getChunkAt(entity.blockPosition());
            if (!world.addFreshEntity(entity)) throw new IllegalStateException(msg("captura.arena_indisponivel"));
            refreshBalls(player,session);
            updateBar(session,System.currentTimeMillis());
            if (sessions.get(player.getUUID())!=session) return;
            tell(player,"captura.inicio","boss",boss.name,"quantidade",grant.remaining,"tempo",RaidTime.format(grant.durationSeconds),"chance",String.format(Locale.ROOT,"%.2f",grant.chance*100));
        } catch(RuntimeException error) { close(player,true); throw error; }
    }
    private void beginBattle(Session session,UUID selected) {
        if (session.battle!=null) return;
        var player=session.player;
        if (BattleRegistry.getBattleByParticipatingPlayer(player)!=null || com.cobblemon.mod.common.util.PlayerExtensionsKt.isPartyBusy(player)) return;
        var party=Cobblemon.INSTANCE.getStorage().getParty(player);
        var chosen=party.get(selected);
        if(chosen==null || chosen.getCurrentHealth()<=0) { tell(player,"textos.selecione_um_pokemon_saudavel_da_sua_equipe");return; }
        UUID lead=selected;
        session.starting=true;
        try {
            BattleBuilder.INSTANCE.pve(player,session.target,lead,BattleFormat.Companion.getGEN_9_SINGLES(),false,false,(float)(session.grant.radius+16),party)
                .ifSuccessful(battle -> {
                    session.battle=battle;
                    var enemy=battle.getSide2().getActors()[0];
                    enemy.getActivePokemon().getFirst().setBattlePokemon(enemy.getPokemonList().getFirst());
                    return kotlin.Unit.INSTANCE;
                });
        } finally { session.starting=false; }
        if (session.battle==null) tell(player,"captura.inicio_recusado");
    }
    boolean challenge(com.cobblemon.mod.common.net.messages.server.BattleChallengePacket packet,ServerPlayer player) {
        var target=player.level().getEntity(packet.getTargetedEntityId());
        if(target==null || !protectedEntity(target)) return false;
        var session=sessions.get(player.getUUID());
        if(session==null || session.target!=target) { tell(player,"captura.alvo_privado");return true; }
        if(System.currentTimeMillis()>=session.ends) { close(player,Outcome.TIMEOUT);return true; }
        if(!inside(player,session) || !player.isAlive() || session.result!=null) return true;
        if(!RaidPermissions.check(player.createCommandSourceStack(),"shirokai.raids.use",true)
            || !RaidPermissions.check(player.createCommandSourceStack(),"shirokai.raids.join",true)) { tell(player,"textos.voce_nao_tem_permissao_para_participar_da_raid");return true; }
        if(!canAimAtOwnCopy(player,session.target)) {
            tell(player,"textos.aproxime_se_do_boss_para_desafia_lo");return true;
        }
        try { beginBattle(session,packet.getSelectedPokemonId()); }
        catch(RuntimeException error) { tell(player,"captura.inicio_recusado"); }
        return true;
    }
    private boolean canAimAtOwnCopy(ServerPlayer player,PokemonEntity target) {
        var eye=player.getEyePosition();
        var end=eye.add(player.getViewVector(1).scale(Cobblemon.config.getBattleWildMaxDistance()));
        var hit=target.getBoundingBox().inflate(target.getPickRadius()).clip(eye,end);
        if(hit.isEmpty()) return false;
        // Other players' invisible copies share this position. They must not block the owner's ray.
        var obstacle=player.level().clip(new net.minecraft.world.level.ClipContext(eye,hit.get(),
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return obstacle.getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private void updateBar(Session session,long now) {
        long remaining=Math.max(0,session.ends-now);
        session.bar.setName(RaidMessages.component(msg("captura.bossbar","boss",session.grant.boss.name,"tempo",RaidTime.format((remaining+999)/1000),"quantidade",session.grant.remaining)));
        session.bar.setProgress((float)Math.clamp(remaining/120000.0,0,1));
    }
    boolean allowsBattle(PokemonBattle battle) {
        for (var session:sessions.values()) for (var actor:battle.getActors()) for (var pokemon:actor.getPokemonList()) {
            if (!pokemon.getEffectedPokemon().getUuid().equals(session.target.getPokemon().getUuid())) continue;
            var ids=new ArrayList<UUID>(); battle.getPlayerUUIDs().forEach(ids::add);
            return session.starting && ids.size()==1 && ids.getFirst().equals(session.player.getUUID());
        }
        return true;
    }
    void throwBall(ServerPlayer player, InteractionHand hand) {
        var session=sessions.get(player.getUUID());
        var stack=player.getItemInHand(hand);
        if (session == null || !isBall(stack) || !session.grant.id.equals(stack.get(DataComponents.CUSTOM_DATA).copyTag().getString(ITEM_KEY))) return;
        long now=System.currentTimeMillis();
        if (session.result!=null || session.flight!=null || now<session.nextThrow || session.grant.resolved || session.grant.remaining<=0 || now>=session.ends) return;
        if (session.battle==null || session.battle.getEnded() || BattleRegistry.getBattleByParticipatingPlayer(player)!=session.battle
            || !(session.battle.getSide1().getActors()[0] instanceof PlayerBattleActor actor) || !actor.canFitForcedAction()) return;
        // Spend and save before creating the projectile. A crash never restores a spent throw.
        session.grant.remaining--;
        persist.run();
        refreshBalls(player,session);
        var ball=new EmptyPokeBallEntity(PokeBalls.getPokeBall(),player.serverLevel(),player,CobblemonEntities.EMPTY_POKEBALL);
        ball.addTag(BALL_TAG);
        session.flight=ball; session.nextThrow=now+1000;
        ball.shootFromRotation(player,player.getXRot(),player.getYRot(),0,1.5f,1.0f);
        if (!player.serverLevel().addFreshEntity(ball)) { session.flight=null; return; }
        ball.getCaptureFuture().whenComplete((success,error) -> server.execute(() -> {
            if (sessions.get(player.getUUID())!=session) return;
            if (Boolean.TRUE.equals(success)) { session.result=Outcome.CAPTURED;session.finishAt=System.currentTimeMillis()+1200; }
            else if(error!=null || session.grant.resolved) { session.result=Outcome.CANCELLED;session.finishAt=System.currentTimeMillis()+1200; }
            else if(session.grant.remaining==0) { session.result=Outcome.NO_BALLS;session.finishAt=System.currentTimeMillis()+1200; }
            else { session.flight=null; tell(player,"captura.restantes","quantidade",session.grant.remaining); }
        }));
    }
    private boolean allowed(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        if (!(ball.getOwner() instanceof ServerPlayer player)) return false;
        var session=sessions.get(player.getUUID());
        return session!=null && session.flight==ball && session.target==pokemon && !session.grant.resolved
            && System.currentTimeMillis()<session.ends && inside(player,session);
    }
    private boolean decide(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        if (!allowed(ball,pokemon)) return false;
        var session=sessions.get(ball.getOwner().getUUID());
        boolean success=random.nextDouble()<session.grant.chance;
        if (success) { session.grant.resolved=true; persist.run(); }
        return success;
    }
    private boolean inside(ServerPlayer player, Session session) {
        return player.serverLevel()==session.target.level() && player.distanceToSqr(session.grant.arena.x,session.grant.arena.y,session.grant.arena.z)<=session.grant.radius*session.grant.radius;
    }
    void tick() { tick(System.currentTimeMillis()); }
    void tick(long now) {
        for(var id:List.copyOf(sessions.keySet())) {
            var session=sessions.get(id); var player=session.player;
            if (player==null) { remove(id); continue; }
            updateBar(session,now);
            if (now>=session.ends) { close(player,Outcome.TIMEOUT);continue; }
            if(session.result!=null) { if(now>=session.finishAt) close(player,session.result);continue; }
            if (sessions.get(id)!=session) continue;
            if (!player.isAlive() || !inside(player,session)) { close(player,Outcome.LEFT);continue; }
            if(session.target.isRemoved() && session.flight==null || session.battle!=null && session.battle.getEnded() && session.flight==null) { close(player,Outcome.BATTLE_ENDED);continue; }
            if (session.flight!=null && session.flight.isRemoved() && now>=session.nextThrow && !session.flight.getCaptureFuture().isDone()) {
                session.flight=null;
                if (session.grant.remaining==0 || session.grant.resolved) close(player,session.grant.resolved?Outcome.CANCELLED:Outcome.NO_BALLS);
            }
        }
    }
    void close(ServerPlayer player, boolean forfeit) { close(player,Outcome.LEFT); }
    void close(ServerPlayer player,Outcome reason) {
        var current=sessions.get(player.getUUID());
        if(current!=null && (current.result==Outcome.CAPTURED || current.flight!=null
            && current.flight.getCaptureFuture().isDone() && !current.flight.getCaptureFuture().isCompletedExceptionally()
            && Boolean.TRUE.equals(current.flight.getCaptureFuture().getNow(false)))) reason=Outcome.CAPTURED;
        var session=remove(player.getUUID());
        try {
            if (session!=null) { state.captures.remove(session.grant.id); persist.run(); }
            RaidInventory.removeRaidBalls(player);
            ShirokaiRaids.inventoryRestored(player);
            if (session!=null) tell(player,reason.message,"boss",session.grant.boss.name);
        } catch(Exception error) { throw new IllegalStateException("Não foi possível encerrar a captura com segurança.",error); }
    }
    private void syncPP(Session session) {
        if(session.battle==null) return;
        if(com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService()
            instanceof com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService graal) {
            // Static expression and a server-generated UUID only; read the live engine before a timed stop.
            var slots=graal.getContext().eval("js","(s=>s?s.battle.sides[0].pokemon.map(p=>({uuid:p.uuid,moves:p.moveSlots.map(m=>({id:m.id,pp:m.pp}))})):[])(battleMap.get('"+session.battle.getBattleId()+"'))");
            var party=Cobblemon.INSTANCE.getStorage().getParty(session.player);
            for(long i=0;i<slots.getArraySize();i++) {
                var entry=slots.getArrayElement(i);var pokemon=party.get(UUID.fromString(entry.getMember("uuid").asString()));
                if(pokemon==null) continue;
                var moves=entry.getMember("moves");
                for(long j=0;j<moves.getArraySize();j++) {
                    var slot=moves.getArrayElement(j);String name=slot.getMember("id").asString();int pp=slot.getMember("pp").asInt();
                    for(var move:pokemon.getMoveSet().getMoves()) if(move.getName().equalsIgnoreCase(name)&&pp>=0&&pp<=move.getMaxPp()) move.setCurrentPp(pp);
                }
            }
        }
    }
    private Session remove(UUID id) {
        var session=sessions.remove(id);
        if(session!=null) {
            session.bar.removeAllPlayers();
            try { syncPP(session); }
            catch(RuntimeException error) { org.slf4j.LoggerFactory.getLogger("shirokai_raids").warn("Não foi possível sincronizar PP ao encerrar captura",error); }
            try { if(session.battle!=null && !session.battle.getEnded()) session.battle.stop(); }
            finally {
                if(session.flight!=null) {
                    session.flight.getSchedulingTracker().clear();
                    session.flight.getCaptureFuture().complete(false);
                    session.flight.discard();
                }
                session.target.getSchedulingTracker().clear();
                RaidPresentation.clearGlow(session.target);
                session.target.discard();session.chunks.close();
            }
        }
        return session;
    }
    void shutdown() {
        for(var session:List.copyOf(sessions.values())) {
            try { close(session.player,Outcome.CANCELLED); }
            catch(Exception error) { org.slf4j.LoggerFactory.getLogger("shirokai_raids").error("Falha ao suspender captura",error); }
        }
    }
    boolean knownBall(Entity entity) { return sessions.values().stream().anyMatch(s -> s.flight==entity); }
    boolean capturePokemon(UUID id) { return sessions.values().stream().anyMatch(s -> s.target.getPokemon().getUuid().equals(id)); }
    boolean knownTarget(Entity entity) { return sessions.values().stream().anyMatch(s -> s.target==entity); }
    private void refreshBalls(ServerPlayer player, Session session) {
        RaidInventory.removeRaidBalls(player);
        if (session.grant.remaining>0) {
            var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:poke_ball")),session.grant.remaining);
            var data=new CompoundTag(); data.putString(ITEM_KEY,session.grant.id);
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            stack.set(DataComponents.CUSTOM_NAME,RaidMessages.component(msg("captura.bola")));
            int slot=player.getInventory().getFreeSlot();
            if(slot<0) throw new IllegalStateException(msg("raid.inventario_vazio"));
            player.getInventory().setItem(slot,stack);
            player.getInventory().selected=slot;
        }
        RaidInventory.sync(player);
    }
    static void tell(ServerPlayer player,String key,Object... args) { player.sendSystemMessage(RaidMessages.chat(msg(key,args))); }
}
