package com.shirokai.raids;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Focused read-only preview regression; fixture is excluded from production JAR. */
public final class AdminMenuIntegration implements ModInitializer {
    private int ticks;
    public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(++ticks!=40)return;
        try{
            server.overworld().setChunkForced(0,0,true);
            for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++)server.overworld().setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3);
            var p=FakePlayer.get(server.overworld(),new GameProfile(UUID.randomUUID(),"AdminMenuSetup"));p.setPos(0,200,0);
            server.getCommands().getDispatcher().execute("raidadmin setspawn",p.createCommandSourceStack().withPermission(4));
            AdminRewardMenuIntegration.verify(server);
            Files.copy(Path.of("admin-rewards-menu-result.json"),Path.of("admin-integration-result.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Throwable error){try{JsonFiles.write(Path.of("admin-integration-result.json").toAbsolutePath(),Map.of("passed",false,"realClients",0,"message",error.toString()));}catch(Exception ignored){}error.printStackTrace();}
        finally{server.halt(false);}
    });}
}
