package com.shirokai.raids;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.shirokai.raids.core.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ShirokaiRaids implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("shirokai_raids");
    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("shirokai-raids");
    private RaidConfig config;
    private Map<String, RaidConfig.Boss> bosses;
    private Map<String, RaidConfig.Rewards> rewards;
    private RaidState state;
    private Path statePath;
    private MinecraftServer server;
    private Active active;
    private boolean healthy;
    private long tick;
    private final Map<UUID,Long> claimCooldown = new HashMap<>();
    private final Set<Integer> warned = new HashSet<>();
    private final Random random = new Random();

    private static final class Participant {
        final UUID pokemon;
        long nextAttack;
        Participant(UUID pokemon) { this.pokemon = pokemon; }
    }
    private static final class Active {
        final String id = UUID.randomUUID().toString();
        final RaidConfig.Boss definition;
        final PokemonEntity boss;
        final ServerLevel world;
        final Vec3 center;
        final RaidHealthManager health;
        final RaidDamageTracker damage = new RaidDamageTracker();
        final Map<UUID, Participant> participants = new HashMap<>();
        final Map<UUID, Long> cooldowns = new HashMap<>();
        final long ends;
        final ServerBossEvent bar;
        Active(RaidConfig.Boss definition, PokemonEntity boss, ServerLevel world, Vec3 center, long ends) {
            this.definition = definition; this.boss = boss; this.world = world; this.center = center; this.ends = ends;
            health = new RaidHealthManager(definition.hp);
            bar = new ServerBossEvent(Component.literal(definition.name), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        }
    }

    @Override public void onInitialize() {
        try { loadConfig(); } catch (Exception e) { throw new IllegalStateException("Configuração Shirokai Raids inválida", e); }
        ServerLifecycleEvents.SERVER_STARTED.register(this::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { if (active != null) finish(false, "Servidor encerrando; raid cancelada."); });
        ServerTickEvents.END_SERVER_TICK.register(s -> tick());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !isBoss(entity.getUUID()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof PokemonEntity && entity.getTags().contains("shirokai_raids_boss") && !isBoss(entity.getUUID())) entity.discard();
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(event -> { if (isBoss(event.getPokemon().getUUID())) event.cancel(); });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            if (active == null) return;
            for (var actor : event.getBattle().getActors()) for (var bp : actor.getPokemonList()) {
                UUID id = bp.getEffectedPokemon().getUuid();
                if (id.equals(active.boss.getPokemon().getUuid()) || active.participants.values().stream().anyMatch(p -> p.pokemon.equals(id))) {
                    event.cancel(); return;
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var raid = Commands.literal("raid").requires(s -> RaidPermissions.check(s,"shirokai.raids.use",true)).executes(c -> info(c.getSource()));
            raid.then(Commands.literal("entrar").requires(s -> RaidPermissions.check(s,"shirokai.raids.join",true)).executes(c -> join(c.getSource())));
            raid.then(Commands.literal("atacar").requires(s -> RaidPermissions.check(s,"shirokai.raids.attack",true)).executes(c -> attack(c.getSource())));
            raid.then(Commands.literal("sair").executes(c -> leave(c.getSource())));
            raid.then(Commands.literal("tempo").executes(c -> info(c.getSource())));
            raid.then(Commands.literal("dano").executes(c -> damage(c.getSource())));
            raid.then(Commands.literal("top").executes(c -> ranking(c.getSource())));
            raid.then(Commands.literal("recompensas").requires(s -> RaidPermissions.check(s,"shirokai.raids.rewards",true)).executes(c -> claim(c.getSource())));
            dispatcher.register(raid);
            var admin = Commands.literal("raidadmin").requires(s -> RaidPermissions.check(s,"shirokai.raids.admin",s.hasPermission(2)));
            admin.then(Commands.literal("iniciar").then(Commands.argument("boss", StringArgumentType.word())
                .suggests((c,b) -> { bosses.keySet().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> start(c.getSource(), StringArgumentType.getString(c,"boss")))));
            admin.then(Commands.literal("parar").executes(c -> { finish(false,"Raid encerrada pela administração."); return 1; }));
            admin.then(Commands.literal("setspawn").executes(c -> setSpawn(c.getSource())));
            admin.then(Commands.literal("status").executes(c -> info(c.getSource())));
            admin.then(Commands.literal("listar").executes(c -> reply(c.getSource(), String.join(", ", bosses.keySet()))));
            admin.then(Commands.literal("reload").executes(c -> {
                if (active != null) return reply(c.getSource(),"Encerre a raid antes de recarregar.");
                try { loadConfig(); return reply(c.getSource(),"Configurações recarregadas."); }
                catch (Exception e) { LOG.error("Falha de configuração", e); return reply(c.getSource(),"Configuração inválida: " + e.getMessage()); }
            }));
            dispatcher.register(admin);
        });
    }

    private void loadConfig() throws IOException {
        JsonFiles.defaultFile(configDir.resolve("config.json"), new RaidConfig());
        JsonFiles.defaultFile(configDir.resolve("bosses/rayquaza.json"), new RaidConfig.Boss());
        JsonFiles.defaultFile(configDir.resolve("rewards/default.json"), new RaidConfig.Rewards());
        var next = JsonFiles.read(configDir.resolve("config.json"), RaidConfig.class); next.validate();
        var bossMap = new TreeMap<String,RaidConfig.Boss>();
        try (var paths = Files.list(configDir.resolve("bosses"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                var boss = JsonFiles.read(path, RaidConfig.Boss.class); boss.validate();
                if (bossMap.putIfAbsent(boss.id,boss) != null) throw new IOException("ID de boss duplicado: " + boss.id);
            }
        }
        if (bossMap.isEmpty()) throw new IOException("Cadastre pelo menos um boss");
        var rewardMap = new HashMap<String,RaidConfig.Rewards>();
        for (var boss : bossMap.values()) {
            var table = JsonFiles.read(configDir.resolve("rewards/" + boss.rewards + ".json"),RaidConfig.Rewards.class); table.validate();
            rewardMap.put(boss.rewards,table);
        }
        config = next; bosses = bossMap; rewards = rewardMap;
    }
    private void started(MinecraftServer server) {
        this.server = server;
        statePath = server.getWorldPath(LevelResource.ROOT).resolve("data/shirokai-raids/state.json");
        try {
            state = Files.exists(statePath) ? JsonFiles.read(statePath,RaidState.class) : new RaidState(); state.validate();
            // Restart recovery cancels the active raid; no repeat rewards or accumulated raids.
            state.activeRaid = null; state.bossEntity = null;
            if (state.nextRaid == 0 || (state.nextRaid < System.currentTimeMillis() && config.restartPolicy.equals("SKIP_MISSED")))
                state.nextRaid = RaidSchedule.next(System.currentTimeMillis(),config.intervalSeconds);
            healthy = true; save();
        } catch (Exception e) { healthy = false; LOG.error("Raids desativadas: falha ao carregar estado; arquivo preservado",e); }
    }
    private void save() {
        if (!healthy) throw new IllegalStateException("Persistência indisponível");
        try { JsonFiles.write(statePath,state); }
        catch (IOException e) { healthy = false; throw new IllegalStateException("Falha ao persistir; raids desativadas para proteger recompensas",e); }
    }
    private boolean isBoss(UUID uuid) { return active != null && active.boss.getUUID().equals(uuid); }
    private int reply(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text),false); return 1; }
    private void broadcast(String text) { server.getPlayerList().broadcastSystemMessage(Component.literal("[Raids] " + text),false); }
    private int info(CommandSourceStack source) {
        if (!healthy) return reply(source,"Raids indisponíveis: verifique os logs de persistência.");
        return reply(source,active == null ? "Próxima raid em " + Math.max(0,(state.nextRaid-System.currentTimeMillis())/1000) + " segundos."
            : active.definition.name + ": " + active.health.current() + "/" + active.health.maximum() + " HP. Restam " + Math.max(0,(active.ends-System.currentTimeMillis())/1000) + "s.");
    }
    private int setSpawn(CommandSourceStack source) {
        if (active != null) return reply(source,"Encerre a raid antes de alterar a arena.");
        try {
            var player = source.getPlayerOrException();
            config.dimension = player.serverLevel().dimension().location().toString();
            config.x = player.getX(); config.y = player.getY(); config.z = player.getZ(); config.spawnConfigured = true;
            JsonFiles.write(configDir.resolve("config.json"),config);
            return reply(source,"Arena configurada. Mantenha o local acessível e livre de construções.");
        } catch (Exception e) { return reply(source,"Não foi possível salvar a arena: " + e.getMessage()); }
    }
    private int start(CommandSourceStack source, String id) {
        try { begin(id); return reply(source,"Raid iniciada."); }
        catch (Exception e) { LOG.error("Falha ao iniciar raid",e); return reply(source,"Não foi possível iniciar: " + e.getMessage()); }
    }
    private void begin(String id) {
        if (!healthy || active != null) throw new IllegalStateException("Raids indisponíveis ou já existe uma raid ativa");
        if (!config.spawnConfigured) throw new IllegalStateException("Use /raidadmin setspawn primeiro");
        var definition = bosses.get(id);
        if (definition == null) throw new IllegalArgumentException("Boss desconhecido");
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(config.dimension)));
        if (world == null) throw new IllegalArgumentException("Dimensão inexistente");
        if (!world.hasChunkAt(net.minecraft.core.BlockPos.containing(config.x,config.y,config.z))) throw new IllegalStateException("O chunk da arena deve estar carregado");
        if (config.y < world.getMinBuildHeight() || config.y >= world.getMaxBuildHeight()) throw new IllegalArgumentException("Altura de arena inválida");
        var properties = PokemonProperties.Companion.parse(definition.species + " level=" + definition.level + " shiny=" + definition.shiny + " uncatchable=yes" + (definition.form.isEmpty() ? "" : " form=" + definition.form));
        var entity = properties.createEntity(world);
        if (!entity.getPokemon().getSpecies().getResourceIdentifier().getPath().equals(definition.species.replace("cobblemon:","")))
            throw new IllegalArgumentException("Espécie inválida ou indisponível");
        entity.getPokemon().setScaleModifier(definition.scale);
        entity.setCustomName(Component.literal(definition.name));
        entity.setPersistenceRequired(); entity.setInvulnerable(true);
        entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(),true);
        entity.addTag("shirokai_raids_boss");
        entity.moveTo(config.x,config.y,config.z,0,0);
        var candidate = new Active(definition,entity,world,new Vec3(config.x,config.y,config.z),System.currentTimeMillis()+config.durationSeconds*1000L);
        active = candidate; // entity load callback must recognize it before insertion
        if (!world.addFreshEntity(entity)) { active = null; throw new IllegalStateException("Spawn recusado"); }
        state.activeRaid = candidate.id; state.bossEntity = entity.getUUID().toString(); state.hp = definition.hp; state.damage.clear();
        state.nextRaid = RaidSchedule.next(System.currentTimeMillis(),config.intervalSeconds); warned.clear();
        try { save(); } catch (RuntimeException e) { entity.discard(); active = null; throw e; }
        broadcast(definition.name + " apareceu! Use /raid entrar e envie seu Pokémon.");
    }
    private boolean inArena(ServerPlayer player) {
        return active != null && player.isAlive() && !player.isSpectator() && player.serverLevel() == active.world && player.position().distanceToSqr(active.center) <= config.arenaRadius*config.arenaRadius;
    }
    private int join(CommandSourceStack source) {
        try {
            var player = source.getPlayerOrException();
            if (!inArena(player)) return reply(source,"Entre fisicamente na arena da raid para participar.");
            active.bar.addPlayer(player);
            return reply(source,"Envie um Pokémon e use /raid atacar. Não há teleporte automático.");
        } catch (Exception e) { return reply(source,"Comando disponível para jogadores na arena."); }
    }
    private int attack(CommandSourceStack source) {
        try {
            if (!healthy) return reply(source,"Persistência indisponível.");
            var player = source.getPlayerOrException();
            if (!inArena(player)) return reply(source,"Você precisa estar na arena.");
            if (!active.participants.containsKey(player.getUUID()) && active.participants.size() >= config.maximumParticipants) return reply(source,"Limite de participantes atingido.");
            if (active.participants.containsKey(player.getUUID())) return reply(source,"Você já está atacando. Use /raid sair para trocar de Pokémon.");
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                var entity = pokemon.getEntity();
                if (entity != null && Objects.equals(pokemon.getOwnerUUID(),player.getUUID()) && entity.level() == active.world
                    && pokemon.getCurrentHealth() > 0 && !entity.isBattling() && !entity.isBusy() && supportedMove(pokemon) != null
                    && entity.position().distanceToSqr(active.boss.position()) <= config.attackRange*config.attackRange) {
                    active.participants.put(player.getUUID(),new Participant(pokemon.getUuid()));
                    active.bar.addPlayer(player);
                    return reply(source,"Seu Pokémon está atacando o boss em tempo real.");
                }
            }
            return reply(source,"Envie um Pokémon saudável com movimento suportado, livre de batalha e próximo do boss.");
        } catch (Exception e) { LOG.warn("Falha ao selecionar atacante",e); return reply(source,"Não foi possível selecionar o Pokémon."); }
    }
    private Move supportedMove(Pokemon pokemon) {
        return pokemon.getMoveSet().getMoves().stream().filter(m -> config.supportedMoves.contains(m.getTemplate().getName()) && m.getTemplate().getPower() > 0).findFirst().orElse(null);
    }
    private int leave(CommandSourceStack source) {
        try { var p=source.getPlayerOrException(); if(active != null) { active.participants.remove(p.getUUID()); active.bar.removePlayer(p); } }
        catch (Exception ignored) { return reply(source,"Comando disponível para jogadores."); }
        return reply(source,"Participação encerrada. Sua contribuição foi preservada.");
    }
    private int damage(CommandSourceStack source) {
        try { var p=source.getPlayerOrException(); return reply(source,active == null ? "Nenhuma raid ativa." : "Dano aplicado: " + active.damage.damage(p.getUUID()) + String.format(Locale.ROOT," (%.2f%%)",100*active.damage.contribution(p.getUUID(),active.health.maximum()))); }
        catch (Exception e) { return reply(source,"Comando disponível para jogadores."); }
    }
    private int ranking(CommandSourceStack source) {
        if (active == null) return reply(source,"Nenhuma raid ativa.");
        active.damage.ranking().stream().limit(10).forEach(e -> reply(source,e.name() + ": " + e.damage() + " HP")); return 1;
    }
    private void tick() {
        tick++;
        if (!healthy || server == null) return;
        try {
            if (active != null && tick % 5 == 0) combat();
            if (tick % 20 != 0) return;
            long now = System.currentTimeMillis();
            if (active != null) {
                if (now >= active.ends) { finish(false,"Raid encerrada por tempo."); return; }
                if (active.boss.isRemoved()) { finish(false,"Boss removido; raid cancelada com segurança."); return; }
                if (active.boss.position().distanceToSqr(active.center) > config.arenaRadius*config.arenaRadius)
                    active.boss.teleportTo(active.center.x,active.center.y,active.center.z);
                active.bar.setProgress((float) active.health.fraction());
                active.bar.setName(Component.literal(active.definition.name + " | " + active.health.current() + " HP | " + (active.ends-now)/1000 + "s"));
                for (var player : server.getPlayerList().getPlayers()) {
                    if (inArena(player)) active.bar.addPlayer(player); else active.bar.removePlayer(player);
                }
                if (tick % (config.bossAttackSeconds*20L) == 0) bossAttack();
                if (tick % 200 == 0) snapshot();
            } else if (config.spawnConfigured) {
                if (RaidSchedule.due(now,state.nextRaid,false)) {
                    try { var ids = new ArrayList<>(bosses.keySet()); begin(ids.get(random.nextInt(ids.size()))); }
                    catch (Exception e) { LOG.warn("Raid automática adiada",e); state.nextRaid=RaidSchedule.next(now,config.intervalSeconds); save(); }
                } else for (int warning : config.warningsSeconds) if (state.nextRaid-now <= warning*1000L && warned.add(warning)) broadcast("Faltam até " + warning + " segundos para a próxima raid.");
            }
        } catch (Exception e) { LOG.error("Raid suspensa após falha; verifique o estado persistente",e); healthy=false; if(active!=null) { active.bar.removeAllPlayers(); active.boss.discard(); active=null; } }
    }
    private Pokemon findPokemon(ServerPlayer owner, UUID uuid) {
        for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(owner)) if (p.getUuid().equals(uuid)) return p;
        return null;
    }
    private void combat() {
        var raid = active;
        var iterator = raid.participants.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry=iterator.next(); var player=server.getPlayerList().getPlayer(entry.getKey());
            var participation=entry.getValue();
            var pokemon=player==null ? null : findPokemon(player,participation.pokemon);
            var entity=pokemon==null ? null : pokemon.getEntity();
            if (player==null || !inArena(player) || entity==null || entity.level()!=raid.world || pokemon.getCurrentHealth()<=0 || entity.isBattling() || entity.isBusy()) { iterator.remove(); continue; }
            if (!CombatRules.eligible(tick,raid.cooldowns.getOrDefault(pokemon.getUuid(),0L),entity.position().distanceToSqr(raid.boss.position()),config.attackRange,
                Objects.equals(pokemon.getOwnerUUID(),player.getUUID()),entity.isAlive(),entity.isBattling(),entity.isBusy())) continue;
            var move=supportedMove(pokemon); if(move==null) { iterator.remove(); continue; }
            raid.cooldowns.put(pokemon.getUuid(),tick+config.attackCooldownTicks);
            var template=move.getTemplate(); boolean physical=template.getDamageCategory().getName().equals("physical");
            String type=template.getElementalType().getShowdownId();
            var defending=raid.boss.getPokemon();
            var types=new ArrayList<String>(); for(var t:defending.getTypes()) types.add(t.getShowdownId());
            boolean stab=false; for(var t:pokemon.getTypes()) if(t.getShowdownId().equals(type)) stab=true;
            long requested=CombatRules.damage(pokemon.getLevel(),physical?pokemon.getAttack():pokemon.getSpecialAttack(),
                physical?defending.getDefence():defending.getSpecialDefence(),template.getPower(),stab?1.5:1,TypeChart.effectiveness(type,types));
            long applied=raid.health.damage(requested);
            raid.damage.record(player.getUUID(),player.getGameProfile().getName(),pokemon.getUuid(),applied,System.currentTimeMillis());
            if (raid.health.current()==0) { finish(true,"Boss derrotado! Recompensas disponíveis em /raid recompensas."); return; }
        }
    }
    private void bossAttack() {
        int phase=CombatRules.phase(active.health.fraction());
        var entries=new ArrayList<>(active.participants.entrySet()); Collections.shuffle(entries,random);
        int count=phase==1?1:Math.min(phase,entries.size());
        for(int i=0;i<count && i<entries.size();i++) {
            var entry=entries.get(i); var owner=server.getPlayerList().getPlayer(entry.getKey());
            var pokemon=owner==null?null:findPokemon(owner,entry.getValue().pokemon);
            var entity=pokemon==null?null:pokemon.getEntity();
            if(entity==null || !inArena(owner) || entity.level()!=active.world || entity.isBattling() || entity.isBusy()
                || entity.position().distanceToSqr(active.boss.position())>config.attackRange*config.attackRange) continue;
            // Pokemon HP, never player damage or block destruction. Entity ratio follows the data state.
            pokemon.setCurrentHealth(Math.max(0,pokemon.getCurrentHealth()-config.bossDamage*phase));
            entity.setHealth(entity.getMaxHealth()*pokemon.getCurrentHealth()/Math.max(1,pokemon.getMaxHealth()));
        }
    }
    private void snapshot() {
        state.hp=active.health.current(); state.damage.clear();
        for(var e:active.damage.ranking()) state.damage.put(e.player().toString(),e.damage());
        save();
    }
    private void finish(boolean defeated,String message) {
        if(active==null) return;
        var raid=active;
        try {
            state.hp=raid.health.current(); state.damage.clear();
            for(var e:raid.damage.ranking()) state.damage.put(e.player().toString(),e.damage());
            if(defeated && !state.completed.contains(raid.id)) {
                var table=rewards.get(raid.definition.rewards);
                for(var entry:raid.damage.ranking()) {
                    double contribution=raid.damage.contribution(entry.player(),raid.health.maximum());
                    if(contribution<table.minimumContribution) continue;
                    int rolls=RaidRewardManager.rolls(contribution,table.minimumContribution);
                    int remaining=table.maximumItems;
                    for(int r=0;r<rolls && remaining>0;r++) {
                        RaidConfig.Reward item=table.items.get(RaidRewardManager.select(table.items.stream().map(i->i.weight).toList(),random));
                        int count=Math.min(remaining,random.nextInt(item.maximum-item.minimum+1)+item.minimum); remaining-=count;
                        state.pending.computeIfAbsent(entry.player().toString(),k->new ArrayList<>()).add(new RaidState.Pending(raid.id,item.item,count));
                    }
                }
                state.completed.add(raid.id);
            }
            state.activeRaid=null; state.bossEntity=null; save();
        } finally { raid.bar.removeAllPlayers(); raid.boss.discard(); active=null; }
        broadcast(message);
    }
    private int claim(CommandSourceStack source) {
        if(!healthy) return reply(source,"Resgate indisponível: persistência precisa de atenção.");
        try {
            var player=source.getPlayerOrException(); var key=player.getUUID().toString();
            if(tick < claimCooldown.getOrDefault(player.getUUID(),0L)) return reply(source,"Aguarde cinco segundos entre resgates.");
            claimCooldown.put(player.getUUID(),tick+100);
            var pending=state.pending.computeIfAbsent(key,k->new ArrayList<>());
            if(pending.isEmpty()) return reply(source,"Nenhuma recompensa pendente.");
            int delivered=0;
            if(!pending.isEmpty()) {
                var reward=pending.getFirst(); var id=ResourceLocation.tryParse(reward.item);
                if(id==null || !BuiltInRegistries.ITEM.containsKey(id)) return reply(source,"Item indisponível; recompensa preservada para a administração corrigir.");
                var item=BuiltInRegistries.ITEM.get(id); int maxStack=new ItemStack(item).getMaxStackSize();
                int count=Math.min(reward.count,maxStack);
                int slot=player.getInventory().getFreeSlot();
                if(slot<0) return reply(source,"Inventário cheio; recompensas preservadas."); // Only empty slots: no partial inventory insertion.
                var reservation=new RaidState.Pending(reward.raid,reward.item,count);
                reservation.id=key+":"+reward.id+":"+UUID.randomUUID();
                reward.count-=count;
                if(reward.count==0) pending.removeFirst();
                state.uncertain.put(reservation.id,reservation); save();
                player.getInventory().setItem(slot,new ItemStack(item,count));
                server.getPlayerList().saveAll();
                state.uncertain.remove(reservation.id); save(); delivered+=count;
            }
            return reply(source,"Itens entregues: " + delivered + ". Pendências: " + pending.size() + ". Resgates interrompidos por crash exigem conferência administrativa.");
        } catch(Exception e) { LOG.error("Falha no resgate; reserva preservada para conferência",e); return reply(source,"Resgate interrompido. Avise a administração; não será repetido automaticamente."); }
    }
}
