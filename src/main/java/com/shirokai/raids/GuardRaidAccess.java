package com.shirokai.raids;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.*;

/** Optional Guard ABI. Fail closed when an installed Guard has an incompatible API. */
final class GuardRaidAccess {
    private static boolean present() { return FabricLoader.getInstance().isModLoaded("shirokai_guard"); }
    static boolean teleport(ServerPlayer player, ServerLevel world, double x, double y, double z,
                            float yaw, float pitch, String raid) {
        if (!present()) return player.teleportTo(world,x,y,z,Set.of(),yaw,pitch);
        try {
            return (boolean) Class.forName("dev.shirokai.guard.api.GuardEntryApi")
                .getMethod("teleportRaid",UUID.class,String.class,double.class,double.class,double.class,
                    float.class,float.class,String.class)
                .invoke(null,player.getUUID(),world.dimension().location().toString(),x,y,z,yaw,pitch,raid);
        } catch (ReflectiveOperationException e) {
            org.slf4j.LoggerFactory.getLogger("shirokai_raids").error("Guard instalado sem API de entrada compatível; teleporte recusado",e);
            return false;
        }
    }
    static void invalidate(UUID player) { call("invalidatePlayer",UUID.class,player); }
    static void end(String raid) { call("endRaid",String.class,raid); }
    private static void call(String method, Class<?> type, Object value) {
        if (!present()) return;
        try { Class.forName("dev.shirokai.guard.api.GuardEntryApi").getMethod(method,type).invoke(null,value); }
        catch (ReflectiveOperationException e) { org.slf4j.LoggerFactory.getLogger("shirokai_raids").error("Falha na invalidação Guard",e); }
    }
}
