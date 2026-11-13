package com.shirokai.raids;

import java.util.*;
import java.util.random.RandomGenerator;

/** Selection/balance rules independent of the Minecraft runtime. Special slots never fall back to normal. */
final class RaidAutomaticCatalog {
    static final Set<String> SPECIAL_LABELS=Set.of("legendary","mythical","ultra_beast","paradox");
    record Species(String id,String name,boolean implemented,boolean finalEvolution,int hp,int attack,int defence,int specialAttack,int specialDefence,int speed,double heightMetres,Set<String> labels,List<String> moves) {
        int total(){return hp+attack+defence+specialAttack+specialDefence+speed;}
        int offense(){return Math.max(attack,specialAttack);}
        boolean special(){return labels.stream().anyMatch(SPECIAL_LABELS::contains);}
        double power(){return total()+(offense()-100)*.75+(speed-80)*.6+(hp+defence+specialDefence-240)*.15;}
    }
    record Draw(Species species,RaidConfig.Boss boss,boolean special) {}
    static boolean specialDue(long starts){if(starts<0||starts==Long.MAX_VALUE)throw new IllegalArgumentException("Contador automático inválido");return starts%RaidCatalogSettings.SPECIAL_INTERVAL==RaidCatalogSettings.SPECIAL_INTERVAL-1;}
    static boolean eligible(Species s,RaidCatalogSettings c){
        return s.implemented&&s.finalEvolution&&s.id.matches("[a-z0-9_.-]+:[a-z0-9_.-]+")&&c.namespaces.contains(s.id.split(":",2)[0])&&!c.blocked.contains(s.id)
            &&s.total()>=c.minimumBaseStats&&s.offense()>=c.minimumOffense&&s.heightMetres>=c.minimumHeightMetres&&s.heightMetres<=c.maximumHeightMetres&&s.moves.size()>=2;
    }
    static String difficulty(Species s){return s.special()?(s.total()>=660||s.labels.contains("restricted")?"supremo":"lendario"):(s.power()>=590?"epico":s.power()>=535?"raro":"normal");}
    static Draw draw(Collection<Species> species,RaidCatalogSettings settings,long starts,List<String> recentNormal,List<String> recentSpecial,RandomGenerator random){
        boolean special=specialDue(starts);
        var pool=species.stream().filter(s->eligible(s,settings)&&s.special()==special).sorted(Comparator.comparing(Species::id)).toList();
        if(pool.isEmpty())throw new IllegalStateException(special?"20ª raid aguarda um lendário/especial elegível no catálogo; nenhuma raid comum foi substituída.":"Nenhum Pokémon final e forte disponível com os filtros do catálogo.");
        var history=special?recentSpecial:recentNormal;
        int limit=special?settings.recentSpecial:settings.recentNormal;
        var recent=history.subList(Math.max(0,history.size()-limit),history.size());
        var fresh=pool.stream().filter(s->!recent.contains(s.id)).toList();
        // A small/blocked pool can exhaust history: repeat an eligible species, never weaken filters.
        var choices=fresh.isEmpty()?pool:fresh;
        var chosen=choices.get(random.nextInt(choices.size()));
        String difficulty=difficulty(chosen);var profile=settings.profiles.get(difficulty);
        var boss=new RaidConfig.Boss();boss.id="auto_"+chosen.id.substring(chosen.id.indexOf(':')+1).replaceAll("[^a-z0-9_-]","_");boss.species=chosen.id;boss.difficulty=difficulty;
        boss.name=chosen.name+" — Raid "+switch(difficulty){case "raro"->"Rara";case "epico"->"Épica";case "lendario"->"Lendária";case "supremo"->"Suprema";default->"Normal";};
        boss.level=profile.level;boss.iv=profile.iv;boss.offenseEv=profile.offenseEv;boss.speedEv=profile.speedEv;boss.healthEv=profile.healthEv;boss.strongPhysical=chosen.attack>=chosen.specialAttack;boss.competitiveNature=true;
        boss.moves=List.copyOf(chosen.moves);boss.rewards=profile.rewards;
        double strength=Math.clamp(chosen.power()/profile.referencePower,.85,1.15);
        boss.hp=Math.round((long)settings.expectedPlayers*settings.estimatedDamagePerTurn*profile.targetTurns*strength);
        boss.damageMultiplier=profile.damageMultiplier;
        boss.scale=(float)Math.clamp(profile.targetHeightMetres/chosen.heightMetres,.75,3);
        boss.shiny=random.nextDouble()<settings.shinyChance;boss.validate();
        return new Draw(chosen,boss,special);
    }
    static void record(RaidState state,Draw draw,RaidCatalogSettings settings){
        if(state.automaticRaidStarts==Long.MAX_VALUE)throw new IllegalStateException("Contador automático esgotado");
        state.automaticRaidStarts++;
        var history=draw.special?state.catalogRecentSpecial:state.catalogRecentNormal;
        int limit=draw.special?settings.recentSpecial:settings.recentNormal;
        history.remove(draw.species.id);history.add(draw.species.id);
        while(history.size()>limit)history.removeFirst();
    }
}
