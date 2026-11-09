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
    private Map<String, RaidConfig.Boss> bosses;
    private Map<String, RaidConfig.Rewards> rewards;
    private RaidState state;
    private Path statePath;
    private MinecraftServer server;
    private Active active;
    private boolean healthy;
    private long tick;
    private RewardDelivery rewardDelivery;
    private final Set<UUID> rewardQueue = new LinkedHashSet<>();
    private final Map<UUID,Long> claimCooldown = new HashMap<>();
    private final Map<UUID,Long> teleportCooldown = new HashMap<>();
    private final Set<Integer> warned = new HashSet<>();
    private final Random random = new Random();

    private static final class Active {
        final String id = UUID.randomUUID().toString();
        final RaidConfig.Boss definition;
        final PokemonEntity boss;
        final ServerLevel world;
        final Vec3 center;
        final RaidCombatCoordinator health;
        RaidChunkLease chunks;
        TraditionalRaidBattles battles;
        final RaidDamageTracker damage = new RaidDamageTracker();
        final long ends;
        final ServerBossEvent bar;
        Active(RaidConfig.Boss definition, PokemonEntity boss, ServerLevel world, Vec3 center, long ends, int capacity) {
            this.definition = definition; this.boss = boss; this.world = world; this.center = center; this.ends = ends;
            health = new RaidCombatCoordinator(UUID.fromString(id),definition.hp,capacity);
            bar = new ServerBossEvent(Component.literal(definition.name), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        }
    }

    @Override public void onInitialize() {
        instance=this;
        TraditionalRaidBattles.register();
        try { loadConfig(); } catch (Exception e) { throw new IllegalStateException(msg("textos.configuracao_shirokai_raids_invalida"), e); }
        ServerLifecycleEvents.SERVER_STARTED.register(this::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { if (active != null) finish(false, msg("textos.servidor_encerrando_raid_cancelada")); });
        ServerTickEvents.END_SERVER_TICK.register(s -> tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> rewardQueue.add(handler.getPlayer().getUUID()));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !isBoss(entity.getUUID()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof PokemonEntity && (entity.getTags().contains("shirokai_raids_boss")
                || entity.getTags().contains("shirokai_raid_boss"))
                && (active == null || entity != active.boss || world != active.world)) entity.discard();
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(event -> { if (isBoss(event.getPokemon().getUUID())) event.cancel(); });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            if (active == null) return;
            for (var actor : event.getBattle().getActors()) for (var bp : actor.getPokemonList()) {
                UUID id = bp.getEffectedPokemon().getUuid();
                if (id.equals(active.boss.getPokemon().getUuid())) {
                    event.cancel(); return;
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var raid = Commands.literal("raid").requires(s -> RaidPermissions.check(s,"shirokai.raids.use",true)).executes(c -> raidCommand(c.getSource()));
            raid.then(Commands.literal("entrar").requires(s -> RaidPermissions.check(s,"shirokai.raids.join",true)).executes(c -> join(c.getSource())));
            raid.then(Commands.literal("lobby").requires(s -> RaidPermissions.check(s,"shirokai.raids.lobby",true)).executes(c -> lobby(c.getSource())));
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
            var admin = Commands.literal("raidadmin").requires(s -> RaidPermissions.check(s,"shirokai.raids.admin",s.hasPermission(2)));
            admin.then(Commands.literal("iniciar").then(Commands.argument("boss", StringArgumentType.word())
                .suggests((c,b) -> { bosses.keySet().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> start(c.getSource(), StringArgumentType.getString(c,"boss")))));
            admin.then(Commands.literal("parar").executes(c -> { finish(false,msg("textos.raid_encerrada_pela_administracao")); return 1; }));
            admin.then(Commands.literal("setspawn").executes(c -> setSpawn(c.getSource())));
            admin.then(Commands.literal("setlobby").executes(c -> setLobby(c.getSource())));
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
                if (active != null) return reply(c.getSource(),msg("textos.encerre_a_raid_antes_de_recarregar"));
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
        var nextMessages = RaidMessages.load(configDir.resolve("messages.json"));
        var bossMap = RaidBossCatalog.load(configDir.resolve("bosses"));
        var rewardMap = new HashMap<String,RaidConfig.Rewards>();
        for (var boss : bossMap.values()) {
            var table = JsonFiles.read(configDir.resolve("rewards/" + boss.rewards + ".json"),RaidConfig.Rewards.class); table.validate();
            rewardMap.put(boss.rewards,table);
        }
        if(server!=null) { validateRewardItems(rewardMap); validateDestinations(next); }
        config = next; bosses = bossMap; rewards = rewardMap;
        RaidMessages.install(nextMessages);
    }
    private void validateRewardItems(Map<String,RaidConfig.Rewards> tables) {
        for(var table:tables.entrySet()) for(var drop:table.getValue().configuredItems())
            if(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(drop.item)))
                throw new IllegalArgumentException(msg("textos.item_inexistente_na_tabela")+table.getKey()+": "+drop.item);
    }
    private void validateDestinations(RaidConfig next) {
        if (next.spawnConfigured) validateDestination(next.dimension, next.y);
        if (next.lobby.configured) validateDestination(next.lobby.dimension, next.lobby.y);
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
            state.archive.complete(RaidArchive.Outcome.INTERRUPTED, System.currentTimeMillis(), Map.of());
            // Restart recovery cancels the active raid; no repeat rewards or accumulated raids.
            state.activeRaid = null; state.bossEntity = null;
            if (state.nextRaid == 0 || (state.nextRaid < System.currentTimeMillis() && config.restartPolicy.equals("SKIP_MISSED")))
                state.nextRaid = RaidSchedule.next(System.currentTimeMillis(),config.intervalSeconds);
            healthy = true; save();
            rewardDelivery = new RewardDelivery(state, this::save);
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
        if(mod==null || mod.active==null || player.level().getEntity(packet.getTargetedEntityId())!=mod.active.boss) return false;
        try {
            if(!mod.healthy || !mod.state.returns.containsKey(player.getUUID().toString()) || !mod.inArena(player) || mod.active.health.current()==0 || !mod.active.boss.canBattle(player))
                throw new IllegalStateException(msg("textos.a_raid_nao_esta_disponivel_para_voce_neste_momento"));
            if(!RaidPermissions.check(player.createCommandSourceStack(),"shirokai.raids.use",true)
                || !RaidPermissions.check(player.createCommandSourceStack(),"shirokai.raids.join",true))
                throw new IllegalStateException(msg("textos.voce_nao_tem_permissao_para_participar_da_raid"));
            if(!com.cobblemon.mod.common.util.PlayerExtensionsKt.canInteractWith(player,mod.active.boss,
                com.cobblemon.mod.common.Cobblemon.config.getBattleWildMaxDistance()))
                throw new IllegalStateException(msg("textos.aproxime_se_do_boss_para_desafia_lo"));
            mod.active.battles.challenge(player,packet.getSelectedPokemonId());
            mod.state.archive.current.participants.put(player.getUUID().toString(), player.getGameProfile().getName());
            mod.snapshot();
        } catch(RuntimeException error) {
            if (!mod.healthy && mod.active != null) {
                mod.cleanup(mod.active);
            }
            player.sendSystemMessage(Component.literal(error.getMessage()==null?msg("textos.nao_foi_possivel_iniciar_a_batalha"):error.getMessage()));
            LOG.debug("Desafio de raid recusado",error);
        }
        return true;
    }
    public static boolean blockCommand(CommandSourceStack source, String command) {
        var mod = instance;
        if (mod == null || mod.state == null || !source.isPlayer()
            || !mod.state.returns.containsKey(source.getPlayer().getUUID().toString())
            || RaidCommands.allowedDuringRaid(command)) return false;
        source.sendFailure(Component.literal(msg("comandos.bloqueados")));
        return true;
    }
    private boolean isBoss(UUID uuid) { return active != null && active.boss.getUUID().equals(uuid); }
    private int reply(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text),false); return 1; }
    private void broadcast(String text) { server.getPlayerList().broadcastSystemMessage(Component.literal(msg("chat.prefixo", "texto", text)),false); }
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
            source.sendFailure(Component.literal(msg("textos.voce_nao_tem_permissao_para_entrar_na_arena"))); return 0;
        }
        var player=source.getPlayer();
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
            || com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            source.sendFailure(Component.literal(msg("textos.encerre_sua_batalha_e_saia_de_veiculos_antes_de_usar_raid"))); return 0;
        }
        if (tick < teleportCooldown.getOrDefault(player.getUUID(),0L)) {
            source.sendFailure(Component.literal(msg("textos.aguarde_cinco_segundos_entre_teleportes"))); return 0;
        }
        teleportCooldown.put(player.getUUID(),tick+100);
        var raid=active;
        if (raid.boss.isRemoved() || !raid.boss.isAlive() || server.getLevel(raid.world.dimension()) != raid.world) {
            source.sendFailure(Component.literal(msg("textos.a_arena_nao_esta_disponivel"))); return 0;
        }
        var spot=SafeTeleportSearch.find(raid.boss.getX(),raid.center.y,raid.boss.getZ(),config.arenaRadius,
            player.getUUID().hashCode(),candidate -> safeSpot(raid,player,candidate));
        if (spot.isEmpty()) {
            source.sendFailure(Component.literal(msg("textos.nao_ha_um_local_seguro_e_carregado_proximo_ao_boss_avise_a_administracao"))); return 0;
        }
        var target=spot.get();
        double x=target.x()+.5,z=target.z()+.5;
        float yaw=(float)(Math.toDegrees(Math.atan2(raid.boss.getZ()-z,raid.boss.getX()-x))-90);
        String key = player.getUUID().toString();
        if (state.returns.containsKey(key)) return reply(source,msg("textos.voce_ja_entrou_use_raid_sair_antes_de_entrar_novamente"));
        state.returns.put(key, new RaidState.ReturnPosition(player.serverLevel().dimension().location().toString(),
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        save(); // Persist the origin before moving the player.
        player.teleportTo(raid.world,x,target.feetY(),z,Set.of(),yaw,0);
        player.fallDistance=0;
        raid.bar.addPlayer(player);
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
        var body=net.minecraft.world.phys.AABB.ofSize(new Vec3(feet.getX()+.5,feet.getY()+player.getBbHeight()/2.0,feet.getZ()+.5),
            player.getBbWidth(),player.getBbHeight(),player.getBbWidth());
        return body.minX >= world.getWorldBorder().getMinX() && body.maxX <= world.getWorldBorder().getMaxX()
            && body.minZ >= world.getWorldBorder().getMinZ() && body.maxZ <= world.getWorldBorder().getMaxZ()
            && world.noCollision(player,body) && (boss == null || !boss.getBoundingBox().inflate(2).intersects(body))
            && world.getEntities(player,body,entity -> entity.isAlive() && entity.isPickable()).isEmpty();
    }
    private int lobby(CommandSourceStack source) {
        if (!healthy) return reply(source, msg("textos.teleporte_indisponivel_confira_a_persistencia"));
        if (!config.lobby.configured) return reply(source, msg("textos.o_lobby_ainda_nao_foi_configurado_pela_administracao"));
        try {
            var player = source.getPlayerOrException();
            if (state.returns.containsKey(player.getUUID().toString())) return reply(source,msg("textos.use_raid_sair_para_voltar_ao_local_anterior_antes_de_ir_ao_lobby"));
            if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
                source.sendFailure(Component.literal(msg("textos.encerre_sua_batalha_e_saia_de_veiculos_antes_de_ir_ao_lobby"))); return 0;
            }
            if (tick < teleportCooldown.getOrDefault(player.getUUID(), 0L)) {
                source.sendFailure(Component.literal(msg("textos.aguarde_cinco_segundos_entre_teleportes"))); return 0;
            }
            validateDestination(config.lobby.dimension, config.lobby.y);
            var world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(config.lobby.dimension)));
            var center = net.minecraft.core.BlockPos.containing(config.lobby.x, config.lobby.y, config.lobby.z);
            if (!world.getWorldBorder().isWithinBounds(center)) throw new IllegalStateException(msg("textos.lobby_fora_da_borda_do_mundo"));
            world.getChunkAt(center);
            var target = safeBody(world, player, center, null) ? java.util.Optional.of(new SafeTeleportSearch.Spot(center.getX(), center.getY(), center.getZ()))
                : SafeTeleportSearch.find(config.lobby.x, config.lobby.y, config.lobby.z, 16, player.getUUID().hashCode(),
                    spot -> safeBody(world, player, new net.minecraft.core.BlockPos(spot.x(), spot.feetY(), spot.z()), null));
            if (target.isEmpty()) { source.sendFailure(Component.literal(msg("textos.nao_ha_um_destino_seguro_no_lobby_posicao_preservada"))); return 0; }
            var spot = target.get();
            player.teleportTo(world, spot.x()+.5, spot.feetY(), spot.z()+.5, Set.of(), config.lobby.yaw, config.lobby.pitch);
            player.fallDistance = 0;
            teleportCooldown.put(player.getUUID(), tick + 100);
            return reply(source, msg("textos.voce_chegou_ao_lobby_da_raid"));
        } catch (Exception error) { source.sendFailure(Component.literal(msg("textos.nao_foi_possivel_acessar_o_lobby") + error.getMessage())); return 0; }
    }
    private void cleanup(Active raid) {
        try { if (raid.battles != null) raid.battles.close(); }
        finally {
            try { raid.bar.removeAllPlayers(); raid.boss.discard(); }
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
    private int setLobby(CommandSourceStack source) {
        try {
            var player = source.getPlayerOrException();
            if (!safeBody(player.serverLevel(), player, net.minecraft.core.BlockPos.containing(player.position()), null))
                return reply(source, msg("textos.fique_sobre_piso_solido_e_em_um_local_livre_para_definir_o_lobby"));
            return configure(source, next -> {
                next.lobby.configured = true; next.lobby.dimension = player.serverLevel().dimension().location().toString();
                next.lobby.x = player.getX(); next.lobby.y = player.getY(); next.lobby.z = player.getZ();
                next.lobby.yaw = player.getYRot(); next.lobby.pitch = player.getXRot();
            }, msg("textos.lobby_salvo_use_raid_lobby_para_acessa_lo"));
        } catch (Exception e) { return reply(source, msg("textos.nao_foi_possivel_definir_o_lobby") + e.getMessage()); }
    }
    private int configure(CommandSourceStack source, java.util.function.Consumer<RaidConfig> change, String message) {
        if (active != null) return reply(source, msg("textos.encerre_a_raid_antes_de_alterar_a_configuracao"));
        try {
            var next = JsonFiles.copy(config, RaidConfig.class); change.accept(next); next.validate(); validateDestinations(next);
            JsonFiles.write(configDir.resolve("config.json"), next);
            boolean reschedule = config.intervalSeconds != next.intervalSeconds || config.automaticRaids != next.automaticRaids;
            config = next;
            if (reschedule && healthy) { state.nextRaid = RaidSchedule.next(System.currentTimeMillis(), config.intervalSeconds); warned.clear(); save(); }
            return reply(source, message);
        } catch (Exception error) {
            LOG.error("Falha ao atualizar configuração", error);
            source.sendFailure(Component.literal(msg("textos.configuracao_nao_concluida") + error.getMessage())); return 0;
        }
    }
    private int status(CommandSourceStack source) {
        reply(source, msg("textos.persistencia") + (healthy ? msg("textos.disponivel") : msg("textos.indisponivel")) + msg("textos.automatico") + config.automaticRaids
            + msg("textos.intervalo") + RaidTime.format(config.intervalSeconds) + msg("textos.duracao") + RaidTime.format(config.durationSeconds) + msg("textos.limite") + config.maxConcurrentPlayers);
        reply(source, msg("textos.arena") + (config.spawnConfigured ? config.dimension + " " + config.x + ", " + config.y + ", " + config.z : msg("textos.use_raidadmin_setspawn"))
            + msg("textos.lobby") + (config.lobby.configured ? config.lobby.dimension : msg("textos.use_raidadmin_setlobby")));
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
        } catch (Exception error) { source.sendFailure(Component.literal(msg("textos.conferencia_nao_concluida") + error.getMessage())); return 0; }
    }
    private int start(CommandSourceStack source, String id) {
        try { begin(id); return reply(source,msg("textos.raid_iniciada")); }
        catch (Exception e) { LOG.error("Falha ao iniciar raid",e); return reply(source,msg("raid.erro_inicio", "erro", e.getMessage())); }
    }
    private void begin(String id) {
        if (!healthy || active != null) throw new IllegalStateException(msg("textos.raids_indisponiveis_ou_ja_existe_uma_raid_ativa"));
        if (!config.spawnConfigured) throw new IllegalStateException(msg("textos.use_raidadmin_setspawn_primeiro"));
        var definition = bosses.get(id);
        if (definition == null) throw new IllegalArgumentException(msg("textos.boss_desconhecido"));
        validateRewardItems(Map.of(definition.rewards,rewards.get(definition.rewards)));
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(config.dimension)));
        if (world == null) throw new IllegalArgumentException(msg("textos.dimensao_inexistente"));
        if (config.y < world.getMinBuildHeight() || config.y >= world.getMaxBuildHeight()) throw new IllegalArgumentException(msg("textos.altura_de_arena_invalida"));
        var center = net.minecraft.core.BlockPos.containing(config.x, config.y, config.z);
        if (!world.getWorldBorder().isWithinBounds(center)) throw new IllegalArgumentException(msg("textos.arena_fora_da_borda_do_mundo"));
        var lease = new RaidChunkLease(world, new net.minecraft.world.level.ChunkPos(center), config.arenaRadius);
        boolean retained = false;
        try {
        world.getChunkAt(center);
        var properties = PokemonProperties.Companion.parse(definition.species + " level=" + definition.level + " shiny=" + definition.shiny + " uncatchable=yes" + (definition.form.isEmpty() ? "" : " form=" + definition.form));
        var entity = properties.createEntity(world);
        if (!entity.getPokemon().getSpecies().getResourceIdentifier().getPath().equals(definition.species.replace("cobblemon:","")))
            throw new IllegalArgumentException(msg("textos.especie_invalida_ou_indisponivel"));
        entity.getPokemon().setScaleModifier(definition.scale);
        entity.setCustomName(Component.literal(definition.name));
        entity.setPersistenceRequired(); entity.setInvulnerable(true);
        entity.setGlowingTag(true);
        entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(),false);
        entity.setNoAi(true);
        entity.addTag("shirokai_raids_boss");
        entity.addTag("shirokai_raid_boss");
        entity.moveTo(config.x,config.y,config.z,config.spawnYaw,config.spawnPitch);
        var candidate = new Active(definition,entity,world,new Vec3(config.x,config.y,config.z),System.currentTimeMillis()+config.durationSeconds*1000L,config.maxConcurrentPlayers);
        candidate.chunks = lease;
        candidate.battles=new TraditionalRaidBattles(entity,candidate.health,definition.damageMultiplier,hit ->
            candidate.damage.record(hit.player(),hit.name(),hit.pokemon(),hit.applied(),System.currentTimeMillis()),
            player -> inArena(player) && state.returns.containsKey(player.getUUID().toString()));
        active = candidate; // entity load callback must recognize it before insertion
        if (!world.addFreshEntity(entity)) { active = null; throw new IllegalStateException(msg("textos.spawn_recusado")); }
        state.activeRaid = candidate.id; state.bossEntity = entity.getUUID().toString(); state.hp = definition.hp; state.damage.clear();
        state.archive.current = new RaidArchive.Event(candidate.id, definition.name, definition.species, System.currentTimeMillis(), definition.hp);
        state.nextRaid = RaidSchedule.next(System.currentTimeMillis(),config.intervalSeconds); warned.clear();
        try { save(); } catch (RuntimeException e) { entity.discard(); active = null; throw e; }
        retained = true;
        broadcast(msg("raid.spawn", "boss", definition.name));
        } catch (RuntimeException error) {
            if (active != null && active.chunks == lease) cleanup(active);
            throw error;
        } finally { if (!retained) lease.close(); }
    }
    private boolean inArena(ServerPlayer player) {
        return active != null && player.isAlive() && !player.isSpectator() && player.serverLevel() == active.world && player.position().distanceToSqr(active.center) <= config.arenaRadius*config.arenaRadius;
    }
    private int join(CommandSourceStack source) {
        return teleportToArena(source);
    }
    private int leave(CommandSourceStack source) {
        if (!source.isPlayer()) return reply(source,msg("textos.comando_disponivel_para_jogadores"));
        if (!healthy) return reply(source,msg("textos.saida_indisponivel_confira_a_persistencia"));
        var player = source.getPlayer();
        String key = player.getUUID().toString();
        var origin = state.returns.get(key);
        if (origin == null) return reply(source,msg("textos.voce_nao_entrou_na_raid_use_raid_entrar"));
        var world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(origin.dimension)));
        if (world == null) return reply(source,msg("textos.a_dimensao_de_retorno_esta_indisponivel_sua_posicao_foi_preservada_avise_a_"));
        if (!player.isAlive() || player.isPassenger()) return reply(source,msg("textos.voce_precisa_estar_vivo_e_fora_de_veiculos_para_sair"));
        if (active != null) { active.battles.leave(player.getUUID()); active.bar.removePlayer(player); }
        if (com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) != null)
            return reply(source,msg("textos.encerre_sua_batalha_atual_antes_de_retornar"));
        player.closeContainer();
        player.teleportTo(world,origin.x,origin.y,origin.z,Set.of(),origin.yaw,origin.pitch);
        player.fallDistance=0;
        state.returns.remove(key);
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
    private void tick() {
        tick++;
        if (!healthy || server == null) return;
        try {
            retryRewards();
            if(active!=null) {
                active.battles.tick();
                if(active.health.current()==0) { finish(true,msg("textos.boss_derrotado_recompensas_disponiveis_em_raid_recompensas")); return; }
                if (tick % 10 == 0) {
                    boolean lowHealthFlash = active.health.fraction() <= 0.20 && (tick / 10) % 2 == 0;
                    active.bar.setColor(lowHealthFlash ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
                }
            }
            if (tick % 20 != 0) return;
            long now = System.currentTimeMillis();
            if (active != null) {
                if (now >= active.ends) { finish(RaidArchive.Outcome.TIMEOUT,msg("textos.raid_encerrada_por_tempo")); return; }
                if (active.boss.isRemoved() || !active.boss.isAlive() || active.boss.level() != active.world
                    || server.getLevel(active.world.dimension()) != active.world) { finish(false,msg("textos.boss_removido_ou_deslocado_raid_cancelada_com_seguranca")); return; }
                if (active.boss.position().distanceToSqr(active.center) > config.arenaRadius*config.arenaRadius)
                    active.boss.teleportTo(active.center.x,active.center.y,active.center.z);
                active.bar.setProgress((float) active.health.fraction());
                active.bar.setName(Component.literal(msg("bossbar.titulo", "boss", active.definition.name, "vida", active.health.current(), "vida_maxima", active.health.maximum(), "tempo", RaidTime.format(Math.max(0,(active.ends-now)/1000)))));
                for (var player : server.getPlayerList().getPlayers()) {
                    if (inArena(player) && state.returns.containsKey(player.getUUID().toString())) active.bar.addPlayer(player);
                    else { active.bar.removePlayer(player); active.battles.leave(player.getUUID()); }
                }
                if (tick % 200 == 0) snapshot();
            } else if (config.spawnConfigured && config.automaticRaids) {
                if (RaidSchedule.due(now,state.nextRaid,false)) {
                    try { begin(RaidBossCatalog.randomId(bosses, random)); }
                    catch (Exception e) { LOG.warn("Raid automática adiada",e); state.nextRaid=RaidSchedule.next(now,config.intervalSeconds); save(); }
                } else RaidSchedule.warning(state.nextRaid - now, config.warningsSeconds.stream().filter(w -> w <= config.intervalSeconds).toList(), warned)
                    .ifPresent(warning -> broadcast(RaidTime.nextRaidWarning(warning)));
            }
        } catch (Exception e) { LOG.error("Raid suspensa após falha; verifique o estado persistente",e); healthy=false; if(active!=null) cleanup(active); }
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
        if(active==null) return;
        var raid=active;
        boolean defeated = outcome == RaidArchive.Outcome.VICTORY;
        try {
            capture(raid);
            var drops = new HashMap<String, Map<String, Integer>>();
            if(defeated && !state.completed.contains(raid.id)) {
                var table=rewards.get(raid.definition.rewards);
                for(var entry:raid.damage.ranking()) {
                    double contribution=raid.damage.contribution(entry.player(),raid.health.maximum());
                    if(contribution<table.minimumContribution) continue;
                    for(var drop:RaidLootRoller.roll(table,contribution,random)) {
                        state.pending.computeIfAbsent(entry.player().toString(),k->new ArrayList<>()).add(new RaidState.Pending(raid.id,drop.item(),drop.count()));
                        drops.computeIfAbsent(entry.player().toString(), key -> new TreeMap<>()).merge(drop.item(), drop.count(), Integer::sum);
                    }
                }
                state.completed.add(raid.id);
            }
            state.archive.complete(outcome, System.currentTimeMillis(), drops);
            state.activeRaid=null; state.bossEntity=null; save();
        } finally { cleanup(raid); }
        broadcast(message);
        if (defeated) {
            // Includes contributors who left combat; offline players are retried on login.
            for (var entry : raid.damage.ranking()) rewardQueue.add(entry.player());
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
            player.sendSystemMessage(Component.literal(msg("textos.menu_de_recompensas_indisponivel_neste_momento"))); return;
        }
        PendingRewardMenu.open(player, mod.state, java.time.ZoneId.of(mod.config.menuTimeZone), 0, () -> mod.healthy,
            () -> mod.claim(player.createCommandSourceStack()));
    }
    private void retryRewards() {
        if (tick % 100 == 0) for (var player : server.getPlayerList().getPlayers()) {
            String key = player.getUUID().toString();
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
        try {
            int delivered = rewardDelivery.deliver(new PlayerRewardTarget(server, player), 4);
            if (delivered > 0) player.sendSystemMessage(Component.literal(msg("recompensas.entregues", "quantidade", delivered)));
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
