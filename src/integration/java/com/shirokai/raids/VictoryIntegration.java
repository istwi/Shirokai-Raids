package com.shirokai.raids;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import com.shirokai.raids.core.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Drives the real raid completion path; no client rendering or combat simulation claims. */
public final class VictoryIntegration implements ModInitializer {
    private static final class Probe extends ServerPlayer {
        final List<String> messages=new ArrayList<>();
        Probe(MinecraftServer server,String name){super(server,server.overworld(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());}
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component c){messages.add(c.getString());super.sendSystemMessage(c);}
    }
    private MinecraftServer server;
    private Object mod;
    private RaidState state;
    private Map<UUID,ServerPlayer> lookup;
    private Probe first,second;
    private PokemonEntity boss;
    private RaidChunkLease lease;
    private Field victoryField,activeField;
    private int ticks,stage,checks;
    private boolean inspected,disconnected;
    private float originalScale;
    private long deadline;
    private long defeatedAfter;
    private void require(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{server=s;try{tick();}catch(Throwable error){finish(false,error.toString());}});}
    private static Object get(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private int command(Probe player,String text)throws Exception{return server.getCommands().getDispatcher().execute(text,player.createCommandSourceStack().withPermission(4));}
    @SuppressWarnings("unchecked") private void setup()throws Exception {
        var singleton=ShirokaiRaids.class.getDeclaredField("instance");singleton.setAccessible(true);mod=singleton.get(null);state=(RaidState)get(mod,"state");
        activeField=ShirokaiRaids.class.getDeclaredField("active");activeField.setAccessible(true);victoryField=ShirokaiRaids.class.getDeclaredField("victory");victoryField.setAccessible(true);
        for(var f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(f.getGenericType() instanceof ParameterizedType t&&t.getRawType()==Map.class&&Arrays.equals(t.getActualTypeArguments(),new Type[]{UUID.class,ServerPlayer.class})){f.setAccessible(true);lookup=(Map<UUID,ServerPlayer>)f.get(server.getPlayerList());break;}
        require(lookup!=null,"Lookup ausente");
        server.overworld().setChunkForced(0,0,true);for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)server.overworld().setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3);
        startRound("Cancel");stage=1;
    }
    private Probe player(String name)throws Exception {
        var p=new Probe(server,name);p.connection=FakePlayer.get(server.overworld(),new GameProfile(UUID.randomUUID(),"ConnectionProbe")).connection;p.connection.player=p;
        p.setPos(0,200,3);lookup.put(p.getUUID(),p);server.getPlayerList().getPlayers().add(p);
        Cobblemon.INSTANCE.getStorage().getParty(p).add(PokemonProperties.Companion.parse("mewtwo level=100").create());return p;
    }
    private void startRound(String name)throws Exception {
        first=player(name+"First");second=player(name+"Second");command(first,"raidadmin setspawn");command(first,"raidadmin iniciar rayquaza");
        command(first,"raid entrar");command(second,"raid entrar");first.setPos(0,200,3);second.setPos(0,200,-3);
        var active=activeField.get(mod);require(active!=null,"Raid não iniciou");boss=(PokemonEntity)get(active,"boss");originalScale=boss.getPokemon().getScaleModifier();lease=(RaidChunkLease)get(active,"chunks");
        var health=(RaidCombatCoordinator)get(active,"health");var damage=(RaidDamageTracker)get(active,"damage");long hp=health.maximum();
        damage.record(first.getUUID(),first.getGameProfile().getName(),UUID.randomUUID(),hp*3/5,System.currentTimeMillis());damage.record(second.getUUID(),second.getGameProfile().getName(),UUID.randomUUID(),hp-hp*3/5,System.currentTimeMillis());
        // Simulate a fight that outlasted the old spawn-based deadline.
        state.nextRaid=System.currentTimeMillis()-60_000;
        defeatedAfter=System.currentTimeMillis();
        var token=health.join(first.getUUID()).orElseThrow();health.applyDamage(token,1,hp);inspected=false;disconnected=false;
    }
    private void tick()throws Exception {
        ticks++;if(ticks==40)setup();if(stage==0)return;
        var transition=victoryField.get(mod);
        if(stage<=2&&transition!=null){
            var animation=(RaidVictoryTransition)get(transition,"animation");int age=animation.age();
            if(!inspected&&age>=10){
                long interval=((RaidConfig)get(mod,"config")).intervalSeconds*1000L;
                require(state.nextRaid>=defeatedAfter+interval && state.nextRaid<=System.currentTimeMillis()+interval,"Próxima raid não recebeu intervalo completo após a derrota");
                require(JsonFiles.read(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/shirokai-raids/state.json"),RaidState.class).nextRaid==state.nextRaid,"Agendamento após derrota não foi persistido");
                require(!boss.isRemoved()&&boss.getPokemon().getScaleModifier()<originalScale,"Boss sumiu sem contração gradual");require(!lease.closed(),"Ticket liberado antes de terminar efeito");
                require(ShirokaiRaids.captureManager().hasGrant(first)&&!ShirokaiRaids.captureManager().active(first.getUUID()),"Captura apareceu instantaneamente");require(copyCount()==0,"Cópias apareceram durante contração");
                require(Boolean.TRUE.equals(boss.getEntityData().get(PokemonEntity.getUNBATTLEABLE())),"Boss derrotado ainda aceita desafio");require(!boss.hurt(server.overworld().damageSources().generic(),1),"Boss de transição recebeu dano");
                var grant=state.captures.values().stream().filter(g->g.player.equals(first.getUUID().toString())).findFirst().orElseThrow();deadline=grant.expiresAt;
                command(first,"raidadmin iniciar rayquaza");require(activeField.get(mod)==null&&victoryField.get(mod)==transition,"Nova raid sobrepôs transição");
                inspected=true;
                if(stage==1){
                    command(first,"raid sair");require(!ShirokaiRaids.captureManager().hasGrant(first)&&ShirokaiRaids.captureManager().hasGrant(second),"Sair não removeu direito pendente individual");
                    command(second,"raidadmin parar");require(victoryField.get(mod)==null&&boss.isRemoved()&&lease.closed(),"Cancelar transição vazou entidade/ticket");require(state.captures.isEmpty(),"Cancelar transição preservou chances");command(second,"raid sair");startRound("Reveal");stage=2;return;
                }
            }
            if(stage==2&&inspected&&!disconnected&&age>=15){
                ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(second.connection,server);
                require(!ShirokaiRaids.captureManager().hasGrant(second),"Desconexão preservou chance antes da revelação");
                lookup.remove(second.getUUID());server.getPlayerList().getPlayers().remove(second);
                lookup.put(second.getUUID(),second);server.getPlayerList().getPlayers().add(second);require(!ShirokaiRaids.captureManager().hasGrant(second),"Reconectar restaurou chance");disconnected=true;
            }
            if(stage==2&&age>=RaidVictoryTransition.BURST_TICK&&age<RaidVictoryTransition.REVEAL_TICK){
                require(boss.isRemoved()&&copyCount()==0,"Não houve pausa entre explosão e cópia");require(!lease.closed(),"Arena descarregou durante pausa");
            }
        }
        if(stage==2&&inspected&&victoryField.get(mod)==null){
            require(disconnected&&lease.closed(),"Finalização de transição incompleta");require(ShirokaiRaids.captureManager().active(first.getUUID())&&!ShirokaiRaids.captureManager().active(second.getUUID()),"Revelação não respeitou quem saiu/desconectou");require(copyCount()==1,"Número de cópias privadas incorreto");
            var grant=state.captures.values().stream().filter(g->g.player.equals(first.getUUID().toString())).findFirst().orElseThrow();require(grant.expiresAt==deadline,"Delay renovou os dois minutos");
            require(first.messages.stream().anyMatch(s->s.contains("restantes para tentar capturar")),"Introdução não informa tempo restante real");require(first.messages.stream().noneMatch(s->s.contains("Chance por bola")),"Introdução ainda expõe chance por bola");
            command(first,"raid sair");command(second,"raid sair");require(state.captures.isEmpty()&&copyCount()==0,"Sair após revelação deixou cópias");finish(true,"Contração, pausa, revelação tardia, cancelamento, tickets, saída/desconexão, prazo absoluto e chat sem chance validados.");
        }
        if(ticks>500)throw new AssertionError("Timeout stage="+stage);
    }
    private int copyCount(){return server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-50,150,-50,50,260,50),e->e.getTags().contains(RaidCapture.TARGET_TAG)&&!e.isRemoved()).size();}
    private void finish(boolean passed,String message){stage=0;try{JsonFiles.write(Path.of("victory-integration-result.json").toAbsolutePath(),Map.of("passed",passed,"checks",checks,"realClients",0,"message",message));}catch(Exception e){e.printStackTrace();}System.out.println("SHIROKAI_VICTORY "+passed+" "+message);server.halt(false);}
}
