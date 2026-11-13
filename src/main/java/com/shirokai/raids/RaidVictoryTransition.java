package com.shirokai.raids;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Owns the defeated boss and its chunk ticket until private capture copies can be revealed. */
final class RaidVictoryTransition implements AutoCloseable {
    static final int BURST_TICK=30,REVEAL_TICK=60;
    private final PokemonEntity boss;
    private final RaidChunkLease chunks;
    private final ServerBossEvent bar;
    private final ServerLevel world;
    private final Vec3 origin;
    private final float scale;
    private final double centerY;
    private int age;
    private boolean closed;
    RaidVictoryTransition(PokemonEntity boss,RaidChunkLease chunks,ServerBossEvent bar){
        this.boss=boss;this.chunks=chunks;this.bar=bar;world=(ServerLevel)boss.level();origin=boss.position();scale=boss.getPokemon().getScaleModifier();centerY=origin.y+Math.min(3,boss.getBbHeight()/2);
    }
    void begin(String name,Collection<ServerPlayer> viewers){
        boss.getEntityData().set(PokemonEntity.getUNBATTLEABLE(),true);boss.setNoGravity(true);boss.setDeltaMovement(Vec3.ZERO);
        bar.removeAllPlayers();viewers.forEach(bar::addPlayer);bar.setName(RaidMessages.component(RaidMessages.msg("bossbar.vitoria")));bar.setColor(BossEvent.BossBarColor.WHITE);bar.setProgress(1);
        RaidPresentation.victory(boss,name,viewers);
    }
    boolean known(net.minecraft.world.entity.Entity entity){return !closed&&entity==boss;}
    boolean known(UUID entity){return !closed&&boss.getUUID().equals(entity);}
    boolean pokemon(UUID id){return !closed&&boss.getPokemon().getUuid().equals(id);}
    void removeViewer(ServerPlayer player){bar.removePlayer(player);}
    int age(){return age;}
    static float scaleAt(float original,int tick){return original*(float)(1-.55*Math.pow(Math.clamp(tick/(double)BURST_TICK,0,1),2));}
    boolean tick(){
        if(closed)return true;
        age++;bar.setProgress(1-Math.min(1,age/(float)REVEAL_TICK));
        if(age<BURST_TICK&&!boss.isRemoved()){
            if(age%2==0){boss.getPokemon().setScaleModifier(scaleAt(scale,age));boss.setPos(origin.x,origin.y+.3*age/BURST_TICK,origin.z);boss.setDeltaMovement(Vec3.ZERO);}
            if(age%4==0)ring(2.2-1.7*age/BURST_TICK,centerY,16,new Vector3f(1,.25f,.18f));
            if(age%6==0)world.sendParticles(ParticleTypes.END_ROD,origin.x,centerY,origin.z,5,.5,.7,.5,.025);
        }
        if(age==BURST_TICK){
            world.sendParticles(ParticleTypes.FLASH,origin.x,centerY,origin.z,1,0,0,0,0);
            world.sendParticles(ParticleTypes.EXPLOSION,origin.x,centerY,origin.z,1,0,0,0,0);
            world.sendParticles(ParticleTypes.FIREWORK,origin.x,centerY,origin.z,28,.9,.7,.9,.12);
            world.sendParticles(ParticleTypes.END_ROD,origin.x,centerY,origin.z,36,.7,.9,.7,.08);
            world.playSound(null,origin.x,centerY,origin.z,SoundEvents.GENERIC_EXPLODE,SoundSource.HOSTILE,.5f,1.4f);
            removeBoss();
        }
        if(age>BURST_TICK&&age<REVEAL_TICK&&age%5==0)ring(.6+(age-BURST_TICK)*.025,origin.y+.35,20,new Vector3f(.1f,.85f,1));
        if(age==REVEAL_TICK){
            ring(1.4,origin.y+.55,32,new Vector3f(.1f,1,1));world.sendParticles(ParticleTypes.ENCHANT,origin.x,origin.y+1,origin.z,24,.7,.8,.7,.12);
            world.playSound(null,origin.x,origin.y,origin.z,SoundEvents.EXPERIENCE_ORB_PICKUP,SoundSource.PLAYERS,.6f,.85f);
        }
        return age>=REVEAL_TICK;
    }
    private void ring(double radius,double y,int points,Vector3f color){
        var particle=new DustParticleOptions(color,1.15f);
        for(int i=0;i<points;i++){double angle=2*Math.PI*i/points+age*.08;world.sendParticles(particle,origin.x+radius*Math.cos(angle),y+.15*Math.sin(angle*2+age*.1),origin.z+radius*Math.sin(angle),1,0,0,0,0);}
    }
    private void removeBoss(){RaidPresentation.clearGlow(boss);if(!boss.isRemoved())boss.discard();}
    public void close(){if(closed)return;closed=true;try{bar.removeAllPlayers();removeBoss();}finally{chunks.close();}}
}
