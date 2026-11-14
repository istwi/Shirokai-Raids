package com.shirokai.raids;

import static com.shirokai.raids.RaidMessages.msg;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.shirokai.raids.core.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ShirokaiRaids implements ModInitializer {
    private static ShirokaiRaids instance;
    private static final Logger LOG = LoggerFactory.getLogger("shirokai_raids");
    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("shirokai-raids");
    private RaidConfig config;
    private RaidCatalogSettings catalogSettings;
    private Map<String, RaidConfig.Boss> bosses;
    private Map<String, RaidConfig.Rewards> rewards;
    private RaidState state;
    private Path statePath;
    private MinecraftServer server;
    private Active active;
    private record Victory(String raid,Set<UUID> present,RaidVictoryTransition animation) {}
    private Victory victory;
    private final ServerBossEvent countdownBar = new ServerBossEvent(Component.empty(),
        BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);

    private boolean healthy;
    private long tick;
    private RewardDelivery rewardDelivery;
    private RaidRewardSettings rewardSettings;
    private RaidRewardJournal modularRewards;
    private RaidCapture captures;
    private final Set<UUID> rewardQueue = new LinkedHashSet<>();
    private final Map<UUID,Long> claimCooldown = new HashMap<>();
    private final Map<UUID,Long> teleportCooldown = new HashMap<>();
    private final Random random = new Random();

    private static final class Active {
        final String id = UUID.randomUUID().toString();
        final RaidConfig.Boss definition;
        final PokemonEntity boss;
        final ServerLevel world;
        final Vec3 center;
        final RaidCombatCoordinator health;
        long rewardPendingAtStart;
        final Set<UUID> rewardParticipants=new HashSet<>();
        RaidRewardSettings rewardSettings;
        RaidConfig.Rewards legacyRewards;
        RaidChunkLease chunks;
        TraditionalRaidBattles battles;
        final RaidDamageTracker damage = new RaidDamageTracker();
        final long ends;
        final ServerBossEvent bar;
        Active(RaidConfig.Boss definition, PokemonEntity boss, ServerLevel world, Vec3 center, long ends, int capacity) {
            this.definition = definition; this.boss = boss; this.world = world; this.center = center; this.ends = ends;
            health = new RaidCombatCoordinator(UUID.fromString(id),definition.hp,capacity);
            bar = new ServerBossEvent(RaidMessages.component(definition.name), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        }
    }

    @Override public void onInitialize() {
        instance=this;
        TraditionalRaidBattles.register();
        RaidCapture.register();
        try { loadConfig(); } catch (Exception e) { throw new IllegalStateException(msg("textos.configuracao_shirokai_raids_invalida"), e); }
        ServerLifecycleEvents.SERVER_STARTED.register(this::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { cancelVictory(); if (captures != null) captures.shutdown(); countdownBar.removeAllPlayers(); if (active != null) finish(false, msg("textos.servidor_encerrando_raid_cancelada")); });
        ServerTickEvents.END_SERVER_TICK.register(s -> tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            try { RaidLegacyInventory.recover(handler.getPlayer()); } catch(Exception error) { LOG.error("Backup legado preservado",error); }
            RaidInventory.removeRaidBalls(handler.getPlayer());
            rewardQueue.add(handler.getPlayer().getUUID());
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            if (captures != null) captures.close(player,true);
            try { RaidLegacyInventory.recover(player); } catch(Exception error) { LOG.error("Backup legado preservado",error); }
            RaidInventory.removeRaidBalls(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            countdownBar.removePlayer(handler.getPlayer());
            if (victory!=null) victory.animation.removeViewer(handler.getPlayer());
            if (active != null) active.battles.leave(handler.getPlayer().getUUID());
            if (captures != null) captures.close(handler.getPlayer(),true);
            RaidInventory.removeRaidBalls(handler.getPlayer());
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !isBoss(entity.getUUID()) && !RaidCapture.protectedEntity(entity));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && RaidCapture.isBall(item.getItem())) entity.discard();
            if (RaidCapture.protectedEntity(entity) && (captures==null || !captures.knownTarget(entity))) { RaidPresentation.clearGlow(entity); entity.discard(); }
            if (entity.getTags().contains(RaidCapture.BALL_TAG) && (captures==null || !captures.knownBall(entity))) entity.discard();
            if (entity instanceof PokemonEntity && (entity.getTags().contains("shirokai_raids_boss")
                || entity.getTags().contains("shirokai_raid_boss"))
                && (active == null || entity != active.boss || world != active.world) && !(victory!=null && victory.animation.known(entity))) { RaidPresentation.clearGlow(entity); entity.discard(); }
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(event -> { if (isBoss(event.getPokemon().getUUID())) event.cancel(); });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            if (captures!=null && !captures.allowsBattle(event.getBattle())) { event.cancel(); return; }
            for (var actor : event.getBattle().getActors()) for (var bp : actor.getPokemonList()) {
                UUID id = bp.getEffectedPokemon().getUuid();
                if ((active!=null && id.equals(active.boss.getPokemon().getUuid())) || (victory!=null && victory.animation.pokemon(id))) {
                    event.cancel(); return;
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var raid = Commands.literal("raid").requires(s -> RaidPermissions.check(s,"shirokai.raids.use",true)).executes(c -> raidCommand(c.getSource()));
            raid.then(Commands.literal("entrar").requires(s -> RaidPermissions.check(s,"shirokai.raids.join",true)).executes(c -> join(c.getSource())));
            raid.then(Commands.literal("sair").executes(c -> leave(c.getSource())));
            raid.then(Commands.literal("tempo").executes(c -> info(c.getSource())));
            raid.then(Commands.literal("dano").executes(c -> damage(c.getSource())));
            raid.then(Commands.literal("top").executes(c -> ranking(c.getSource())));
            raid.then(Commands.literal("menu").executes(c -> raidCommand(c.getSource())));
            raid.then(Commands.literal("historico").executes(c -> openRaidMenu(c.getSource(), false)));
            raid.then(Commands.literal("ranking").executes(c -> openRaidMenu(c.getSource(), true)));
            raid.then(Commands.literal("recompensas").requires(s -> RaidPermissions.check(s,"shirokai.raids.rewards",true)).executes(c -> pendingMenu(c.getSource())));
            raid.then(Commands.literal("pendentes").requires(s -> RaidPermissions.check(s,"shirokai.raids.rewards",true)).executes(c -> pendingMenu(c.getSource())));
            raid.then(Commands.literal("resgatar").requires(s -> RaidPermissions.check(s,"shirokai.raids.rewards",true)).executes(c -> claim(c.getSource())));
            dispatcher.register(raid);
            var admin = Commands.literal("raidadmin").requires(s -> RaidPermissions.check(s,"shirokai.raids.admin",s.hasPermission(2))).executes(c -> adminRewardMenu(c.getSource(),null));
            admin.then(Commands.literal("menu").executes(c -> adminRewardMenu(c.getSource(),null)));
            admin.then(Commands.literal("recompensas").executes(c -> adminRewardMenu(c.getSource(),null))
                .then(Commands.argument("boss",StringArgumentType.word()).suggests((c,b) -> {adminRewardSource().contexts().stream().map(RaidAdminRewardMenu.Context::id).filter(id -> !id.isEmpty()&&!id.startsWith("@")).forEach(b::suggest);return b.buildFuture();})
                .executes(c -> adminRewardMenu(c.getSource(),StringArgumentType.getString(c,"boss")))));
            admin.then(Commands.literal("iniciar").then(Commands.argument("boss", StringArgumentType.word())
                .suggests((c,b) -> { bosses.keySet().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> start(c.getSource(), StringArgumentType.getString(c,"boss")))));
            admin.then(Commands.literal("parar").executes(c -> { finish(false,msg("textos.raid_encerrada_pela_administracao")); return 1; }));
            admin.then(Commands.literal("sortear").executes(c -> { try {beginAutomatic();return reply(c.getSource(),"Raid sorteada e iniciada. O ciclo automático foi contabilizado.");}catch(Exception e){return reply(c.getSource(),"Sorteio recusado: "+e.getMessage());} }));
            admin.then(Commands.literal("catalogo").executes(c -> catalogStatus(c.getSource())));
            admin.then(Commands.literal("setspawn").executes(c -> setSpawn(c.getSource())));
            admin.then(Commands.literal("rewardreload").executes(c -> rewardReload(c.getSource())));
            admin.then(Commands.literal("rewardstatus").executes(c -> rewardStatus(c.getSource())));
            admin.then(Commands.literal("rewardreview").then(Commands.argument("transaction",StringArgumentType.word())
                .then(Commands.literal("confirmar").then(Commands.argument("motivo",StringArgumentType.greedyString()).executes(c -> rewardReview(c.getSource(),StringArgumentType.getString(c,"transaction"),true,StringArgumentType.getString(c,"motivo")))))
                .then(Commands.literal("repetir").then(Commands.argument("motivo",StringArgumentType.greedyString()).executes(c -> rewardReview(c.getSource(),StringArgumentType.getString(c,"transaction"),false,StringArgumentType.getString(c,"motivo")))))));
            admin.then(Commands.literal("setentrada").executes(c -> setEntry(c.getSource())));
            admin.then(Commands.literal("status").executes(c -> status(c.getSource())));
            admin.then(Commands.literal("info").executes(c -> status(c.getSource())));
            admin.then(Commands.literal("intervalo").then(Commands.argument("segundos", com.mojang.brigadier.arguments.IntegerArgumentType.integer(60,31_536_000))
                .executes(c -> configure(c.getSource(), next -> next.intervalSeconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"segundos"), msg("textos.intervalo_salvo")))));
            admin.then(Commands.literal("duracao").then(Commands.argument("segundos", com.mojang.brigadier.arguments.IntegerArgumentType.integer(30,7200))
                .executes(c -> configure(c.getSource(), next -> next.durationSeconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"segundos"), msg("textos.duracao_salva")))));
            admin.then(Commands.literal("limite").then(Commands.argument("jogadores", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,100))
                .executes(c -> configure(c.getSource(), next -> next.maxConcurrentPlayers = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"jogadores"), msg("textos.limite_salvo_capacidade_precisa_de_ensaio_real")))));
            admin.then(Commands.literal("automatico").then(Commands.argument("ativado", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                .executes(c -> configure(c.getSource(), next -> next.automaticRaids = com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"ativado"), msg("textos.agendamento_automatico_atualizado")))));
            var reservations = Commands.literal("reservas").requires(s -> RaidPermissions.check(s,"shirokai.raids.reconcile",s.hasPermission(2)))
                .executes(c -> listReservations(c.getSource()));
            for (var action : LegacyReservationReview.Action.values()) {
                String label = action == LegacyReservationReview.Action.CONFIRM ? "confirmar" : "devolver";
                reservations.then(Commands.literal(label).then(Commands.argument("id", StringArgumentType.string())
                    .suggests((c,b) -> { if (state != null) state.uncertain.keySet().stream().sorted().limit(100)
                        .map(StringArgumentType::escapeIfRequired).forEach(b::suggest); return b.buildFuture(); })
                    .then(Commands.argument("motivo", StringArgumentType.greedyString()).executes(c -> reconcile(c.getSource(),
                        StringArgumentType.getString(c,"id"), action, StringArgumentType.getString(c,"motivo"))))));
            }
            admin.then(reservations);
            admin.then(Commands.literal("listar").executes(c -> reply(c.getSource(), String.join(", ", bosses.keySet()))));
            admin.then(Commands.literal("reload").executes(c -> {
                if (active != null || victory!=null) return reply(c.getSource(),msg("textos.encerre_a_raid_antes_de_recarregar"));
                try { loadConfig(); return reply(c.getSource(),msg("textos.configuracoes_recarregadas")); }
                catch (Exception e) { LOG.error("Falha de configuração", e); return reply(c.getSource(),msg("config.erro", "erro", e.getMessage())); }
            }));
            dispatcher.register(admin);
        });
    }

    private void loadConfig() throws IOException {
        JsonFiles.defaultFile(configDir.resolve("config.json"), new RaidConfig());
        if (!Files.exists(configDir.resolve("bosses")))
            JsonFiles.defaultFile(configDir.resolve("bosses/rayquaza.json"), new RaidConfig.Boss());
        JsonFiles.defaultFile(configDir.resolve("rewards/default.json"), RaidConfig.Rewards.defaults());
        var next = JsonFiles.read(configDir.resolve("config.json"), RaidConfig.class); next.validate();
        var nextCatalogSettings=RaidCatalogSettings.load(configDir.resolve("catalog.json"));
        var nextRewardSettings = RaidRewardSettings.load(configDir.resolve("rewards-v2"));
        var nextMessages = RaidMessages.load(configDir.resolve("messages.json"));
        var bossMap = RaidBossCatalog.load(configDir.resolve("bosses"),nextCatalogSettings.enabled);
        var rewardMap = new HashMap<String,RaidConfig.Rewards>();
        var rewardIds=new HashSet<String>();bossMap.values().forEach(b -> rewardIds.add(b.rewards));
        if(nextCatalogSettings.enabled)nextCatalogSettings.profiles.values().forEach(p -> rewardIds.add(p.rewards));
        for (var id : rewardIds) {
            var table = JsonFiles.read(configDir.resolve("rewards/" + id + ".json"),RaidConfig.Rewards.class); table.validate();
            rewardMap.put(id,table);
        }
        if(server!=null) { validateRewardItems(rewardMap); validateDestinations(next); }
        config = next; bosses = bossMap; rewards = rewardMap; rewardSettings = nextRewardSettings; catalogSettings=nextCatalogSettings;
        RaidMessages.install(nextMessages);
    }
    private void validateRewardItems(Map<String,RaidConfig.Rewards> tables) {
        for(var table:tables.entrySet()) for(var drop:table.getValue().configuredItems())
            if(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(drop.item)))
                throw new IllegalArgumentException(msg("textos.item_inexistente_na_tabela")+table.getKey()+": "+drop.item);
    }
    private void validateDestinations(RaidConfig next) {
        if (next.spawnConfigured) validateDestination(next.dimension, next.y);
        if (next.entry.configured) validateDestination(next.entry.dimension, next.entry.y);
    }
    private void validateDestination(String dimension, double y) {
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)));
        if (world == null || y <= world.getMinBuildHeight() || y + 2 >= world.getMaxBuildHeight())
            throw new IllegalArgumentException(msg("textos.destino_invalido") + dimension + msg("textos.y") + y);
    }
    private void started(MinecraftServer server) {
        this.server = server;
        statePath = server.getWorldPath(LevelResource.ROOT).resolve("data/shirokai-raids/state.json");
        try {
            validateRewardItems(rewards); validateDestinations(config);
            var loaded = Files.exists(statePath) ? JsonFiles.read(statePath,RaidState.class) : new RaidState(); loaded.validate();
            state = loaded;
            state.captures.clear(); // Capture opportunities never survive a restart or reconnect.
            state.archive.complete(RaidArchive.Outcome.INTERRUPTED, System.currentTimeMillis(), Map.of());
            // Restart recovery cancels the active raid; no repeat rewards or accumulated raids.
            state.activeRaid = null; state.bossEntity = null;
            if (state.nextRaid == 0 || (state.nextRaid < System.currentTimeMillis() && config.restartPolicy.equals("SKIP_MISSED")))
                state.nextRaid = RaidSchedule.next(System.currentTimeMillis(),config.intervalSeconds);
            healthy = true; save();
            rewardDelivery = new RewardDelivery(state, this::save);
            captures = new RaidCapture(server,state,this::save);
            modularRewards = new RaidRewardJournal(state.modularRewards,this::save,server,line -> LOG.info("{}",line));
            modularRewards.register("currency",new ImpactorRaidRewards(() -> rewardSettings.integrations.impactor));
            modularRewards.register("fragment",new CratesRaidRewards(server,() -> rewardSettings.integrations.crates));
            for(var provider:FabricLoader.getInstance().getEntrypoints("shirokai_raids:rewards",RaidRewardHandler.Provider.class)) provider.register(modularRewards::register);
            modularRewards.recover();
            modularRewards.onDelivered(g -> {
                var player=server.getPlayerList().getPlayer(UUID.fromString(g.player));
                if(player!=null) RaidCapture.tell(player,"recompensas.modular_entregue","quantidade",g.choice.amount.toPlainString(),"premio",rewardName(g.choice.reward.type,g.choice.reward.reference),"categoria",g.scope.equals("special")?"TOP 3":"Geral");
            });
            rewardQueue.clear();
        } catch (Exception e) { healthy = false; LOG.error("Raids desativadas: falha ao carregar estado; arquivo preservado",e); }
    }
    private void save() {
        if (!healthy) throw new IllegalStateException(msg("textos.persistencia_indisponivel"));
        try { JsonFiles.write(statePath,state); }
        catch (IOException e) { healthy = false; throw new IllegalStateException(msg("textos.falha_ao_persistir_raids_desativadas_para_proteger_recompensas"),e); }
    }
    public static boolean tryChallenge(com.cobblemon.mod.common.net.messages.server.BattleChallengePacket packet,ServerPlayer player) {
        var mod=instance;
        if(mod!=null && mod.captures!=null && mod.captures.challenge(packet,player)) return true;
        if(mod==null || mod.active==null || player.level().getEntity(packet.getTargetedEntityId())!=mod.active.boss) return false;
        try {
            if (mod.captures!=null && mod.captures.active(player.getUUID())) throw new IllegalStateException(msg("captura.ja_ativa"));
            if(!mod.healthy || !mod.joinedCurrentRaid(player) || !mod.inArena(player) || mod.active.health.current()==0 || !mod.active.boss.canBattle(player))
                throw new IllegalStateException(msg("textos.a_raid_nao_esta_disponivel_para_voce_neste_momento"));
            if(!RaidPermissions.check(player.createCommandSourceStack(),"shirokai.raids.use",true)
                || !RaidPermissions.check(player.createCommandSourceStack(),"shirokai.raids.join",true))
                throw new IllegalStateException(msg("textos.voce_nao_tem_permissao_para_participar_da_raid"));
            if(!com.cobblemon.mod.common.util.PlayerExtensionsKt.canInteractWith(player,mod.active.boss,
                com.cobblemon.mod.common.Cobblemon.config.getBattleWildMaxDistance()))
                throw new IllegalStateException(msg("textos.aproxime_se_do_boss_para_desafia_lo"));
            RaidInventory.requireEmpty(player);
            mod.active.battles.challenge(player,packet.getSelectedPokemonId());
            mod.state.archive.current.participants.put(player.getUUID().toString(), player.getGameProfile().getName());
            mod.snapshot();
        } catch(RuntimeException error) {
            if (!mod.healthy && mod.active != null) {
                mod.cleanup(mod.active);
            }
            player.sendSystemMessage(RaidMessages.chat(error.getMessage()==null?msg("textos.nao_foi_possivel_iniciar_a_batalha"):error.getMessage()));
            LOG.debug("Desafio de raid recusado",error);
        }
        return true;
    }
    public static boolean blockCommand(CommandSourceStack source, String command) {
        var mod = instance;
        if (mod == null || mod.state == null || !source.isPlayer()
            || (!mod.state.returns.containsKey(source.getPlayer().getUUID().toString()) && !RaidInventory.locked(source.getPlayer()))
            || RaidCommands.allowedDuringRaid(command) || (RaidAdminRewardMenu.allowedCommand(command) && RaidAdminRewardMenu.allowed(source.getPlayer()))) return false;
        source.sendFailure(RaidMessages.chat(msg("comandos.bloqueados")));
        return true;
    }
    private boolean isBoss(UUID uuid) { return active != null && active.boss.getUUID().equals(uuid) || victory!=null && victory.animation.known(uuid); }
    private int reply(CommandSourceStack source, String text) { source.sendSuccess(() -> RaidMessages.chat(text),false); return 1; }
    private void broadcast(String text) { server.getPlayerList().broadcastSystemMessage(RaidMessages.chat(text),false); }
    private int info(CommandSourceStack source) {
        if (!healthy) return reply(source,msg("textos.raids_indisponiveis_verifique_os_logs_de_persistencia"));
        if (active == null && !config.automaticRaids) return reply(source, msg("textos.raids_automaticas_desativadas_aguarde_um_evento_manual"));
        return reply(source,active == null ? msg("raid.proxima", "tempo", RaidTime.format(Math.max(0,(state.nextRaid-System.currentTimeMillis())/1000)))
            : msg("raid.estado", "boss", active.definition.name, "vida", active.health.current(), "vida_maxima", active.health.maximum(), "tempo", RaidTime.format(Math.max(0,(active.ends-System.currentTimeMillis())/1000))));
    }
    private int raidCommand(CommandSourceStack source) {
        if (!source.isPlayer()) return info(source);
        var player = source.getPlayer();
        RaidOverviewMenu.open(player, this::overview, () -> join(player.createCommandSourceStack()),
            () -> leave(player.createCommandSourceStack()), () -> raidCommand(player.createCommandSourceStack()));
        return 1;
    }
    private List<String> overview() {
        var lines = new ArrayList<String>();
        if (!healthy) lines.add(msg("textos.raids_temporariamente_indisponiveis"));
        else if (active == null) lines.add(config.automaticRaids
            ? msg("raid.proxima", "tempo", RaidTime.format(Math.max(0,(state.nextRaid-System.currentTimeMillis())/1000)))
            : msg("textos.aguardando_evento_manual"));
        else {
            lines.add(msg("menu.boss", "boss", active.definition.name));
            lines.add(msg("menu.vida", "vida", active.health.current(), "vida_maxima", active.health.maximum()));
            lines.add(msg("menu.tempo", "tempo", RaidTime.format(Math.max(0,(active.ends-System.currentTimeMillis())/1000))));
            lines.add(msg("menu.combatentes", "jogadores", active.health.snapshot().activeSessions(), "limite", config.maxConcurrentPlayers));
        }
        lines.add(msg("textos.entre_para_enfrentar_o_boss_dentro_da_arena"));
        lines.add(msg("textos.sair_devolve_voce_ao_local_anterior_a_entrada"));
        return lines;
    }
    private int teleportToArena(CommandSourceStack source) {
        if (!healthy || active == null) return info(source);
        if (!source.isPlayer()) return info(source);
        if (!RaidPermissions.check(source,"shirokai.raids.join",true)) {
            source.sendFailure(RaidMessages.chat(msg("textos.voce_nao_tem_permissao_para_entrar_na_arena"))); return 0;
        }
        var player=source.getPlayer();
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
            || com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            source.sendFailure(RaidMessages.chat(msg("textos.encerre_sua_batalha_e_saia_de_veiculos_antes_de_usar_raid"))); return 0;
        }
        if (tick < teleportCooldown.getOrDefault(player.getUUID(),0L)) {
            source.sendFailure(RaidMessages.chat(msg("textos.aguarde_cinco_segundos_entre_teleportes"))); return 0;
        }
        teleportCooldown.put(player.getUUID(),tick+100);
        var raid=active;
        if (raid.boss.isRemoved() || !raid.boss.isAlive() || server.getLevel(raid.world.dimension()) != raid.world) {
            source.sendFailure(RaidMessages.chat(msg("textos.a_arena_nao_esta_disponivel"))); return 0;
        }
        ServerLevel destinationWorld=raid.world;
        Vec3 destination;
        float yaw,pitch;
        if(config.entry.configured) {
            destinationWorld=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(config.entry.dimension)));
            destination=new Vec3(config.entry.x,config.entry.y,config.entry.z);
            if(destinationWorld==null || !destinationWorld.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(destination)))
                { source.sendFailure(RaidMessages.chat(msg("entrada.indisponivel")));return 0; }
            destinationWorld.getChunkAt(net.minecraft.core.BlockPos.containing(destination));
            if(!safeBodyAt(destinationWorld,player,destination,destinationWorld==raid.world?raid.boss:null,true))
                { source.sendFailure(RaidMessages.chat(msg("entrada.indisponivel")));return 0; }
            yaw=config.entry.yaw;pitch=config.entry.pitch;
        } else {
            var spot=SafeTeleportSearch.find(raid.boss.getX(),raid.center.y,raid.boss.getZ(),config.arenaRadius,
                player.getUUID().hashCode(),candidate -> safeSpot(raid,player,candidate));
            if(spot.isEmpty()) { source.sendFailure(RaidMessages.chat(msg("textos.nao_ha_um_local_seguro_e_carregado_proximo_ao_boss_avise_a_administracao")));return 0; }
            var target=spot.get();destination=new Vec3(target.x()+.5,target.feetY(),target.z()+.5);
            yaw=(float)(Math.toDegrees(Math.atan2(raid.boss.getZ()-destination.z,raid.boss.getX()-destination.x))-90);pitch=0;
        }
        String key = player.getUUID().toString();
        if (captures != null && captures.active(player.getUUID())) return reply(source,msg("captura.ja_ativa"));
        if (state.returns.containsKey(key)) return reply(source,msg("textos.voce_ja_entrou_use_raid_sair_antes_de_entrar_novamente"));
        if(!raid.rewardParticipants.contains(player.getUUID()) && !RaidRewardJournal.hasCapacity(raid.rewardPendingAtStart,raid.rewardParticipants.size()+1,raid.rewardSettings.integrations))
            return reply(source,"Não há capacidade para novas recompensas nesta raid. Avise a administração.");
        try { RaidInventory.requireEmpty(player); } catch(IllegalStateException error) { return reply(source,error.getMessage()); }
        state.returns.put(key, new RaidState.ReturnPosition(player.serverLevel().dimension().location().toString(),
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        state.returns.get(key).raid=raid.id;
        save(); // Persist the origin before moving the player; never move or store personal items.
        raid.rewardParticipants.add(player.getUUID());
        player.teleportTo(destinationWorld,destination.x,destination.y,destination.z,Set.of(),yaw,pitch);
        player.fallDistance=0;
        if(inArena(player)) raid.bar.addPlayer(player);
        return reply(source,msg("textos.voce_chegou_a_arena_envie_seu_pokemon_e_desafie_o_boss_com_r_para_escolher_"));
    }
    private boolean safeSpot(Active raid,ServerPlayer player,SafeTeleportSearch.Spot spot) {
        var world=raid.world;
        var feet=new net.minecraft.core.BlockPos(spot.x(),spot.feetY(),spot.z());
        if (new Vec3(spot.x()+.5,spot.feetY(),spot.z()+.5).distanceToSqr(raid.center) > config.arenaRadius*config.arenaRadius
            || spot.feetY() <= world.getMinBuildHeight() || spot.feetY()+player.getBbHeight() >= world.getMaxBuildHeight()
            || !world.hasChunkAt(feet) || !world.getWorldBorder().isWithinBounds(feet)) return false;
        return safeBody(world, player, feet, raid.boss);
    }
    private boolean safeBody(ServerLevel world, ServerPlayer player, net.minecraft.core.BlockPos feet, net.minecraft.world.entity.Entity boss) {
        return safeBodyAt(world,player,new Vec3(feet.getX()+.5,feet.getY(),feet.getZ()+.5),boss);
    }
    private boolean safeBodyAt(ServerLevel world,ServerPlayer player,Vec3 position,net.minecraft.world.entity.Entity boss) {
        return safeBodyAt(world,player,position,boss,false);
    }
    private boolean safeBodyAt(ServerLevel world,ServerPlayer player,Vec3 position,net.minecraft.world.entity.Entity boss,boolean allowPlayers) {
        var feet=net.minecraft.core.BlockPos.containing(position);
        if (feet.getY() <= world.getMinBuildHeight() || feet.getY() + player.getBbHeight() >= world.getMaxBuildHeight()
            || !world.hasChunkAt(feet) || !world.getWorldBorder().isWithinBounds(feet)) return false;
        var floor=feet.below();
        if (!world.getBlockState(floor).isFaceSturdy(world,floor,net.minecraft.core.Direction.UP)) return false;
        for(var pos:List.of(floor,feet,feet.above())) {
            var state=world.getBlockState(pos);
            if(!state.getFluidState().isEmpty() || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.CACTUS) || state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE) || state.is(net.minecraft.world.level.block.Blocks.FIRE)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE) || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE) || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return false;
        }
        var body=net.minecraft.world.phys.AABB.ofSize(new Vec3(position.x,position.y+player.getBbHeight()/2.0,position.z),
            player.getBbWidth(),player.getBbHeight(),player.getBbWidth());
        return body.minX >= world.getWorldBorder().getMinX() && body.maxX <= world.getWorldBorder().getMaxX()
            && body.minZ >= world.getWorldBorder().getMinZ() && body.maxZ <= world.getWorldBorder().getMaxZ()
            && world.noCollision(player,body) && (boss == null || !boss.getBoundingBox().inflate(2).intersects(body))
            && world.getEntities(player,body,entity -> entity.isAlive() && entity.isPickable() && (!allowPlayers || !(entity instanceof ServerPlayer))).isEmpty();
    }
    private void cleanup(Active raid) {
        try { if (raid.battles != null) raid.battles.close(); }
        finally {
            try { raid.bar.removeAllPlayers(); RaidPresentation.clearGlow(raid.boss); raid.boss.discard(); }
            finally { if (raid.chunks != null) raid.chunks.close(); if (active == raid) active = null; }
        }
    }
    private int setSpawn(CommandSourceStack source) {
        try {
            var player = source.getPlayerOrException();
            return configure(source, next -> {
                next.dimension = player.serverLevel().dimension().location().toString();
                next.x = player.getX(); next.y = player.getY(); next.z = player.getZ(); next.spawnConfigured = true;
                next.spawnYaw = player.getYRot(); next.spawnPitch = player.getXRot();
            }, msg("textos.arena_configurada_mantenha_o_local_acessivel_e_livre_de_construcoes"));
        } catch (Exception e) { return reply(source,msg("textos.nao_foi_possivel_salvar_a_arena") + e.getMessage()); }
    }
    private int setEntry(CommandSourceStack source) {
        try {
            var player = source.getPlayerOrException();
            if (!safeBodyAt(player.serverLevel(), player, player.position(), null,true))
                return reply(source, msg("entrada.insegura"));
            return configure(source, next -> {
                next.entry.configured = true; next.entry.dimension = player.serverLevel().dimension().location().toString();
                next.entry.x = player.getX(); next.entry.y = player.getY(); next.entry.z = player.getZ();
                next.entry.yaw = player.getYRot(); next.entry.pitch = player.getXRot();
            }, msg("entrada.salva"));
        } catch (Exception e) { return reply(source, msg("entrada.erro") + e.getMessage()); }
    }
    private int configure(CommandSourceStack source, java.util.function.Consumer<RaidConfig> change, String message) {
        if (active != null || victory!=null) return reply(source, msg("textos.encerre_a_raid_antes_de_alterar_a_configuracao"));
        try {
            var next = JsonFiles.copy(config, RaidConfig.class); change.accept(next); next.validate(); validateDestinations(next);
            JsonFiles.write(configDir.resolve("config.json"), next);
            boolean reschedule = config.intervalSeconds != next.intervalSeconds || config.automaticRaids != next.automaticRaids;
            config = next;
            if (reschedule && healthy) { state.nextRaid = RaidSchedule.next(System.currentTimeMillis(), config.intervalSeconds); save(); }
            return reply(source, message);
        } catch (Exception error) {
            LOG.error("Falha ao atualizar configuração", error);
            source.sendFailure(RaidMessages.chat(msg("textos.configuracao_nao_concluida") + error.getMessage())); return 0;
        }
    }
    private int status(CommandSourceStack source) {
        reply(source, msg("textos.persistencia") + (healthy ? msg("textos.disponivel") : msg("textos.indisponivel")) + msg("textos.automatico") + config.automaticRaids
            + msg("textos.intervalo") + RaidTime.format(config.intervalSeconds) + msg("textos.duracao") + RaidTime.format(config.durationSeconds) + msg("textos.limite") + config.maxConcurrentPlayers);
        reply(source, msg("textos.arena") + (config.spawnConfigured ? config.dimension + " " + config.x + ", " + config.y + ", " + config.z : msg("textos.use_raidadmin_setspawn"))
            + msg("entrada.rotulo") + (config.entry.configured ? config.entry.dimension : msg("entrada.comando")));
        if (active != null) reply(source, msg("textos.evento") + active.id + msg("textos.sessoes") + active.health.snapshot().activeSessions()
            + msg("textos.raio_do_ticket") + active.chunks.radius() + " chunks");
        if (state != null) reply(source, msg("textos.pendencias") + state.pending.values().stream().mapToInt(List::size).sum()
            + msg("textos.reservas_recuperaveis") + state.deliveries.size() + msg("textos.reservas_antigas_incertas") + state.uncertain.size());
        var runtime = Runtime.getRuntime();
        reply(source, String.format(Locale.ROOT, "MSPT médio: %.2f; heap usado/máximo: %d/%d MiB. Capacidade depende de ensaio real.",
            server.getAverageTickTimeNanos()/1_000_000.0, (runtime.totalMemory()-runtime.freeMemory())/1_048_576, runtime.maxMemory()/1_048_576));
        return 1;
    }
    private int listReservations(CommandSourceStack source) {
        if (!healthy) return reply(source, msg("textos.conferencia_indisponivel_corrija_a_persistencia_primeiro"));
        reply(source, msg("textos.reservas_antigas_incertas_2") + state.uncertain.size() + msg("textos.so_devolver_apos_comprovar_que_os_itens_nao_foram_recebidos"));
        state.uncertain.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(20).forEach(entry ->
            reply(source, entry.getKey() + " — " + entry.getValue().count + "x " + entry.getValue().item + msg("textos.raid") + entry.getValue().raid));
        return 1;
    }
    private int reconcile(CommandSourceStack source, String id, LegacyReservationReview.Action action, String reason) {
        if (!healthy) return reply(source, msg("textos.conferencia_indisponivel_corrija_a_persistencia_primeiro"));
        try {
            String actor = source.isPlayer() ? source.getPlayer().getUUID().toString() : "console";
            LegacyReservationReview.resolve(state, id, action, actor, reason, System.currentTimeMillis());
            save();
            LOG.warn("Reserva antiga conferida: id={}, ação={}, operador={}, motivo={}", id, action, actor, reason);
            return reply(source, action == LegacyReservationReview.Action.CONFIRM ? msg("textos.recebimento_confirmado_e_auditado_nenhum_item_adicional_entregue")
                : msg("textos.itens_voltaram_as_pendencias_uma_unica_vez_conferencia_auditada"));
        } catch (Exception error) { source.sendFailure(RaidMessages.chat(msg("textos.conferencia_nao_concluida") + error.getMessage())); return 0; }
    }
    private int start(CommandSourceStack source, String id) {
        try { begin(id); return reply(source,msg("textos.raid_iniciada")); }
        catch (Exception e) { LOG.error("Falha ao iniciar raid",e); return reply(source,msg("raid.erro_inicio", "erro", e.getMessage())); }
    }
    private int catalogStatus(CommandSourceStack source) {
        if(state==null)return reply(source,"Catálogo indisponível.");
        var species=RaidCatalogPokemon.species(catalogSettings);
        var counts=new TreeMap<String,Long>();
        species.stream().filter(p -> RaidAutomaticCatalog.eligible(p,catalogSettings)).forEach(p -> counts.merge(RaidAutomaticCatalog.difficulty(p),1L,Long::sum));
        reply(source,"Catálogo: "+counts+" | raids automáticas iniciadas: "+state.automaticRaidStarts+" | próxima especial em "+(20-state.automaticRaidStarts%20)+" raid(s).");
        return 1;
    }
    private void beginAutomatic() {
        if(!catalogSettings.enabled){begin(RaidBossCatalog.randomId(bosses,random));return;}
        var draw=RaidAutomaticCatalog.draw(RaidCatalogPokemon.species(catalogSettings),catalogSettings,state.automaticRaidStarts,state.catalogRecentNormal,state.catalogRecentSpecial,random);
        begin(draw.boss(),draw);
    }
    private void begin(String id) {
        var definition=bosses.get(id);
        if(definition==null)throw new IllegalArgumentException(msg("textos.boss_desconhecido"));
        begin(definition,null);
    }
    private void begin(RaidConfig.Boss definition,RaidAutomaticCatalog.Draw automaticDraw) {
        if (!healthy || active != null || victory!=null) throw new IllegalStateException(msg("textos.raids_indisponiveis_ou_ja_existe_uma_raid_ativa"));
        if (captures!=null && captures.anyActive()) throw new IllegalStateException(msg("captura.aguarde_fases"));
        if (!config.spawnConfigured) throw new IllegalStateException(msg("textos.use_raidadmin_setspawn_primeiro"));
        validateRewardItems(Map.of(definition.rewards,rewards.get(definition.rewards)));
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(config.dimension)));
        if (world == null) throw new IllegalArgumentException(msg("textos.dimensao_inexistente"));
        if (config.y < world.getMinBuildHeight() || config.y >= world.getMaxBuildHeight()) throw new IllegalArgumentException(msg("textos.altura_de_arena_invalida"));
        long rewardPendingAtStart=state.modularRewards.grants.values().stream().filter(g -> g.status!=RaidRewardJournal.Status.DONE && g.status!=RaidRewardJournal.Status.SKIPPED).count();
        if(!RaidRewardJournal.hasCapacity(rewardPendingAtStart,config.maxConcurrentPlayers,rewardSettings.integrations))
            throw new IllegalStateException("Limite de pendências: resolva as entregas antes de iniciar outra raid.");
        var center = net.minecraft.core.BlockPos.containing(config.x, config.y, config.z);
        if (!world.getWorldBorder().isWithinBounds(center)) throw new IllegalArgumentException(msg("textos.arena_fora_da_borda_do_mundo"));
        var lease = new RaidChunkLease(world, new net.minecraft.world.level.ChunkPos(center), config.arenaRadius);
        boolean retained = false;
        try {
        world.getChunkAt(center);
        var properties = PokemonProperties.Companion.parse(definition.species + " level=" + definition.level + " shiny=" + definition.shiny + " uncatchable=yes" + (definition.form.isEmpty() ? "" : " form=" + definition.form));
        var entity = properties.createEntity(world);
        if (!entity.getPokemon().getSpecies().getResourceIdentifier().equals(ResourceLocation.parse(definition.species.contains(":")?definition.species:"cobblemon:"+definition.species)))
            throw new IllegalArgumentException(msg("textos.especie_invalida_ou_indisponivel"));
        RaidCatalogPokemon.train(entity.getPokemon(),definition);
        entity.getPokemon().setScaleModifier(definition.scale);
        entity.setCustomName(RaidMessages.component(definition.name));
        entity.setPersistenceRequired(); entity.setInvulnerable(true);
        entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(),false);
        entity.setNoAi(true);
        entity.addTag("shirokai_raids_boss");
        entity.addTag("shirokai_raid_boss");
        entity.moveTo(config.x,config.y,config.z,config.spawnYaw,config.spawnPitch);
        var candidate = new Active(definition,entity,world,new Vec3(config.x,config.y,config.z),System.currentTimeMillis()+config.durationSeconds*1000L,config.maxConcurrentPlayers);
        candidate.rewardPendingAtStart=rewardPendingAtStart;
        candidate.rewardSettings=JsonFiles.copy(rewardSettings,RaidRewardSettings.class);
        candidate.legacyRewards=JsonFiles.copy(rewards.get(definition.rewards),RaidConfig.Rewards.class);
        candidate.chunks = lease;
        candidate.battles=new TraditionalRaidBattles(entity,candidate.health,definition.damageMultiplier,hit ->
            candidate.damage.record(hit.player(),hit.name(),hit.pokemon(),hit.applied(),System.currentTimeMillis()),
            player -> inArena(player) && joinedCurrentRaid(player));
        active = candidate; // entity load callback must recognize it before insertion
        RaidPresentation.glow(entity, false);
        if (!world.addFreshEntity(entity)) { RaidPresentation.clearGlow(entity); active = null; throw new IllegalStateException(msg("textos.spawn_recusado")); }
        state.activeRaid = candidate.id; state.bossEntity = entity.getUUID().toString(); state.hp = definition.hp; state.damage.clear();
        state.archive.current = new RaidArchive.Event(candidate.id, definition.name, definition.species, System.currentTimeMillis(), definition.hp);
        if(automaticDraw!=null) RaidAutomaticCatalog.record(state,automaticDraw,catalogSettings);
        try { save(); } catch (RuntimeException e) { RaidPresentation.clearGlow(entity); entity.discard(); active = null; throw e; }
        retained = true;
        countdownBar.removeAllPlayers();
        broadcast(msg("raid.spawn", "boss", definition.name));
        } catch (RuntimeException error) {
            if (active != null && active.chunks == lease) cleanup(active);
            throw error;
        } finally { if (!retained) lease.close(); }
    }
    private boolean joinedCurrentRaid(ServerPlayer player) {
        var origin=state.returns.get(player.getUUID().toString());
        return active!=null && origin!=null && active.id.equals(origin.raid);
    }
    private boolean inArena(ServerPlayer player) {
        return active != null && player.isAlive() && !player.isSpectator() && player.serverLevel() == active.world && player.position().distanceToSqr(active.center) <= config.arenaRadius*config.arenaRadius;
    }
    public static boolean participating(ServerPlayer player) {
        return instance!=null && instance.state!=null && instance.state.returns.containsKey(player.getUUID().toString());
    }
    static void inventoryRestored(ServerPlayer player) {
        if (instance!=null && instance.healthy && !RaidInventory.locked(player)) instance.rewardQueue.add(player.getUUID());
    }
    public static boolean isCaptureActor(com.cobblemon.mod.common.api.battles.model.actor.BattleActor actor) {
        return instance!=null && instance.captures!=null && actor.getPokemonList().stream().anyMatch(p -> instance.captures.capturePokemon(p.getEffectedPokemon().getUuid()));
    }
    static RaidCapture captureManager() { return instance == null ? null : instance.captures; }
    static void throwCaptureBall(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        var mod=instance;
        if (mod==null || !mod.healthy || mod.captures==null) return;
        try { mod.captures.throwBall(player,hand); }
        catch(Exception error) { LOG.error("Tentativa de captura suspensa",error); }
    }
    public static boolean readOnlyRaidMenu(ServerPlayer player) {
        return player.containerMenu instanceof RaidOverviewMenu || player.containerMenu instanceof RaidMenu
            || player.containerMenu instanceof PendingRewardMenu;
    }
    private int join(CommandSourceStack source) {
        return teleportToArena(source);
    }
    private int leave(CommandSourceStack source) {
        if (!source.isPlayer()) return reply(source,msg("textos.comando_disponivel_para_jogadores"));
        if (!healthy) return reply(source,msg("textos.saida_indisponivel_confira_a_persistencia"));
        var player = source.getPlayer();
        try { RaidLegacyInventory.recover(player); } catch(Exception error) { return reply(source,error.getMessage()); }
        String key = player.getUUID().toString();
        var origin = state.returns.get(key);
        if (origin == null) return reply(source,msg("textos.voce_nao_entrou_na_raid_use_raid_entrar"));
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(origin.dimension)));
        if (world == null) return reply(source,msg("textos.a_dimensao_de_retorno_esta_indisponivel_sua_posicao_foi_preservada_avise_a_"));
        if (!player.isAlive() || player.isPassenger()) return reply(source,msg("textos.voce_precisa_estar_vivo_e_fora_de_veiculos_para_sair"));
        if (victory!=null) victory.animation.removeViewer(player);
        if (active != null) { active.battles.leave(player.getUUID()); active.bar.removePlayer(player); }
        try { if (captures != null) captures.close(player,true); }
        catch(Exception error) { return reply(source,error.getMessage()); }
        if (com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) != null)
            return reply(source,msg("textos.encerre_sua_batalha_atual_antes_de_retornar"));
        player.closeContainer();
        player.teleportTo(world,origin.x,origin.y,origin.z,Set.of(),origin.yaw,origin.pitch);
        player.fallDistance=0;
        state.returns.remove(key);
        state.arenaExits.remove(key);
        inventoryRestored(player);
        if (active != null) snapshot(); else save();
        return reply(source,msg("raid.retorno"));
    }
    private int damage(CommandSourceStack source) {
        try { var p=source.getPlayerOrException(); return reply(source,active == null ? msg("textos.nenhuma_raid_ativa") : msg("textos.dano_aplicado") + active.damage.damage(p.getUUID()) + String.format(Locale.ROOT," (%.2f%%)",100*active.damage.contribution(p.getUUID(),active.health.maximum()))); }
        catch (Exception e) { return reply(source,msg("textos.comando_disponivel_para_jogadores")); }
    }
    private int ranking(CommandSourceStack source) {
        if (active == null) return reply(source,msg("textos.nenhuma_raid_ativa"));
        active.damage.ranking().stream().limit(10).forEach(e -> reply(source,e.name() + ": " + e.damage() + " HP")); return 1;
    }
    private void updateCountdown(long now) {
        if (!healthy || active != null || victory!=null || captures!=null && captures.anyActive() || !config.spawnConfigured || !config.automaticRaids) {
            countdownBar.removeAllPlayers();
            return;
        }
        long remaining = Math.max(0, state.nextRaid - now);
        countdownBar.setName(RaidMessages.component(msg("bossbar.proxima", "tempo", RaidTime.format((remaining + 999) / 1000))));
        countdownBar.setProgress(RaidSchedule.countdownProgress(remaining, config.intervalSeconds));
        for (var player : server.getPlayerList().getPlayers()) countdownBar.addPlayer(player);
    }
    private void tick() {
        tick++;
        if (server == null) return;
        if (!healthy) { cancelVictory(); countdownBar.removeAllPlayers(); if (captures!=null) captures.shutdown(); return; }
        try {
            if (captures != null) captures.tick();
            advanceVictory();
            retryRewards();
            if(tick%20==0 && modularRewards!=null) modularRewards.tick(rewardSettings.integrations.deliveriesPerTick);
            if(active!=null) {
                active.battles.tick();
                if(active.health.current()==0) { finish(true,msg("textos.boss_derrotado_recompensas_disponiveis_em_raid_recompensas")); return; }
                if (tick % 10 == 0) {
                    boolean lowHealthFlash = active.health.fraction() <= 0.20 && (tick / 10) % 2 == 0;
                    active.bar.setColor(lowHealthFlash ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.RED);
                }
            }
            if (tick % 20 != 0) return;
            long now = System.currentTimeMillis();
            returnExpiredPlayers(now);
            updateCountdown(now);
            if (active != null) {
                if (now >= active.ends) { finish(RaidArchive.Outcome.TIMEOUT,msg("textos.raid_encerrada_por_tempo")); return; }
                if (active.boss.isRemoved() || !active.boss.isAlive() || active.boss.level() != active.world
                    || server.getLevel(active.world.dimension()) != active.world) { finish(false,msg("textos.boss_removido_ou_deslocado_raid_cancelada_com_seguranca")); return; }
                if (active.boss.position().distanceToSqr(active.center) > config.arenaRadius*config.arenaRadius)
                    active.boss.teleportTo(active.center.x,active.center.y,active.center.z);
                active.bar.setProgress((float) active.health.fraction());
                active.bar.setName(RaidMessages.component(msg("bossbar.titulo", "boss", active.definition.name, "vida", active.health.current(), "vida_maxima", active.health.maximum(), "tempo", RaidTime.format(Math.max(0,(active.ends-now)/1000)))));
                for (var player : server.getPlayerList().getPlayers()) {
                    if (inArena(player) && joinedCurrentRaid(player)) active.bar.addPlayer(player);
                    else { active.bar.removePlayer(player); active.battles.leave(player.getUUID()); }
                }
                if (tick % 200 == 0) snapshot();
            } else if (victory==null && config.spawnConfigured && config.automaticRaids && (captures==null || !captures.anyActive())) {
                if (RaidSchedule.due(now,state.nextRaid,false)) {
                    try { beginAutomatic(); }
                    catch (Exception e) { LOG.warn("Raid automática adiada",e); state.nextRaid=RaidSchedule.next(now,config.intervalSeconds); save(); }
                }
                updateCountdown(now);
            }
        } catch (Exception e) { LOG.error("Raid suspensa após falha; verifique o estado persistente",e); healthy=false; countdownBar.removeAllPlayers(); cancelVictory(); if(active!=null) cleanup(active); }
    }
    private void snapshot() {
        capture(active);
        save();
    }
    private void capture(Active raid) {
        state.hp = raid.health.current(); state.damage.clear();
        state.archive.current.damage.clear();
        for (var e : raid.damage.ranking()) {
            String key = e.player().toString();
            state.damage.put(key, e.damage());
            state.archive.current.damage.put(key, e.damage());
            state.archive.current.participants.put(key, e.name());
        }
    }
    private void finish(boolean defeated,String message) {
        finish(defeated ? RaidArchive.Outcome.VICTORY : RaidArchive.Outcome.CANCELLED, message);
    }
    private void finish(RaidArchive.Outcome outcome, String message) {
        if(active==null) { if(victory!=null && outcome!=RaidArchive.Outcome.VICTORY){cancelVictory();if(!message.isBlank())broadcast(message);}return; }
        var raid=active;
        boolean deferred=false;
        boolean defeated = outcome == RaidArchive.Outcome.VICTORY;
        long victoryTime=System.currentTimeMillis();
        Set<UUID> present=new HashSet<>();
        if (defeated) for (var player:server.getPlayerList().getPlayers())
            if (inArena(player) && joinedCurrentRaid(player)) present.add(player.getUUID());
        try {
            capture(raid);
            var drops = new HashMap<String, Map<String, Integer>>();
            if(defeated && !state.completed.contains(raid.id)) {
                modularRewards.award(raid.id,raid.definition.id,raid.definition.difficulty,raid.health.maximum(),raid.damage.ranking(),raid.rewardSettings,random);
                var table=raid.legacyRewards;
                for(var entry:raid.damage.ranking()) {
                    if(!raid.rewardSettings.integrations.legacyItems) continue;
                    double contribution=raid.damage.contribution(entry.player(),raid.health.maximum());
                    if(contribution<table.minimumContribution) continue;
                    for(var drop:RaidLootRoller.roll(table,contribution,random)) {
                        state.pending.computeIfAbsent(entry.player().toString(),k->new ArrayList<>()).add(new RaidState.Pending(raid.id,drop.item(),drop.count()));
                        drops.computeIfAbsent(entry.player().toString(), key -> new TreeMap<>()).merge(drop.item(), drop.count(), Integer::sum);
                    }
                }
                if (captures != null) captures.award(raid.id,raid.definition,config,raid.damage.ranking(),present,victoryTime);
                for(var entry:state.returns.entrySet()) if(raid.id.equals(entry.getValue().raid)) {
                    var exit=new RaidState.ArenaExit();exit.deadline=victoryTime+600_000L;
                    exit.dimension=raid.world.dimension().location().toString();exit.x=raid.center.x;exit.y=raid.center.y;exit.z=raid.center.z;exit.radius=config.arenaRadius;
                    state.arenaExits.put(entry.getKey(),exit);
                }
                state.completed.add(raid.id);
            }
            state.archive.complete(outcome, System.currentTimeMillis(), drops);
            // Start a full interval only when this boss event ends, never at spawn.
            state.nextRaid = RaidSchedule.next(victoryTime, config.intervalSeconds);
            state.activeRaid=null; state.bossEntity=null; save();
            if (defeated) {
                raid.battles.close();
                var animation=new RaidVictoryTransition(raid.boss,raid.chunks,raid.bar);
                victory=new Victory(raid.id,Set.copyOf(present),animation);active=null;deferred=true;
                animation.begin(raid.definition.name,server.getPlayerList().getPlayers().stream().filter(p -> present.contains(p.getUUID())).toList());
            }
        } finally { if(!deferred) cleanup(raid); }
        if (!defeated && !message.isBlank()) broadcast(message);
        if (defeated) {
            for (var entry:raid.damage.ranking()) rewardQueue.add(entry.player());
        }
    }
    private void advanceVictory() {
        var current=victory;
        if(current==null || !current.animation.tick())return;
        victory=null;
        try {
            for(var player:server.getPlayerList().getPlayers()) if(current.present.contains(player.getUUID()) && captures!=null && captures.hasGrant(player)) {
                var origin=state.returns.get(player.getUUID().toString());
                if(origin==null || !current.raid.equals(origin.raid)){captures.close(player,true);continue;}
                try {captures.start(player);}
                catch(Exception error){captures.close(player,RaidCapture.Outcome.CANCELLED);LOG.warn("Captura não iniciada após transição",error);}
            }
        } finally {current.animation.close();}
    }
    private void cancelVictory() {
        var current=victory;if(current==null)return;victory=null;
        try{current.animation.close();}finally{
            boolean removed=state.captures.values().removeIf(g -> current.raid.equals(g.raid));
            if(removed&&healthy)save();
        }
    }
    private void returnExpiredPlayers(long now) {
        for(var player:List.copyOf(server.getPlayerList().getPlayers())) {
            var exit=state.arenaExits.get(player.getUUID().toString());
            if(exit==null || !exit.due(now,player.serverLevel().dimension().location().toString(),player.getX(),player.getY(),player.getZ()) || !player.isAlive()) continue;
            if(captures!=null) captures.close(player,true);
            var battle=com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
            if(battle!=null) battle.stop();
            player.stopRiding();
            leave(player.createCommandSourceStack());
            if(!state.returns.containsKey(player.getUUID().toString())) RaidCapture.tell(player,"raid.retorno_automatico");
        }
    }
    private int openRaidMenu(CommandSourceStack source, boolean global) {
        if (!healthy) return reply(source, msg("textos.historico_indisponivel_confira_a_persistencia"));
        try {
            var player = source.getPlayerOrException();
            if (!RaidMenu.allowed(player, global)) return reply(source, msg("textos.voce_nao_tem_permissao_para_abrir_este_menu"));
            RaidMenu.open(player, state, java.time.ZoneId.of(config.menuTimeZone), global, RaidArchive.Metric.PARTICIPATIONS, 0,
                () -> healthy);
            return 1;
        } catch (Exception error) { return reply(source, msg("textos.menu_disponivel_para_jogadores_com_permissao")); }
    }
    private int pendingMenu(CommandSourceStack source) {
        try { openPendingMenu(source.getPlayerOrException()); return 1; }
        catch (Exception error) { return reply(source, msg("textos.menu_disponivel_para_jogadores_com_persistencia_e_permissao_disponiveis")); }
    }
    static void openPendingMenu(ServerPlayer player) {
        var mod = instance;
        if (mod == null || !mod.healthy || !PendingRewardMenu.allowed(player)) {
            player.sendSystemMessage(RaidMessages.chat(msg("textos.menu_de_recompensas_indisponivel_neste_momento"))); return;
        }
        PendingRewardMenu.open(player, mod.state, java.time.ZoneId.of(mod.config.menuTimeZone), 0, () -> mod.healthy,
            () -> mod.claim(player.createCommandSourceStack()));
    }
    static String rewardName(String type,String reference) {
        return reference.equals("impactor:dollars")?"Dollars":reference.equals("impactor:tokens")?"Tokens":type.equals("fragment")?"Fragmentos de chave ("+reference+")":reference;
    }
    private int adminRewardMenu(CommandSourceStack source,String boss) {
        try {
            if(!healthy) return reply(source,"Prévia indisponível: confira a persistência do servidor.");
            var player=source.getPlayerOrException();
            RaidAdminRewardMenu.open(player,this::adminRewardSource,()->healthy,boss);return 1;
        }catch(Exception error){return reply(source,"Não foi possível abrir a prévia: "+error.getMessage());}
    }
    private RaidAdminRewardMenu.Source adminRewardSource() {
        var contexts=new ArrayList<RaidAdminRewardMenu.Context>();
        contexts.add(new RaidAdminRewardMenu.Context("","Padrão da dificuldade",null,null,null,null,""));
        if(active!=null)contexts.add(new RaidAdminRewardMenu.Context("@active","Raid em andamento: "+active.definition.name,active.definition.difficulty,active.definition.rewards,active.rewardSettings,active.legacyRewards,active.definition.id));
        bosses.values().stream().sorted(Comparator.comparing(b->b.id)).forEach(b->contexts.add(new RaidAdminRewardMenu.Context(b.id,b.name,b.difficulty,b.rewards,null,null,b.id)));
        Map<String,RaidAutomaticCatalog.Species> auto=new HashMap<>();
        if(rewardSettings.bosses.values.keySet().stream().anyMatch(id->id.startsWith("auto_")))
            for(var species:RaidCatalogPokemon.species(catalogSettings))auto.put("auto_"+species.id().substring(species.id().indexOf(':')+1).replaceAll("[^a-z0-9_-]","_"),species);
        for(var id:new TreeSet<>(rewardSettings.bosses.values.keySet()))if(!bosses.containsKey(id)){
            var species=auto.get(id);String difficulty=species==null?null:RaidAutomaticCatalog.difficulty(species);
            String legacyTable=difficulty==null?null:catalogSettings.profiles.get(difficulty).rewards;
            contexts.add(new RaidAdminRewardMenu.Context(id,species==null?"Override: "+id:species.name()+" (override)",difficulty,legacyTable,null,null,id));
        }
        var tables=new HashMap<String,String>();catalogSettings.profiles.forEach((difficulty,profile)->tables.put(difficulty,profile.rewards));
        var handlers=modularRewards==null?Map.<String,RaidRewardHandler>of():modularRewards.handlers();
        return new RaidAdminRewardMenu.Source(rewardSettings,Map.copyOf(rewards),Map.copyOf(tables),List.copyOf(contexts),(type,reference)->{
            if(type.equals("item")){var key=ResourceLocation.tryParse(reference);return key!=null&&BuiltInRegistries.ITEM.containsKey(key);}
            var handler=handlers.get(type);return handler!=null&&handler.available(reference);
        });
    }
    private int rewardReload(CommandSourceStack source) {
        try {var next=RaidRewardSettings.load(configDir.resolve("rewards-v2"));rewardSettings=next;return reply(source,"Tabelas de recompensas recarregadas. Raids iniciadas preservam suas tabelas.");}
        catch(Exception error){return reply(source,"Recarregamento recusado; tabelas anteriores preservadas: "+error.getMessage());}
    }
    private int rewardStatus(CommandSourceStack source) {
        if(state==null)return reply(source,"Recompensas indisponíveis.");
        for(var status:RaidRewardJournal.Status.values()) reply(source,"Recompensas "+status+": "+state.modularRewards.grants.values().stream().filter(g->g.status==status).count());
        state.modularRewards.grants.values().stream().filter(g->g.status==RaidRewardJournal.Status.PENDING).collect(java.util.stream.Collectors.groupingBy(g->g.choice.reward.type+"/"+g.choice.reward.reference,java.util.stream.Collectors.counting())).forEach((type,count)->reply(source,"Pendentes "+type+": "+count));
        state.modularRewards.grants.values().stream().filter(g->g.status==RaidRewardJournal.Status.UNCERTAIN).limit(20).forEach(g->reply(source,g.id+" | "+g.player+" | "+g.choice.reward.reference+" x"+g.choice.amount));
        return 1;
    }
    private int rewardReview(CommandSourceStack source,String id,boolean paid,String reason) {
        try {if(!healthy||modularRewards==null)throw new IllegalStateException("Persistência indisponível");modularRewards.review(id,paid,source.getTextName()+": "+reason);return reply(source,"Conferência registrada.");}
        catch(Exception error){return reply(source,"Conferência recusada: "+error.getMessage());}
    }
    private void retryRewards() {
        if (tick % 100 == 0) for (var player : server.getPlayerList().getPlayers()) {
            String key = player.getUUID().toString();
            var outstanding=new HashSet<String>();
            state.modularRewards.grants.values().stream().filter(g->g.player.equals(key)&&g.status!=RaidRewardJournal.Status.DONE&&g.status!=RaidRewardJournal.Status.SKIPPED).forEach(g->outstanding.add(g.id));
            new PlayerRewardTarget(server,player,"shirokai_modular:").prune(outstanding);
            if (!state.pending.getOrDefault(key, List.of()).isEmpty()
                || state.deliveries.values().stream().anyMatch(d -> d.player.equals(key))) rewardQueue.add(player.getUUID());
        }
        if (rewardQueue.isEmpty()) return;
        var iterator = rewardQueue.iterator();
        UUID id = iterator.next(); iterator.remove();
        var player = server.getPlayerList().getPlayer(id);
        if (player != null) deliverRewards(player);
    }
    private int deliverRewards(ServerPlayer player) {
        if (RaidInventory.locked(player)) return 0;
        try {
            int delivered = rewardDelivery.deliver(new PlayerRewardTarget(server, player), 4);
            if (delivered > 0) player.sendSystemMessage(RaidMessages.chat(msg("recompensas.entregues", "quantidade", delivered)));
            return delivered;
        } catch (Exception error) {
            healthy = false;
            LOG.error("Entregas suspensas: reserva preservada. Confira os armazenamentos antes de reiniciar.", error);
            if (active != null) {
                cleanup(active);
            }
            throw new IllegalStateException(msg("textos.persistencia_de_recompensas_precisa_de_atencao"), error);
        }
    }
    private int claim(CommandSourceStack source) {
        if(!healthy) return reply(source,msg("textos.resgate_indisponivel_persistencia_precisa_de_atencao"));
        try {
            var player=source.getPlayerOrException(); var key=player.getUUID().toString();
            if(tick < claimCooldown.getOrDefault(player.getUUID(),0L)) return reply(source,msg("textos.aguarde_cinco_segundos_entre_resgates"));
            claimCooldown.put(player.getUUID(),tick+100);
            int delivered = deliverRewards(player);
            int pending = state.pending.getOrDefault(key, List.of()).stream().mapToInt(r -> r.count).sum()
                + state.deliveries.values().stream().filter(d -> d.player.equals(key)).mapToInt(d -> d.reward.count).sum();
            return reply(source,msg("textos.itens_entregues") + delivered + msg("textos.itens_pendentes") + pending
                + msg("textos.sem_espaco_ou_com_item_indisponivel_as_pendencias_sao_preservadas_reservas_"));
        } catch(Exception e) { LOG.error("Falha no resgate; reserva preservada",e); return reply(source,msg("recompensas.erro")); }
    }
}
