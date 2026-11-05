package com.shirokai.raids;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.luckperms.api.LuckPermsProvider;

final class RaidPermissions {
    private RaidPermissions() {}
    static boolean check(CommandSourceStack source, String node, boolean fallback) {
        if (!source.isPlayer()) return source.hasPermission(2);
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) return fallback;
        return query(source,node,fallback);
    }
    private static boolean query(CommandSourceStack source,String node,boolean fallback) {
        try {
            var api=LuckPermsProvider.get();
            var user=api.getUserManager().getUser(source.getPlayer().getUUID());
            if(user==null) return false; // Never grant privileges while permission data is unavailable.
            var result=user.getCachedData().getPermissionData().checkPermission(node);
            return result.asBoolean() || (result == net.luckperms.api.util.Tristate.UNDEFINED && fallback);
        } catch(IllegalStateException e) { return false; }
    }
}
