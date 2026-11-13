package com.shirokai.raids;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.nio.file.*;
import java.util.*;

public final class CatalogIntegration implements ModInitializer {
    private MinecraftServer server;
    private FakePlayer player;
    private int ticks,starts,checks,battleTick;
    private boolean second;
    private com.cobblemon.mod.common.entity.pokemon.PokemonEntity battleBoss;
    private com.cobblemon.mod.common.pokemon.Pokemon lead;
    private RaidCatalogSettings settings;
    private List<RaidAutomaticCatalog.Species> catalog;
    private final List<String> selected=new ArrayList<>();
    private void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{server=s;try{tick();}catch(Throwable e){finish(false,e.toString());}});}
    private int command(String command)throws Exception{return server.getCommands().getDispatcher().execute(command,player.createCommandSourceStack().withPermission(4));}
    private RaidState state()throws Exception{return JsonFiles.read(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/shirokai-raids/state.json"),RaidState.class);}
    private void setup()throws Exception {
        server.overworld().setChunkForced(0,0,true);
        for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++)server.overworld().setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3);
        player=FakePlayer.get(server.overworld(),new GameProfile(UUID.randomUUID(),"CatalogProbe"));player.setPos(0,200,0);
        settings=RaidCatalogSettings.load(Path.of("config/shirokai-raids/catalog.json"));catalog=RaidCatalogPokemon.species(settings);
        var eligible=catalog.stream().filter(s->RaidAutomaticCatalog.eligible(s,settings)).toList();
        var counts=new TreeMap<String,Long>();eligible.forEach(s->counts.merge(RaidAutomaticCatalog.difficulty(s),1L,Long::sum));
        System.out.println("CATALOG_POOLS "+counts);JsonFiles.write(Path.of("catalog-pool-report.json").toAbsolutePath(),eligible);
        require(counts.keySet().equals(RaidRewardSettings.DIFFICULTIES),"Não há candidatos em todas as dificuldades: "+counts);
        for(String id:List.of("arcanine","gyarados","charizard","tyranitar"))require(eligible.stream().anyMatch(s->s.id().equals("cobblemon:"+id)),"Forma forte final não encontrada: "+id);
        for(String id:List.of("charmander","charmeleon","magikarp","growlithe","clefable"))require(eligible.stream().noneMatch(s->s.id().equals("cobblemon:"+id)),"Espécie inadequada incluída: "+id);
        // Remove every manual boss: automatic raids must work without individual registrations.
        try(var files=Files.list(Path.of("config/shirokai-raids/bosses"))){for(var p:files.toList())Files.delete(p);}
        command("raidadmin reload");command("raidadmin catalogo");
        command("raidadmin sortear");require(state().automaticRaidStarts==0,"Falha por arena não configurada consumiu contador");
        command("raidadmin setspawn");
    }
    private void tick()throws Exception {
        ticks++;if(ticks==40){second=Files.exists(Path.of("catalog-integration-result.json"));if(second){restart();return;}setup();}
        if(ticks<50||ticks%10!=0)return;
        if(starts==20){battle();return;}
        long previous=state().automaticRaidStarts;
        command("raidadmin sortear");starts++;
        var bosses=server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-30,180,-30,30,250,30),e->e.getTags().contains("shirokai_raids_boss")&&!e.isRemoved());
        require(bosses.size()==1,"Sorteio não criou boss único: "+starts);
        var entity=bosses.getFirst();var pokemon=entity.getPokemon();var id=pokemon.getSpecies().getResourceIdentifier().toString();
        var entry=catalog.stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();
        require(RaidAutomaticCatalog.eligible(entry,settings),"Boss inelegível entrou na arena");
        require(entry.special()==(starts%20==0),"Especial fora da 20ª posição: "+id+" #"+starts);
        var difficulty=RaidAutomaticCatalog.difficulty(entry);var profile=settings.profiles.get(difficulty);
        require(pokemon.getIvs().getOrDefault(Stats.SPEED)==15,"IV de Speed não ficou fixo em 15");
        require(pokemon.getLevel()==profile.level,"Nível não aplicado");require(pokemon.getIvs().getOrDefault(Stats.ATTACK)==profile.iv,"IV não aplicado");
        require(pokemon.getEvs().getOrDefault(pokemon.getForm().getBaseStats().get(Stats.ATTACK)>=pokemon.getForm().getBaseStats().get(Stats.SPECIAL_ATTACK)?Stats.ATTACK:Stats.SPECIAL_ATTACK)==profile.offenseEv,"EV ofensivo não aplicado");
        var legal=new HashSet<>(pokemon.getForm().getMoves().getLevelUpMovesUpTo(profile.level));legal.addAll(pokemon.getForm().getMoves().getTmMoves());legal.addAll(pokemon.getForm().getMoves().getEvolutionMoves());
        require(pokemon.getMoveSet().getMoves().size()>=2,"Boss sem golpes ofensivos suficientes");
        for(var move:pokemon.getMoveSet().getMoves())require(legal.stream().anyMatch(m->m.getName().equals(move.getName()))&&!RaidCatalogPokemon.UNSUITABLE_MOVES.contains(move.getName()),"Golpe inadequado ou ilegal: "+move.getName());
        var disk=state();require(disk.automaticRaidStarts==previous+1,"Início não persistiu contador");require(disk.hp>0&&disk.hp<=100_000,"Vida não foi calibrada para o grupo padrão: "+disk.hp);
        selected.add(id+"/"+difficulty);command("raidadmin sortear");require(state().automaticRaidStarts==disk.automaticRaidStarts,"Segunda raid simultânea consumiu contador");
        if(starts==20){battleBoss=entity;return;}
        command("raidadmin parar");require(state().automaticRaidStarts==disk.automaticRaidStarts,"Encerrar raid alterou contador");
        if(starts==10){command("raidadmin reload");require(state().automaticRaidStarts==10,"Reload zerou ciclo");}
    }
    @SuppressWarnings("unchecked") private void battle()throws Exception {
        if(battleTick==0){
            battleTick=ticks;
            var saved=state();saved.validate();require(saved.automaticRaidStarts==20,"Ciclo persistido incorreto");
            require(!RaidAutomaticCatalog.specialDue(JsonFiles.copy(saved,RaidState.class).automaticRaidStarts),"21ª raid não voltou ao pool normal");
            command("raidadmin iniciar inexistente");require(state().automaticRaidStarts==20,"Falha manual alterou contador");
            for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(field.getGenericType() instanceof java.lang.reflect.ParameterizedType t && t.getRawType()==Map.class && Arrays.equals(t.getActualTypeArguments(),new java.lang.reflect.Type[]{UUID.class,net.minecraft.server.level.ServerPlayer.class})){
                field.setAccessible(true);((Map<UUID,net.minecraft.server.level.ServerPlayer>)field.get(server.getPlayerList())).put(player.getUUID(),player);break;
            }
            server.getPlayerList().getPlayers().add(player);
            lead=com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("mewtwo level=100 moves=protect").create();
            com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player).add(lead);
            command("raid entrar");player.setPos(0,200,4);
            lead.sendOut(server.overworld(),player.position().add(0,0,2),null,e -> kotlin.Unit.INSTANCE);
        }
        var battle=com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
        if(battle==null){
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,battleBoss.getBoundingBox().getCenter());
            com.cobblemon.mod.common.net.serverhandling.ChallengeHandler.INSTANCE.handle(new com.cobblemon.mod.common.net.messages.server.BattleChallengePacket(battleBoss.getId(),lead.getUuid(),com.cobblemon.mod.common.battles.BattleFormat.Companion.getGEN_9_SINGLES()),server,player);
        }else{
            var actor=(com.cobblemon.mod.common.battles.actor.PlayerBattleActor)battle.getSide1().getActors()[0];
            if(actor.getMustChoose()&&actor.getRequest()!=null&&actor.getRequest().getActive()!=null)actor.setActionResponses(List.of(new com.cobblemon.mod.common.battles.MoveActionResponse("protect",null,null)));
            var graal=(com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService)com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService();
            var slots=graal.getContext().eval("js","(s=>s?s.battle.sides[1].pokemon[0].moveSlots.map(m=>({pp:m.pp,max:m.maxpp})):[])(battleMap.get('"+battle.getBattleId()+"'))");
            boolean used=false;for(long i=0;i<slots.getArraySize();i++)if(slots.getArrayElement(i).getMember("pp").asInt()<slots.getArrayElement(i).getMember("max").asInt())used=true;
            if(used){
                var clone=battle.getSide2().getActors()[0].getPokemonList().getFirst().getEffectedPokemon();
                require(clone.getIvs().getOrDefault(Stats.SPEED)==15,"Clone de combate perdeu IV 15 de Speed");
                require(clone.getIvs().getOrDefault(Stats.ATTACK)==battleBoss.getPokemon().getIvs().getOrDefault(Stats.ATTACK),"Clone nativo perdeu IVs do catálogo");
                require(clone.getMoveSet().getMoves().stream().map(m->m.getName()).toList().equals(battleBoss.getPokemon().getMoveSet().getMoves().stream().map(m->m.getName()).toList()),"Clone nativo perdeu golpes selecionados");
                require(state().automaticRaidStarts==20,"Batalha avançou contador");command("raid sair");command("raidadmin parar");
                finish(true,"Catálogo real, 20 bosses treinados, 20ª especial, golpes legais/IVs/EVs e batalha nativa com ação do boss e PP real verificados.");return;
            }
        }
        if(ticks-battleTick>400)throw new AssertionError("Boss automático não executou golpe na batalha nativa");
    }
    private void restart()throws Exception {
        player=FakePlayer.get(server.overworld(),new GameProfile(UUID.randomUUID(),"CatalogRestart"));player.setPos(0,200,0);
        settings=RaidCatalogSettings.load(Path.of("config/shirokai-raids/catalog.json"));catalog=RaidCatalogPokemon.species(settings);
        require(state().automaticRaidStarts==20,"Novo JVM perdeu contador da 20ª raid");
        command("raidadmin sortear");require(state().automaticRaidStarts==21,"21ª raid não avançou contador após reinício");
        var bosses=server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-30,180,-30,30,250,30),e->e.getTags().contains("shirokai_raids_boss")&&!e.isRemoved());
        require(bosses.size()==1,"Novo processo não criou boss único");
        var id=bosses.getFirst().getPokemon().getSpecies().getResourceIdentifier().toString();require(catalog.stream().anyMatch(s->s.id().equals(id)&&!s.special()),"21ª raid deveria ser comum");
        command("raidadmin parar");
        var manual=new RaidConfig.Boss();manual.id="manual_event";manual.species="arcanine";manual.hp=3000;
        JsonFiles.write(Path.of("config/shirokai-raids/bosses/manual_event.json"),manual);command("raidadmin reload");command("raidadmin iniciar manual_event");
        require(state().activeRaid!=null,"Evento manual não iniciou");
        var manualBosses=server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-30,180,-30,30,250,30),e->e.getTags().contains("shirokai_raids_boss")&&!e.isRemoved());
        require(manualBosses.size()==1&&manualBosses.getFirst().getPokemon().getIvs().getOrDefault(Stats.SPEED)==15,"Boss manual não recebeu IV 15 de Speed");require(state().automaticRaidStarts==21,"Evento manual consumiu posição do ciclo");command("raidadmin parar");
        finish(true,"Novo JVM preservou ciclo, 21ª foi comum e evento manual não consumiu a sequência automática.");
    }
    private void finish(boolean passed,String message){try{JsonFiles.write(Path.of(second?"catalog-restart-result.json":"catalog-integration-result.json").toAbsolutePath(),Map.of("passed",passed,"realClients",0,"checks",checks,"selected",selected,"message",message));}catch(Exception e){e.printStackTrace();}System.out.println("SHIROKAI_CATALOG "+passed+" "+message);server.halt(false);}
}
