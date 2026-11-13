package com.shirokai.raids;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.*;

/** Uses loaded Cobblemon data, base forms only. No synthetic species or invented moves. */
final class RaidCatalogPokemon {
    static final Set<String> UNSUITABLE_MOVES=Set.of("explosion","selfdestruct","mistyexplosion","finalgambit","memento","mindblown","steelbeam","chloroblast","hyperbeam","gigaimpact","frenzyplant","blastburn","hydrocannon","rockwrecker","roaroftime","eternabeam","prismaticlaser","meteorassault","freezeshock","iceburn","razorwind","skullbash","solarbeam","solarblade","skyattack","fly","dig","dive","bounce","phantomforce","shadowforce","focuspunch","futuresight","doomdesire","belch","snore","dreameater","lastresort","synchronoise");
    static List<RaidAutomaticCatalog.Species> species(RaidCatalogSettings settings){
        var result=new ArrayList<RaidAutomaticCatalog.Species>();
        for(var species:PokemonSpecies.getSpecies()){
            if(!species.getImplemented())continue;
            var form=species.getStandardForm();var stats=form.getBaseStats();
            var labels=new HashSet<>(species.getLabels());labels.addAll(form.getLabels());
            var metadata=new RaidAutomaticCatalog.Species(species.getResourceIdentifier().toString(),species.getName(),true,form.getEvolutions().isEmpty(),stats.getOrDefault(Stats.HP,0),stats.getOrDefault(Stats.ATTACK,0),stats.getOrDefault(Stats.DEFENCE,0),stats.getOrDefault(Stats.SPECIAL_ATTACK,0),stats.getOrDefault(Stats.SPECIAL_DEFENCE,0),stats.getOrDefault(Stats.SPEED,0),form.getHeight()/10d,Set.copyOf(labels),List.of());
            int level=settings.profiles.get(RaidAutomaticCatalog.difficulty(metadata)).level;
            var moves=new LinkedHashSet<MoveTemplate>();
            moves.addAll(form.getMoves().getLevelUpMovesUpTo(level));moves.addAll(form.getMoves().getTmMoves());moves.addAll(form.getMoves().getEvolutionMoves());
            var usable=moves.stream().filter(m->m.getPower()>=40&&m.getPower()<=150&&(m.getAccuracy()>=80||m.getAccuracy()<=0)&&!UNSUITABLE_MOVES.contains(m.getName())).toList();
            var attacks=selectMoves(usable,stats.getOrDefault(Stats.ATTACK,0),stats.getOrDefault(Stats.SPECIAL_ATTACK,0),form.getPrimaryType().getName(),form.getSecondaryType()==null?"":form.getSecondaryType().getName());
            result.add(new RaidAutomaticCatalog.Species(species.getResourceIdentifier().toString(),species.getName(),true,form.getEvolutions().isEmpty(),stats.getOrDefault(Stats.HP,0),stats.getOrDefault(Stats.ATTACK,0),stats.getOrDefault(Stats.DEFENCE,0),stats.getOrDefault(Stats.SPECIAL_ATTACK,0),stats.getOrDefault(Stats.SPECIAL_DEFENCE,0),stats.getOrDefault(Stats.SPEED,0),form.getHeight()/10d,Set.copyOf(labels),attacks));
        }
        return List.copyOf(result);
    }
    private static List<String> selectMoves(List<MoveTemplate> moves,int attack,int specialAttack,String primary,String secondary){
        var ranked=moves.stream().sorted(Comparator.<MoveTemplate>comparingDouble(m->{
            boolean physical=m.getDamageCategory().getName().equalsIgnoreCase("physical");
            double stab=m.getElementalType().getName().equals(primary)||m.getElementalType().getName().equals(secondary)?1.5:1;
            return m.getPower()*(physical?attack:specialAttack)*stab*(m.getAccuracy()<=0?1:m.getAccuracy()/100);
        }).reversed().thenComparing(MoveTemplate::getName)).toList();
        var chosen=new ArrayList<MoveTemplate>();var types=new HashSet<String>();
        for(var move:ranked)if(types.add(move.getElementalType().getName())){chosen.add(move);if(chosen.size()==4)break;}
        for(var move:ranked)if(chosen.size()<4&&!chosen.contains(move))chosen.add(move);
        return chosen.stream().map(MoveTemplate::getName).toList();
    }
    static void train(Pokemon pokemon,RaidConfig.Boss boss){
        if(boss.iv>=0)for(var stat:List.of(Stats.HP,Stats.ATTACK,Stats.DEFENCE,Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE))pokemon.setIV(stat,boss.iv);
        pokemon.setIV(Stats.SPEED,15);
        if(boss.offenseEv>0)pokemon.setEV(boss.strongPhysical?Stats.ATTACK:Stats.SPECIAL_ATTACK,boss.offenseEv);
        if(boss.speedEv>0)pokemon.setEV(Stats.SPEED,boss.speedEv);
        if(boss.healthEv>0)pokemon.setEV(Stats.HP,boss.healthEv);
        if(boss.competitiveNature)pokemon.setNature(boss.strongPhysical?Natures.ADAMANT:Natures.MODEST);
        if(!boss.moves.isEmpty()){
            pokemon.getMoveSet().clear();int slot=0;
            for(var id:boss.moves){var move=Moves.INSTANCE.getByName(id);if(move==null)throw new IllegalArgumentException("Golpe inexistente: "+id);pokemon.getMoveSet().setMove(slot++,move.create());}
        }
        pokemon.heal();
    }
}
