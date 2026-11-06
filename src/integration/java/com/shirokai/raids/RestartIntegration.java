package com.shirokai.raids;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import com.shirokai.raids.core.RaidCombatCoordinator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Four separate JVM boots of one disposable world, including an externally issued SIGKILL. */
public final class RestartIntegration implements ModInitializer {
    private static final Path STATE = Path.of("world/data/shirokai-raids/state.json");
    private static final Path MANIFEST = Path.of("restart-manifest.json").toAbsolutePath();
    private static final ChunkPos CENTER = new ChunkPos(0,-1);
    private static final double X=12.25, Y=200, Z=-8.75;
    private static final UUID CONTRIBUTOR = UUID.fromString("6a8da5cb-645c-4db5-b5c8-c06bbd672c2b");
    private static final UUID OFFLINE = UUID.fromString("792157ad-bcd7-4954-a344-1e0889b5d1cd");
    private static final UUID RECIPIENT = UUID.fromString("c8810380-fd00-4fe7-970e-c32601649077");
    static final class Manifest {
        String firstRaid, firstBoss, crashRaid, crashBoss;
        long nextRaid;
    }
    private final Set<UUID> loadedBosses = new HashSet<>();
    private String phase;
    private int ticks;
    private boolean done;

    @Override public void onInitialize() {
        phase = System.getProperty("shirokai.restart.phase", "");
        if (!List.of("prepare","normal-and-crash","recover","verify-again").contains(phase)) throw new IllegalStateException("Fase de reinício inválida");
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof PokemonEntity && entity.getTags().contains("shirokai_raid_boss")) loadedBosses.add(entity.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (done) return;
            try {
                ticks++;
                if (ticks == 20) server.overworld().getChunkAt(new BlockPos((int)X,(int)Y,(int)Math.floor(Z)));
                if (ticks != 100) return; // Allow persisted entity IO to complete before inspecting it.
                switch (phase) {
                    case "prepare" -> prepare(server);
                    case "normal-and-crash" -> normalAndCrash(server);
                    case "recover" -> recover(server);
                    case "verify-again" -> verifyAgain(server);
                }
            } catch (Throwable error) {
                error.printStackTrace(); done = true;
                try { JsonFiles.write(Path.of(phase+"-result.json").toAbsolutePath(),Map.of("passed",false,"message",error.toString())); }
                catch (Exception writing) { writing.printStackTrace(); }
                server.halt(false);
            }
        });
    }
    private Object mod() throws Exception {
        Field field=ShirokaiRaids.class.getDeclaredField("instance"); field.setAccessible(true); return field.get(null);
    }
    private Object field(Object owner,String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private RaidState state() throws Exception { return (RaidState)field(mod(),"state"); }
    private Object active() throws Exception { return field(mod(),"active"); }
    private int command(MinecraftServer server,String command) throws Exception {
        var player=FakePlayer.get(server.overworld(),new GameProfile(CONTRIBUTOR,"[RestartProbe]"));
        return server.getCommands().getDispatcher().execute(command,player.createCommandSourceStack().withPermission(4));
    }
    private List<PokemonEntity> bosses(MinecraftServer server) {
        return server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-40,180,-60,60,240,40),
            e -> e.getTags().contains("shirokai_raid_boss") || e.getTags().contains("shirokai_raids_boss"));
    }
    private void requireClean(MinecraftServer server) throws Exception {
        require(Boolean.TRUE.equals(field(mod(),"healthy")),"Mod indisponível após boot");
        require(active()==null && state().activeRaid==null && state().archive.current==null,"Evento antigo foi retomado indevidamente");
        require(bosses(server).isEmpty(),"Boss antigo permaneceu carregado");
        require(server.overworld().getForcedChunks().contains(CENTER.toLong()),"Forceload externo perdido no reinício");
        require(server.overworld().getBlockState(new BlockPos(12,199,-9)).is(Blocks.STONE),"Piso da arena mudou");
        var ordinary=server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-40,180,-60,60,240,40),e->e.getTags().contains("restart_control"));
        require(ordinary.size()==1,"Pokémon comum de controle não foi preservado");
        require(!ordinary.getFirst().hasGlowingTag(),"Brilho vazou para Pokémon comum");
    }
    private PokemonEntity start(MinecraftServer server,long damage) throws Exception {
        require(command(server,"raidadmin iniciar rayquaza")==1 && active()!=null,"Nova raid não iniciou");
        var config=JsonFiles.read(Path.of("config/shirokai-raids/config.json"),RaidConfig.class);
        var definition=JsonFiles.read(Path.of("config/shirokai-raids/bosses/rayquaza.json"),RaidConfig.Boss.class);
        var loadedConfig=(RaidConfig)field(mod(),"config");
        require(loadedConfig.durationSeconds==config.durationSeconds && loadedConfig.maxConcurrentPlayers==config.maxConcurrentPlayers,
            "Duração/limite editados não carregados na memória");
        var boss=(PokemonEntity)field(active(),"boss");
        require(Math.abs(boss.getX()-X)<.001 && Math.abs(boss.getY()-Y)<.001 && Math.abs(boss.getZ()-Z)<.001,"Boss não nasceu no ponto salvo");
        require(config.spawnConfigured && config.x==X && config.y==Y && config.z==Z && config.spawnYaw==37 && config.spawnPitch==9,"Ponto/orientação salvos mudaram");
        require(config.lobby.configured && config.lobby.x==24 && config.lobby.y==200 && config.lobby.z==12 && config.lobby.yaw==81,"Lobby salvo mudou");
        require(boss.hasGlowingTag() && boss.isCurrentlyGlowing(),"Brilho não ativado na nova raid");
        require(boss.getPokemon().getScaleModifier()==definition.scale,"Escala editada não aplicada");
        var health=(RaidCombatCoordinator)field(active(),"health");
        require(health.maximum()==definition.hp,"HP editado não aplicado");
        require(((RaidConfig.Boss)field(active(),"definition")).damageMultiplier==definition.damageMultiplier,"Multiplicador editado não carregado");
        if(damage>0) {
            var session=health.join(CONTRIBUTOR).orElseThrow(); health.applyDamage(session,1,damage);
            var tracker=(com.shirokai.raids.core.RaidDamageTracker)field(active(),"damage");
            tracker.record(CONTRIBUTOR,"[RestartProbe]",UUID.randomUUID(),damage,System.currentTimeMillis());
            state().archive.current.participants.put(CONTRIBUTOR.toString(),"[RestartProbe]");
            Method snapshot=ShirokaiRaids.class.getDeclaredMethod("snapshot"); snapshot.setAccessible(true); snapshot.invoke(mod());
        }
        require(bosses(server).size()==1,"Mais de um boss público no novo evento");
        return boss;
    }
    private void prepare(MinecraftServer server) throws Exception {
        server.overworld().setChunkForced(CENTER.x,CENTER.z,true);
        for(int x=-24;x<=48;x++) for(int z=-40;z<=24;z++) server.overworld().setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3);
        var player=FakePlayer.get(server.overworld(),new GameProfile(CONTRIBUTOR,"[RestartProbe]"));
        player.setPos(24,200,12); player.setYRot(81); player.setXRot(7);
        require(command(server,"raidadmin setlobby")==1,"setlobby falhou");
        player.setPos(X,Y,Z); player.setYRot(37); player.setXRot(9);
        require(command(server,"raidadmin setspawn")==1,"setspawn falhou");
        var ordinary=PokemonProperties.Companion.parse("magikarp level=10").createEntity(server.overworld());
        ordinary.setPos(30,200,0); ordinary.setNoAi(true); ordinary.setNoGravity(true); ordinary.setPersistenceRequired(); ordinary.addTag("restart_control");
        require(server.overworld().addFreshEntity(ordinary),"Controle não criado");
        var boss=start(server,1111);
        state().pending.put(OFFLINE.toString(),new ArrayList<>(List.of(new RaidState.Pending("old-offline","minecraft:diamond",3))));
        state().completed.add("old-completed");
        JsonFiles.write(STATE,state());
        var manifest=new Manifest(); manifest.firstRaid=state().activeRaid; manifest.firstBoss=boss.getUUID().toString(); manifest.nextRaid=state().nextRaid;
        JsonFiles.write(MANIFEST,manifest);
        result(server,"Normal shutdown requested with active raid",true);
    }
    private void normalAndCrash(MinecraftServer server) throws Exception {
        requireClean(server);
        var manifest=JsonFiles.read(MANIFEST,Manifest.class);
        var history=state().archive.recent(CONTRIBUTOR.toString());
        require(history.size()==1 && history.getFirst().outcome()==RaidArchive.Outcome.CANCELLED,"Parada normal não cancelou o evento uma vez");
        require(state().archive.totals.get(CONTRIBUTOR.toString()).damage()==1111,"Dano salvo na parada normal mudou");
        require(state().nextRaid==manifest.nextRaid,"Agendamento futuro mudou no reinício normal");
        checkPending();
        var boss=start(server,2222);
        require(!boss.getUUID().toString().equals(manifest.firstBoss),"Boss da parada normal foi reutilizado");
        manifest.crashRaid=state().activeRaid; manifest.crashBoss=boss.getUUID().toString(); manifest.nextRaid=state().nextRaid;
        JsonFiles.write(MANIFEST,manifest);
        require(server.saveEverything(false,true,true),"Checkpoint do mundo falhou");
        var recipient=FakePlayer.get(server.overworld(),new GameProfile(RECIPIENT,"[ReceiptBoot]"));
        state().pending.put(RECIPIENT.toString(),new ArrayList<>(List.of(new RaidState.Pending("crash-reward","minecraft:apple",4))));
        JsonFiles.write(STATE,state());
        int[] writes={0};
        var delivery=new RewardDelivery(state(),()->{
            if(++writes[0]==2) throw new IllegalStateException("planned interruption before ledger acknowledgement");
            try { JsonFiles.write(STATE,state()); } catch(Exception error) { throw new IllegalStateException(error); }
        });
        boolean interrupted=false;
        try { delivery.deliver(new PlayerRewardTarget(server,recipient),4); } catch(IllegalStateException expected) { interrupted=true; }
        require(interrupted && JsonFiles.read(STATE,RaidState.class).deliveries.size()==1,"Janela de queda da entrega não preparada");
        require(apples(recipient)==4,"Inventário não salvo antes da queda");
        // Python reads this atomic marker and kills this exact JVM without running shutdown hooks.
        result(server,"Ready for external SIGKILL with persisted boss and unacknowledged receipt",false);
    }
    private void recover(MinecraftServer server) throws Exception {
        requireClean(server);
        var manifest=JsonFiles.read(MANIFEST,Manifest.class);
        require(loadedBosses.contains(UUID.fromString(manifest.crashBoss)),"O teste não observou o boss persistido sendo carregado e limpo");
        checkTotals(); checkPending();
        require(state().nextRaid==manifest.nextRaid,"Agendamento futuro mudou após queda");
        var recipient=loadRecipient(server);
        require(apples(recipient)==4 && state().deliveries.size()==1,"Recibo/inventário ou reserva não sobreviveram à queda");
        var delivery=(RewardDelivery)field(mod(),"rewardDelivery");
        delivery.deliver(new PlayerRewardTarget(server,recipient),4);
        require(apples(recipient)==4 && state().deliveries.isEmpty(),"Recuperação real duplicou ou perdeu itens");
        require(delivery.deliver(new PlayerRewardTarget(server,recipient),4)==0,"Segunda tentativa repetiu entrega");
        var boss=start(server,0);
        require(!boss.getUUID().toString().equals(manifest.crashBoss),"Boss antigo foi reutilizado em vez de criar novo evento");
        manifest.nextRaid=state().nextRaid; JsonFiles.write(MANIFEST,manifest);
        command(server,"raidadmin parar");
        require(bosses(server).isEmpty(),"Novo boss não foi removido ao cancelar");
        result(server,"Crash recovery, orphan cleanup, original spawn and exactly-once receipt recovery verified",true);
    }
    private void verifyAgain(MinecraftServer server) throws Exception {
        requireClean(server); checkTotals(); checkPending();
        require(state().nextRaid==JsonFiles.read(MANIFEST,Manifest.class).nextRaid,"Agendamento futuro mudou no quarto boot");
        var recipient=loadRecipient(server);
        require(apples(recipient)==4 && state().deliveries.isEmpty(),"Confirmação não sobreviveu ao segundo reinício");
        require(((RewardDelivery)field(mod(),"rewardDelivery")).deliver(new PlayerRewardTarget(server,recipient),4)==0,"Entrega repetida no quarto boot");
        result(server,"Fourth JVM boot did not duplicate history, rewards, bosses or alter saved settings",true);
    }
    private void checkTotals() throws Exception {
        var totals=state().archive.totals.get(CONTRIBUTOR.toString());
        var history=state().archive.recent(CONTRIBUTOR.toString());
        require(totals.participations()==2 && totals.victories()==0 && totals.damage()==3333,"Histórico/totais duplicados ou perdidos no reinício");
        require(history.size()==2 && history.getFirst().outcome()==RaidArchive.Outcome.INTERRUPTED,"Queda não registrada exatamente uma vez");
    }
    private void checkPending() throws Exception {
        require(state().pending.get(OFFLINE.toString()).stream().mapToInt(p->p.count).sum()==3,"Pendências offline antigas mudaram");
        require(state().completed.contains("old-completed"),"Ledger de eventos concluídos mudou");
    }
    private ServerPlayer loadRecipient(MinecraftServer server) throws Exception {
        var player=FakePlayer.get(server.overworld(),new GameProfile(RECIPIENT,"[ReceiptBoot]"));
        player.load(NbtIo.readCompressed(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(RECIPIENT+".dat"),NbtAccounter.unlimitedHeap()));
        return player;
    }
    private static int apples(ServerPlayer player) { return player.getInventory().items.stream().filter(s->s.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum(); }
    private void result(MinecraftServer server,String message,boolean stop) throws Exception {
        done=true;
        var hashes=new TreeMap<String,String>();
        var config=Path.of("config/shirokai-raids");
        try(var files=Files.walk(config)) {
            for(var file:files.filter(p->p.toString().endsWith(".json")).toList())
                hashes.put(config.relativize(file).toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
        JsonFiles.write(Path.of(phase+"-result.json").toAbsolutePath(),Map.of("passed",true,"phase",phase,"message",message,"realClients",0,"configHashes",hashes));
        System.out.println("SHIROKAI_RESTART "+phase+": "+message);
        if(stop) server.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
