package com.shirokai.raids.integration;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemon.mod.common.net.messages.server.BattleChallengePacket;
import com.cobblemon.mod.common.net.serverhandling.ChallengeHandler;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Standard challenge handler + ordinary action responses on a disposable server. No real clients. */
public final class ChallengeIntegration implements ModInitializer {
    private static final long RAID_HP=3000;
    private final List<FakePlayer> players=new ArrayList<>();
    private final List<Pokemon> leads=new ArrayList<>();
    private final List<Pokemon> benches=new ArrayList<>();
    private final Map<UUID,Integer> beforePP=new HashMap<>();
    private Map<UUID,ServerPlayer> lookup;
    private PokemonEntity boss;
    private MinecraftServer server;
    private int ticks, raidEndedTick=-1,leaveTick;
    private long damageBeforeReturn;
    private int ppBeforeReturn;
    private boolean late,finished,swapRequested,switched,departed,returned,secondExit;
    private boolean partialChecked, rewardsChecked;
    private long hpBeforeChoices;

    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if(finished) return;
            server=s;
            try { tick(); } catch(Throwable error) { error.printStackTrace(); complete(false,error.toString()); }
        });
    }
    @SuppressWarnings("unchecked")
    private void attachFakePlayers() throws Exception {
        // Fixture only: locate the UUID->ServerPlayer lookup by its exact generic signature,
        // rather than depending on an obfuscated private field name or changing production code.
        for(Field field:PlayerList.class.getDeclaredFields()) {
            if(field.getGenericType() instanceof ParameterizedType type && type.getRawType()==Map.class
                && Arrays.equals(type.getActualTypeArguments(),new Type[]{UUID.class,ServerPlayer.class})) {
                field.setAccessible(true); lookup=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
                break;
            }
        }
        require(lookup!=null,"Lookup de jogadores não encontrado");
        for(int i=0;i<3;i++) {
            var player=FakePlayer.get(server.overworld(),new GameProfile(UUID.randomUUID(),"[RProbe"+i+"]"));
            player.setPos(5+i*2,200,0);
            require(lookup.putIfAbsent(player.getUUID(),player)==null,"UUID já existia");
            players.add(player);
            if (i == 0) {
                for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                    player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                player.getInventory().setItem(0, new ItemStack(Items.APPLE, 63));
            }
            var party=Cobblemon.INSTANCE.getStorage().getParty(player);
            var lead=PokemonProperties.Companion.parse("garchomp level=100 moves=dragonclaw"," ","=").create();
            party.add(lead); leads.add(lead);
            var bench=PokemonProperties.Companion.parse("mewtwo level=100 moves=psychic"," ","=").create();
            party.add(bench); benches.add(bench);
            require(lead.sendOut(server.overworld(),player.position().add(0,0,2),null,e -> kotlin.Unit.INSTANCE)!=null,"Pokémon não enviado");
            beforePP.put(lead.getUuid(),pp(lead,"dragonclaw"));
            beforePP.put(bench.getUuid(),pp(bench,"psychic"));
        }
    }
    private int command(String command) throws Exception {
        return server.getCommands().getDispatcher().execute(command,players.getFirst().createCommandSourceStack().withPermission(4));
    }
    private void challenge(int index) {
        var player=players.get(index);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,boss.getBoundingBox().getCenter());
        require(boss.canBattle(player),"Cliente padrão recusaria canBattle do boss público");
        require(com.cobblemon.mod.common.util.PlayerExtensionsKt.canInteractWith(player,boss,Cobblemon.config.getBattleWildMaxDistance()),"Desafio fora da distância padrão: "+index);
        ChallengeHandler.INSTANCE.handle(new BattleChallengePacket(boss.getId(),leads.get(index).getUuid(),
            BattleFormat.Companion.getGEN_9_SINGLES()),server,player);
        var battle=BattleRegistry.getBattleByParticipatingPlayer(player);
        require(battle!=null,"Pacote padrão não criou batalha de raid para jogador "+index);
        require(!battle.getSide2().getActors()[0].getPokemonList().getFirst().getUuid().equals(boss.getPokemon().getUuid()),"Boss reutilizado na sessão");
        var dto=new BattleInitializePacket(battle,battle.getSide1());
        require(dto.getSide2().getActors().getFirst().getActivePokemon().getFirst()!=null,"DTO não inclui boss na UI padrão");
        require(boss.getBattleId()==null,"Entidade pública ganhou battleId");
        var graal=(com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService)com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService();
        double multiplier=graal.getContext().eval("js","battleMap.get('"+battle.getBattleId()+"').battle.__shirokaiDamageMultiplier").asDouble();
        require(multiplier==1.5,"Multiplicador configurado não chegou ao motor real");
    }
    private com.google.gson.JsonObject state() throws Exception {
        return com.google.gson.JsonParser.parseString(Files.readString(Path.of("world/data/shirokai-raids/state.json"))).getAsJsonObject();
    }
    private void tick() throws Exception {
        ticks++;
        if(ticks==20) {
            server.overworld().setChunkForced(0,0,true);
            for(int x=-25;x<=25;x++) for(int z=-25;z<=25;z++) server.overworld().setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3);
            attachFakePlayers();
            players.getFirst().setPos(0,200,0);
            var lootFile=Path.of("config/shirokai-raids/rewards/testdrops.json");
            String validLoot=Files.readString(lootFile);
            Files.writeString(lootFile,validLoot.replace("minecraft:apple","missing_mod:missing_item"));
            command("raidadmin reload"); // Must reject unknown registry IDs, retaining previous valid settings.
            Files.writeString(lootFile,validLoot);
            require(command("raidadmin setspawn")==1 && command("raidadmin iniciar rayquaza")==1,"Raid não iniciou após reload inválido");
            players.getFirst().setPos(5,200,0);
            var bosses=server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-48,150,-48,48,250,48),e -> e.getTags().contains("shirokai_raids_boss"));
            require(bosses.size()==1,"Boss público não é único"); boss=bosses.getFirst();
            require(boss.getPokemon().getScaleModifier()==2.5f,"Escala configurada não aplicada");
            boss.getPokemon().getMoveSet().clear();
            boss.getPokemon().getMoveSet().setMove(0,Objects.requireNonNull(Moves.INSTANCE.getByName("splash")).create());
            require(server.getCommands().getDispatcher().getRoot().getChild("raid").getChild("atacar")==null,"Ataque automático ainda registrado");
            challenge(0); challenge(1); hpBeforeChoices=state().get("hp").getAsLong();
        }
        if(boss==null) return;
        if(ticks==220) require(state().get("hp").getAsLong()==hpBeforeChoices,"Dano sem escolher movimento");
        require(boss.getBattleId()==null,"Conflito de battleId no boss público");
        if(!late && ticks>=350 && !boss.isRemoved()) { challenge(2); late=true; }
        if(late && switched && !departed && ticks>=370 && !boss.isRemoved()) {
            var leaving=BattleRegistry.getBattleByParticipatingPlayer(players.get(1));
            if(leaving!=null && pp(leads.get(1),"dragonclaw")<beforePP.get(leads.get(1).getUuid())) {
                server.getCommands().getDispatcher().execute("raid sair",players.get(1).createCommandSourceStack());
                require(BattleRegistry.getBattleByParticipatingPlayer(players.get(1))==null,"Comando sair não encerrou a própria batalha");
                damageBeforeReturn=state().getAsJsonObject("damage").get(players.get(1).getUUID().toString()).getAsLong();
                require(damageBeforeReturn>0,"Sair perdeu contribuição");
                departed=true;leaveTick=ticks;
            }
        }
        if(departed && !returned && ticks>=leaveTick+60 && !boss.isRemoved()) {
            ppBeforeReturn=pp(leads.get(1),"dragonclaw");
            challenge(1);returned=true;
            require(state().getAsJsonObject("damage").get(players.get(1).getUUID().toString()).getAsLong()==damageBeforeReturn,"Retorno zerou contribuição");
        }
        if(returned && !secondExit && !boss.isRemoved()) {
            var battle=BattleRegistry.getBattleByParticipatingPlayer(players.get(1));
            if(battle!=null && pp(leads.get(1),"dragonclaw")<ppBeforeReturn) {
                BattleRegistry.INSTANCE.onPlayerDisconnect(players.get(1));
                server.getCommands().getDispatcher().execute("raid sair",players.get(1).createCommandSourceStack());
                require(state().getAsJsonObject("damage").get(players.get(1).getUUID().toString()).getAsLong()>damageBeforeReturn,"Dano novo não acumulou ao anterior");
                secondExit=true;
                // Contributor really absent from the online lookup when the boss is defeated.
                lookup.remove(players.get(1).getUUID());
            }
        }
        if(ticks>220 && ticks%20==0 && !boss.isRemoved()) for(int i=0;i<players.size();i++) {
            var battle=BattleRegistry.getBattleByParticipatingPlayer(players.get(i));
            if(battle==null || battle.getEnded()) continue;
            if(departed && !returned) continue;
            if(returned && !secondExit && i!=1) continue;
            if(i!=0 && swapRequested && !switched && ticks>240) continue;
            var actor=(PlayerBattleActor)battle.getSide1().getActors()[0];
            if(actor.getMustChoose() && actor.getRequest()!=null && actor.getRequest().getActive()!=null) {
                if(i==0 && !swapRequested) {
                    actor.setActionResponses(List.of(new SwitchActionResponse(benches.get(0).getUuid())));
                    swapRequested=true;
                } else {
                    if(i==0) {
                        require(actor.getActivePokemon().getFirst().getBattlePokemon().getUuid().equals(benches.get(0).getUuid()),"Troca não aplicou o Pokémon escolhido");
                        switched=true;
                    }
                    actor.setActionResponses(List.of(new MoveActionResponse(i==0?"psychic":"dragonclaw","p2a",null)));
                }
            }
        }
        if(boss.isRemoved() && raidEndedTick<0) {
            var state=state();
            require(state.get("hp").getAsLong()==0,"Boss removido sem derrota global");
            require(late && switched && departed && returned && secondExit,"Entrada tardia, troca ou saída não executada");
            long total=0; for(var entry:state.getAsJsonObject("damage").entrySet()) total+=entry.getValue().getAsLong();
            require(total==RAID_HP && state.getAsJsonObject("damage").size()==3,"Contribuição global incorreta");
            for(int i=0;i<3;i++) {
                require(BattleRegistry.getBattleByParticipatingPlayer(players.get(i))==null,"Batalha permaneceu ativa");
                var used=i==0?benches.get(i):leads.get(i);
                require(pp(used,i==0?"psychic":"dragonclaw")<beforePP.get(used.getUuid()),"Movimento escolhido não consumiu PP para jogador "+i);
            }
            var pending=state.getAsJsonObject("pending");
            for(var player:players) {
                int count=0;
                for(var entry:pending.getAsJsonArray(player.getUUID().toString())) {
                    var item=entry.getAsJsonObject();require(item.get("item").getAsString().equals("minecraft:apple"),"Tabela de drops do boss ignorada");
                    count+=item.get("count").getAsInt();
                }
                require(count==4,"Número de sorteios/quantidades configuradas não aplicado");
            }
            raidEndedTick=ticks;
        }
        if (raidEndedTick >= 0 && ticks >= raidEndedTick + 20 && !partialChecked) {
            require(apples(players.get(0)) == 64, "Entrega parcial não completou o stack existente");
            require(pendingApples(players.get(0)) == 3, "Entrega parcial perdeu o saldo");
            require(apples(players.get(2)) == 4 && pendingApples(players.get(2)) == 0, "Entrega automática online falhou");
            require(apples(players.get(1)) == 0 && pendingApples(players.get(1)) == 4, "Contribuinte offline perdeu pendências");
            players.get(0).getInventory().setItem(1, ItemStack.EMPTY);
            lookup.put(players.get(1).getUUID(), players.get(1));
            // Exercise the registered login callback; no real network/client is simulated here.
            for (int i = 0; i < 2; i++) ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(players.get(i).connection, null, server);
            partialChecked = true;
        }
        if (partialChecked && ticks >= raidEndedTick + 40 && !rewardsChecked) {
            require(apples(players.get(0)) == 67 && pendingApples(players.get(0)) == 0, "Saldo parcial não foi entregue após liberar espaço");
            require(apples(players.get(1)) == 4 && pendingApples(players.get(1)) == 0, "Login não entregou pendências offline");
            var deliveries = state().getAsJsonObject("deliveries");
            require(deliveries.isEmpty(), "Reservas concluídas não foram confirmadas");
            for (var player : players) {
                int before = apples(player);
                server.getCommands().getDispatcher().execute("raid recompensas", player.createCommandSourceStack());
                require(apples(player) == before, "Tentativa repetida duplicou itens");
                var path = Path.of("world/playerdata", player.getUUID() + ".dat");
                var nbt = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                require(!nbt.getList("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(), "Inventário não persistiu no arquivo vanilla");
                require(!nbt.getList("Tags", net.minecraft.nbt.Tag.TAG_STRING).isEmpty(), "Recibo não foi salvo junto do inventário");
            }
            rewardsChecked = true;
        }
        if(raidEndedTick>=0 && ticks>=raidEndedTick+60) {
            require(rewardsChecked, "Validação de recompensas não executou");
            var archive = state().getAsJsonObject("archive");
            require(archive.getAsJsonObject("totals").size() == 3, "Participantes ausentes do ranking global");
            long archivedDamage = 0;
            for (var participant : players) {
                String key = participant.getUUID().toString();
                var totals = archive.getAsJsonObject("totals").getAsJsonObject(key);
                require(totals.get("participations").getAsInt() == 1 && totals.get("victories").getAsInt() == 1, "Retorno contou duas participações");
                archivedDamage += totals.get("damage").getAsLong();
                var history = archive.getAsJsonObject("history").getAsJsonArray(key);
                require(history.size() == 1 && history.get(0).getAsJsonObject().getAsJsonObject("drops").get("minecraft:apple").getAsInt() == 4, "Histórico perdeu drops");
            }
            require(archivedDamage == RAID_HP, "Ranking global perdeu dano efetivo");
            var player=players.get(2);
            var wild=PokemonProperties.Companion.parse("magikarp level=5"," ","=").createEntity(server.overworld());
            wild.setNoAi(true); wild.setNoGravity(true); wild.setPos(0,200,-5);
            require(server.overworld().addFreshEntity(wild),"Spawn do Pokémon normal falhou");
            player.setPos(0,200,3);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,wild.getBoundingBox().getCenter());
            ChallengeHandler.INSTANCE.handle(new BattleChallengePacket(wild.getId(),leads.get(2).getUuid(),BattleFormat.Companion.getGEN_9_SINGLES()),server,player);
            var normal=BattleRegistry.getBattleByParticipatingPlayer(player);
            require(normal!=null,"Desafio de Pokémon normal foi quebrado");
            require(normal.getSide2().getActors()[0].getPokemonList().getFirst().getUuid().equals(wild.getPokemon().getUuid()),"Desafio normal entrou no adaptador de raid");
            normal.stop(); wild.discard();
            com.shirokai.raids.RewardRecoveryIntegration.verify(server);
            com.shirokai.raids.RaidMenuIntegration.verify(server);
            com.shirokai.raids.OperationsIntegration.verify(server);
            complete(true,"Desafio padrão, UI, movimentos, PP, troca, contribuição acumulada, configuração por boss, reload inválido, entrega automática/parcial/offline/login, recibos no playerdata, repetição sem duplicação e batalha normal verificados. FakePlayers; nenhum cliente real.");
        }
        if(ticks==600) require(switched,"A troca de Pokémon não terminou em 30 segundos");
        require(ticks<2200,"Timeout do desafio padrão");
    }
    private static int apples(ServerPlayer player) {
        return player.getInventory().items.stream().filter(s -> s.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum();
    }
    private int pendingApples(ServerPlayer player) throws Exception {
        int count = 0;
        var entries = state().getAsJsonObject("pending").getAsJsonArray(player.getUUID().toString());
        if (entries != null) for (var entry : entries) count += entry.getAsJsonObject().get("count").getAsInt();
        return count;
    }
    private static int pp(Pokemon pokemon,String name) {
        return pokemon.getMoveSet().getMoves().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow().getCurrentPp();
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private void complete(boolean passed,String message) {
        finished=true;
        try {
            for(var player:players) {
                var battle=BattleRegistry.getBattleByParticipatingPlayer(player);
                if(battle!=null && !battle.getEnded()) battle.stop();
                if(lookup!=null) lookup.remove(player.getUUID(),player);
            }
            var result=Map.of("passed",passed,"message",message,"realClients",0,"controlledSessions",players.size());
            Files.writeString(Path.of("challenge-integration-result.json"),new com.google.gson.Gson().toJson(result));
            System.out.println("SHIROKAI_CHALLENGE_INTEGRATION "+result);
        } catch(Exception error) { throw new IllegalStateException(error); }
        server.halt(false);
    }
}
