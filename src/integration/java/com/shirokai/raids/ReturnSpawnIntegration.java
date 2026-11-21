package com.shirokai.raids;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public final class ReturnSpawnIntegration implements ModInitializer {
 int ticks,checks;boolean done;Object mod;RaidState state;Probe player;Path config;boolean essentials;
 static final class Probe extends ServerPlayer {final List<String> messages=new ArrayList<>();Probe(MinecraftServer s){super(s,s.overworld(),new GameProfile(UUID.randomUUID(),"ReturnProbe"),ClientInformation.createDefault());}@Override public void sendSystemMessage(net.minecraft.network.chat.Component c){messages.add(c.getString());super.sendSystemMessage(c);}}
 void require(boolean c,String m){if(!c)throw new AssertionError(m);checks++;}
 @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{if(done||++ticks!=30)return;try{run(s);finish(s,true,"Native return/spawn recovery passed");}catch(Throwable e){e.printStackTrace();finish(s,false,e.toString());}});}
 void resetRetry()throws Exception {var map=(Map<?,?>)RaidReturnFallback.field(mod,"returnAttempts");var attempt=map.get(player.getUUID());if(attempt!=null)RaidReturnFallback.field(attempt,"next",0L);}
 void origin(String dim,double x,double y,double z)throws Exception {var p=new RaidState.ReturnPosition(dim,x,y,z,0,0);p.raid=UUID.randomUUID().toString();state.returns.put(player.getUUID().toString(),p);resetRetry();player.setPos(.5,200,.5);}
 void leave(MinecraftServer s)throws Exception {s.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),"raid sair");}
 void returned(double x,double y,double z)throws Exception {
  require(player.position().distanceToSqr(new Vec3(x,y,z))<1e-8,"Expected confirmed destination, actual="+player.position());
  require(!state.returns.containsKey(player.getUUID().toString()),"Return reservation removed only after success");
  require(!ShirokaiRaids.participating(player),"Participation cleared");
  require(!ShirokaiRaids.blockCommand(player.createCommandSourceStack(),"spawn"),"Commands unlocked after confirmed return");
  require(player.getInventory().getItem(0).is(Items.DIAMOND)&&player.getInventory().getItem(0).getCount()==7,"Inventory preserved");
 }
 void floor(MinecraftServer s,int x,int z){s.overworld().getChunkAt(new BlockPos(x,200,z));for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)s.overworld().setBlock(new BlockPos(x+dx,199,z+dz),Blocks.STONE.defaultBlockState(),3);}
 void run(MinecraftServer s)throws Exception {
  essentials=FabricLoader.getInstance().isModLoaded("shirokai_essentials");config=FabricLoader.getInstance().getConfigDir().resolve("shirokai-raids/recovery-spawn.json");Files.deleteIfExists(config);
  var f=ShirokaiRaids.class.getDeclaredField("instance");f.setAccessible(true);mod=f.get(null);state=(RaidState)RaidReturnFallback.field(mod,"state");
  player=new Probe(s);player.connection=FakePlayer.get(s.overworld(),new GameProfile(UUID.randomUUID(),"ConnectionProbe")).connection;player.connection.player=player;
  for(var field:PlayerList.class.getDeclaredFields())if(field.getGenericType() instanceof ParameterizedType type&&type.getRawType()==Map.class&&Arrays.equals(type.getActualTypeArguments(),new Type[]{UUID.class,ServerPlayer.class})){field.setAccessible(true);((Map<UUID,ServerPlayer>)field.get(s.getPlayerList())).put(player.getUUID(),player);break;}
  s.getPlayerList().getPlayers().add(player);floor(s,0,0);floor(s,100,100);floor(s,1000,1000);player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));
  s.overworld().setDefaultSpawnPos(essentials?new BlockPos(2000,200,2000):new BlockPos(1000,200,1000),0);
  if(essentials){player.setPos(1000.5,200,1000.5);s.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),"setspawn");}
  var target=RaidReturnFallback.target(player);require(target.dimension.equals("minecraft:overworld")&&target.x==1000.5&&target.y==200&&target.z==1000.5,"Actual Essentials /spawn or vanilla spawn resolved");
  origin("minecraft:overworld",100.5,200,100.5);leave(s);returned(100.5,200,100.5);require(player.messages.stream().noneMatch(m->m.contains("levado ao spawn")),"Valid origin still preferred");
  for(var invalid:List.of(new RaidState.ReturnPosition("missing:world",100,200,100,0,0),new RaidState.ReturnPosition("minecraft:overworld",100.5,250,100.5,0,0),new RaidState.ReturnPosition("minecraft:overworld",40000000,200,100.5,0,0))){
   origin(invalid.dimension,invalid.x,invalid.y,invalid.z);require(ShirokaiRaids.blockCommand(player.createCommandSourceStack(),"spawn"),"Commands protected while pending");leave(s);returned(1000.5,200,1000.5);
  }
  s.overworld().setBlock(new BlockPos(100,200,100),Blocks.STONE.defaultBlockState(),3);origin("minecraft:overworld",100.5,200,100.5);leave(s);returned(1000.5,200,1000.5);s.overworld().setBlock(new BlockPos(100,200,100),Blocks.AIR.defaultBlockState(),3);
  require(player.messages.stream().anyMatch(m->m.contains("levado ao spawn")),"Fallback explained in chat");
  JsonFiles.write(config,new RaidState.ReturnPosition("minecraft:overworld",100.5,202,100.5,0,0));origin("missing:world",10,200,10);leave(s);returned(100.5,200,100.5);
  JsonFiles.write(config,new RaidState.ReturnPosition("minecraft:overworld",500.5,250,500.5,0,0));origin("missing:world",10,200,10);var saved=state.returns.get(player.getUUID().toString());leave(s);
  require(state.returns.get(player.getUUID().toString())==saved,"Unsafe alternate preserves original reservation");require(ShirokaiRaids.blockCommand(player.createCommandSourceStack(),"spawn"),"Failed return does not unlock commands");
  require(player.messages.stream().anyMatch(m->m.contains("Retorno pendente: dimensão de origem indisponível")),"Manual exit shows actual pending reason");
  require(!RaidReturnFallback.trySpawn(mod,player,"aguardando autenticação"),"No fallback bypass for authentication");require(state.returns.get(player.getUUID().toString())==saved,"Auth rejection leaves data intact");
  Files.deleteIfExists(config);resetRetry();
  // A real filesystem failure must not erase the pending origin or unlock commands.
  RaidReturnFallback.invoke(mod,"save",new Class<?>[0]);
  Path statePath=(Path)RaidReturnFallback.field(mod,"statePath"),backup=statePath.resolveSibling("state-test-backup.json");
  Files.move(statePath,backup,StandardCopyOption.REPLACE_EXISTING);Files.createDirectory(statePath);Files.writeString(statePath.resolve("block"),"fixture");
  try{
   leave(s);require(state.returns.get(player.getUUID().toString())==saved,"IO failure preserves original pending object");
   require(ShirokaiRaids.blockCommand(player.createCommandSourceStack(),"spawn"),"IO failure keeps commands protected");
   require(JsonFiles.read(backup,RaidState.class).returns.get(player.getUUID().toString()).dimension.equals("missing:world"),"Saved original preserved during write failure");
  }finally{Files.deleteIfExists(statePath.resolve("block"));Files.delete(statePath);Files.move(backup,statePath,StandardCopyOption.REPLACE_EXISTING);RaidReturnFallback.field(mod,"healthy",true);}
  resetRetry();leave(s);returned(1000.5,200,1000.5);
  var path=s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("playerdata/"+player.getUUID()+".dat");var nbt=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());
  require(nbt.getList("Pos",6).getDouble(0)==1000.5&&nbt.getString("Dimension").equals("minecraft:overworld"),"Confirmed destination persisted in playerdata");
 }
 void finish(MinecraftServer s,boolean passed,String message){done=true;try{Files.writeString(Path.of("returnspawn-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",passed,"checks",checks,"message",message,"realClients",0,"essentials",essentials,"guard",FabricLoader.getInstance().isModLoaded("shirokai_guard"))));}catch(Exception e){e.printStackTrace();}System.out.println("RETURN_SPAWN "+passed+" "+checks+" "+message);s.halt(false);}
}
