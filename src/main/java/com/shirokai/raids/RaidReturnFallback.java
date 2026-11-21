package com.shirokai.raids;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Recovery-only adapter over the pinned 0.1.24 return pipeline. No auth/Guard bypass. */
public final class RaidReturnFallback {
 static final Set<String> DESTINATION_FAILURES=Set.of("dimensão de origem indisponível","destino salvo inválido","destino salvo sem piso seguro ou obstruído","entrada ou teleporte de retorno recusado pelo Guard","teleporte recusado ou destino não confirmado");
 private static final Set<UUID> ACTIVE=new HashSet<>();
 private static final Map<UUID,RaidState.ReturnPosition> OVERRIDES=new HashMap<>();
 public static RaidState.ReturnPosition overrideOrigin(RaidState.ReturnPosition original,ServerPlayer player){return OVERRIDES.getOrDefault(player.getUUID(),original);}
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("shirokai_raids");
 private RaidReturnFallback(){}
 static Object field(Object owner,String name)throws ReflectiveOperationException {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void field(Object owner,String name,Object value)throws ReflectiveOperationException {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
 static Object invoke(Object owner,String name,Class<?>[] types,Object...args)throws ReflectiveOperationException {var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(owner,args);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException runtime)throw runtime;throw e;}}
 static RaidState.ReturnPosition target(ServerPlayer p)throws ReflectiveOperationException,java.io.IOException {
  Path explicit=FabricLoader.getInstance().getConfigDir().resolve("shirokai-raids/recovery-spawn.json");
  if(Files.exists(explicit))return JsonFiles.read(explicit,RaidState.ReturnPosition.class);
  if(FabricLoader.getInstance().isModLoaded("shirokai_essentials")) {
   var type=Class.forName("dev.shirokai.essentials.ShirokaiEssentials");var app=type.getMethod("instance").invoke(null);
   if(app==null)throw new IllegalStateException("Essentials ainda não está pronto");
   Object key=type.getMethod("spawnKey",ServerPlayer.class).invoke(app,p),data=type.getMethod("data").invoke(app);
   Object pos=data.getClass().getMethod("spawn",String.class).invoke(data,key);
   if(pos!=null){var c=pos.getClass();return new RaidState.ReturnPosition((String)c.getMethod("dimension").invoke(pos),(double)c.getMethod("x").invoke(pos),(double)c.getMethod("y").invoke(pos),(double)c.getMethod("z").invoke(pos),(float)c.getMethod("yaw").invoke(pos),(float)c.getMethod("pitch").invoke(pos));}
   LOG.warn("Spawn Essentials não configurado para {}; tentando spawn global do mundo",key);
  }
  var world=p.server.overworld();var pos=world.getSharedSpawnPos();return new RaidState.ReturnPosition(world.dimension().location().toString(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,world.getSharedSpawnAngle(),0);
 }
 static boolean safe(Object mod,ServerPlayer p,ServerLevel world,Vec3 point)throws ReflectiveOperationException {
  var block=BlockPos.containing(point);if(point.y<=world.getMinBuildHeight()||point.y+p.getBbHeight()>=world.getMaxBuildHeight()||!world.getWorldBorder().isWithinBounds(block))return false;
  world.getChunkAt(block);
  return (boolean)invoke(mod,"safeBodyAt",new Class<?>[]{ServerLevel.class,ServerPlayer.class,Vec3.class,Entity.class,boolean.class},world,p,point,null,true);
 }
 /** Invoked only after the original pipeline has passed its authentication/liveness/retry checks. */
 public static boolean trySpawn(Object mod,ServerPlayer p,String reason) {
  if(!DESTINATION_FAILURES.contains(reason)||!ACTIVE.add(p.getUUID()))return false;
  RaidState state=null;RaidState.ReturnPosition original=null;boolean recovered=false;
  try {
   state=(RaidState)field(mod,"state");original=state.returns.get(p.getUUID().toString());if(original==null)return false;
   var spawn=target(p);spawn.raid=original.raid;
   if(spawn.dimension==null||!Double.isFinite(spawn.x+spawn.y+spawn.z)||Math.abs(spawn.x)>29999984||Math.abs(spawn.z)>29999984||!Float.isFinite(spawn.yaw+spawn.pitch))throw new IllegalArgumentException("Spawn alternativo inválido");
   var id=ResourceLocation.tryParse(spawn.dimension);if(id==null)throw new IllegalArgumentException("Dimensão do spawn inválida");
   var world=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,id));if(world==null)throw new IllegalArgumentException("Dimensão do spawn indisponível");
   Object attempt=((Map<?,?>)field(mod,"returnAttempts")).get(p.getUUID());if(attempt==null)return false;
   // Exact /spawn position first; small local search handles a blocked or slightly airborne spawn.
   search: for(int radius=0;radius<=4;radius++)for(int dy:new int[]{0,1,-1,2,-2,3,-3})for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
    if(radius>0&&Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
    var point=new Vec3(spawn.x+dx,spawn.y+dy,spawn.z+dz);if(!safe(mod,p,world,point))continue;
    var candidate=new RaidState.ReturnPosition(spawn.dimension,point.x,point.y,point.z,spawn.yaw,spawn.pitch);candidate.raid=original.raid;
    OVERRIDES.put(p.getUUID(),candidate);field(attempt,"next",0L);
    recovered=(boolean)invoke(mod,"attemptReturn",new Class<?>[]{ServerPlayer.class},p);
    if(recovered){LOG.info("Retorno alternativo da raid confirmado no spawn para {}: {} [{}, {}, {}]",p.getUUID(),spawn.dimension,point.x,point.y,point.z);p.sendSystemMessage(RaidMessages.chat("O local anterior estava indisponível. Você foi levado ao spawn com segurança."));return true;}
    // A physically safe spawn was found. Never walk around a Guard denial or retry an IO failure.
    break search;
   }
   LOG.warn("Spawn alternativo não confirmado para {}; origem e bloqueio de recuperação preservados",p.getUUID());return false;
  }catch(Exception failure){LOG.warn("Falha ao tentar spawn alternativo para {}; origem preservada",p.getUUID(),failure);return false;}
  finally {if(!recovered&&state!=null&&original!=null)state.returns.put(p.getUUID().toString(),original);OVERRIDES.remove(p.getUUID());ACTIVE.remove(p.getUUID());}
 }
 public static String pendingMessage(Object mod,ServerPlayer p) {
  try{Object attempt=((Map<?,?>)field(mod,"returnAttempts")).get(p.getUUID());String reason=attempt==null?"aguardando próxima tentativa":String.valueOf(field(attempt,"reason"));
   return "Retorno pendente: "+(reason.isBlank()?"aguardando próxima tentativa":reason)+". "+(DESTINATION_FAILURES.contains(reason)?"O spawn alternativo também não pôde ser confirmado. Avise um administrador e confira o console.":"Tente /raid sair novamente após corrigir essa condição.");
  }catch(Exception e){LOG.warn("Motivo do retorno indisponível",e);return "Retorno pendente. Tente /raid sair novamente e confira o console.";}
 }
}
