package com.shirokai.raids.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.UUID;

/** Only for a disposable dedicated test world. Does not simulate battle UI or human clients. */
public final class TeleportIntegration implements ModInitializer {
    private int ticks;
    private FakePlayer player;
    private Vec3 failedOrigin;
    @Override public void onInitialize() {
        if (!Boolean.getBoolean("shirokai.integration")) throw new IllegalStateException("Fixture requer -Dshirokai.integration=true e mundo descartável");
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                ticks++;
                if(ticks==20) first(server);
                if(ticks==140) second(server);
            } catch(Throwable error) { complete(server,false,error.toString()); }
        });
    }
    private void platform(MinecraftServer server,boolean solid) {
        var world=server.overworld();
        for(int x=-25;x<=25;x++) for(int z=-25;z<=25;z++)
            world.setBlock(new BlockPos(x,199,z),(solid?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
    }
    private int command(MinecraftServer server,String command) throws Exception {
        return server.getCommands().getDispatcher().execute(command,player.createCommandSourceStack().withPermission(4).withSource(server));
    }
    private void first(MinecraftServer server) throws Exception {
        platform(server,true);
        player=FakePlayer.get(server.overworld(),new GameProfile(UUID.fromString("bdf41527-c5bb-4057-86aa-74dcbf69c774"),"[RaidTest]"));
        player.setPos(0,200,0);
        require(command(server,"raidadmin setspawn")==1,"setspawn falhou");
        require(command(server,"raidadmin iniciar rayquaza")==1,"spawn falhou");
        var bosses=server.overworld().getEntitiesOfClass(PokemonEntity.class,new AABB(-48,180,-48,48,240,48),e->e.getTags().contains("shirokai_raids_boss"));
        require(bosses.size()==1,"boss único não encontrado");
        player.setPos(1000,200,1000);
        var body=AABB.ofSize(new Vec3(10.5,200+player.getBbHeight()/2.0,0.5),player.getBbWidth(),player.getBbHeight(),player.getBbWidth());
        System.out.println("TELEPORT_DIAGNOSTIC boss="+bosses.getFirst().getBoundingBox()+" body="+body
            +" floor="+server.overworld().getBlockState(new BlockPos(10,199,0))
            +" loaded="+server.overworld().hasChunkAt(new BlockPos(10,200,0))
            +" collisionFree="+server.overworld().noCollision(player,body)
            +" entities="+server.overworld().getEntities(player,body,e->e.isAlive()&&e.isPickable()).size());
        require(command(server,"raid")==1,"/raid não teleportou");
        require(player.position().distanceToSqr(new Vec3(0,200,0))<48*48,"destino fora da arena");
        require(server.overworld().noCollision(player,player.getBoundingBox()),"jogador dentro de blocos");
        require(!bosses.getFirst().getBoundingBox().inflate(2).intersects(player.getBoundingBox()),"jogador dentro do boss");
        var arrived=player.position();
        command(server,"raid tempo");
        require(arrived.equals(player.position()),"/raid tempo teleportou indevidamente");
        require(command(server,"raid")==0,"cooldown de teleporte não aplicado");
        platform(server,false);
        player.setPos(1000,200,1000);failedOrigin=player.position();
    }
    private void second(MinecraftServer server) throws Exception {
        require(command(server,"raid")==0,"arena sem piso aceitou teleporte");
        require(failedOrigin.equals(player.position()),"falha de busca moveu o jogador");
        command(server,"raidadmin parar");
        require(command(server,"raid")==1,"/raid sem raid não informou agendamento");
        require(failedOrigin.equals(player.position()),"/raid sem raid teleportou");
        complete(server,true,"Spawn real, teleporte seguro, distância do boss, cooldown, falha sem piso, /raid tempo e ausência de raid verificados. FakePlayer não é um cliente real.");
    }
    private void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private void complete(MinecraftServer server,boolean passed,String message) {
        try {
            String text=new com.google.gson.Gson().toJson(java.util.Map.of("passed",passed,"message",message,"minecraft","1.21.1","cobblemon","1.8.3","realClients",0));
            Files.writeString(Path.of("teleport-integration-result.json"),text);
            System.out.println("SHIROKAI_TELEPORT_INTEGRATION " + text);
        } catch(Exception e) { throw new IllegalStateException("Falha ao gravar resultado",e); }
        server.halt(false);
    }
}
