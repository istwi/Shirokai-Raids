package com.shirokai.raids;

import java.nio.file.Path;
import java.io.IOException;
import java.util.*;

/** Common profiles replace per-Pokemon registrations for scheduled raids. */
final class RaidCatalogSettings {
    static final int SPECIAL_INTERVAL=20;
    boolean enabled=true;
    int minimumBaseStats=480,minimumOffense=85;
    double minimumHeightMetres=1.2,maximumHeightMetres=10;
    int expectedPlayers=10,estimatedDamagePerTurn=100,recentNormal=8,recentSpecial=3;
    double shinyChance=.01;
    List<String> namespaces=List.of("cobblemon");
    List<String> blocked=List.of("cobblemon:ditto","cobblemon:shedinja","cobblemon:smeargle","cobblemon:shuckle","cobblemon:wobbuffet","cobblemon:clefable","cobblemon:wigglytuff","cobblemon:togekiss","cobblemon:audino","cobblemon:blissey","cobblemon:meganium","cobblemon:azumarill","cobblemon:delcatty","cobblemon:alcremie","cobblemon:slurpuff","cobblemon:aromatisse","cobblemon:maushold","cobblemon:dudunsparce","cobblemon:mrmime","cobblemon:mrrime");
    Map<String,Profile> profiles=profiles();
    static final class Profile {
        int level,iv,offenseEv,speedEv,healthEv,targetTurns;
        double damageMultiplier,targetHeightMetres,referencePower;
        String rewards="default";
        Profile(int level,int iv,int offenseEv,int speedEv,int healthEv,int turns,double damage,double height,double reference){
            this.level=level;this.iv=iv;this.offenseEv=offenseEv;this.speedEv=speedEv;this.healthEv=healthEv;targetTurns=turns;damageMultiplier=damage;targetHeightMetres=height;referencePower=reference;
        }
        void validate(){
            require(level>=1&&level<=100&&iv>=0&&iv<=31&&offenseEv>=0&&offenseEv<=252&&speedEv>=0&&speedEv<=252&&healthEv>=0&&healthEv<=252&&offenseEv+speedEv+healthEv<=510,"IVs/EVs/nível inválidos");
            require(targetTurns>=1&&targetTurns<=1000&&finite(damageMultiplier,.1,5)&&finite(targetHeightMetres,2,10)&&finite(referencePower,100,1500)&&rewards!=null&&rewards.matches("[a-z0-9_-]+"),"Perfil de boss inválido");
        }
    }
    private static Map<String,Profile> profiles(){
        var p=new LinkedHashMap<String,Profile>();
        p.put("normal",new Profile(85,25,128,128,128,25,1,3.5,520));
        p.put("raro",new Profile(90,28,196,196,116,35,1.10,4,560));
        p.put("epico",new Profile(95,31,252,252,4,50,1.20,4.5,620));
        p.put("lendario",new Profile(100,31,252,252,4,65,1.35,5,625));
        p.put("supremo",new Profile(100,31,252,252,4,85,1.50,6,700));
        return p;
    }
    static RaidCatalogSettings load(Path path)throws IOException{JsonFiles.defaultFile(path,new RaidCatalogSettings());var c=JsonFiles.read(path,RaidCatalogSettings.class);c.validate();return c;}
    void validate(){
        require(minimumBaseStats>=400&&minimumBaseStats<=720&&minimumOffense>=70&&minimumOffense<=200&&finite(minimumHeightMetres,.5,5)&&finite(maximumHeightMetres,minimumHeightMetres,30),"Filtros do catálogo inválidos");
        require(expectedPlayers>=1&&expectedPlayers<=100&&estimatedDamagePerTurn>=10&&estimatedDamagePerTurn<=1000&&recentNormal>=0&&recentNormal<=100&&recentSpecial>=0&&recentSpecial<=100&&finite(shinyChance,0,1),"Balanceamento do catálogo inválido");
        require(namespaces!=null&&!namespaces.isEmpty()&&namespaces.size()<=32&&namespaces.stream().allMatch(s->s!=null&&s.matches("[a-z0-9_.-]+"))&&blocked!=null&&blocked.size()<=1000&&blocked.stream().allMatch(s->s!=null&&s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")),"Lista de bloqueio/namespaces inválida");
        require(profiles!=null&&profiles.keySet().equals(RaidRewardSettings.DIFFICULTIES),"Configure as cinco dificuldades do catálogo");
        for(var p:profiles.values()){require(p!=null,"Perfil ausente");p.validate();}
        var order=List.of("normal","raro","epico","lendario","supremo");
        for(int i=1;i<order.size();i++){
            var previous=profiles.get(order.get(i-1));var next=profiles.get(order.get(i));
            require(next.level>=previous.level&&next.targetTurns>=previous.targetTurns&&next.damageMultiplier>=previous.damageMultiplier,"Perfis devem crescer com a dificuldade");
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    private static boolean finite(double value,double min,double max){return Double.isFinite(value)&&value>=min&&value<=max;}
}
