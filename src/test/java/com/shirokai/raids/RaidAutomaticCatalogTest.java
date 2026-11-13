package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidAutomaticCatalogTest {
    @TempDir Path dir;
    private static RaidAutomaticCatalog.Species species(String id,boolean last,int hp,int atk,int def,int spa,int spd,int speed,double height,String... labels){return new RaidAutomaticCatalog.Species("cobblemon:"+id,id,true,last,hp,atk,def,spa,spd,speed,height,Set.of(labels),List.of("flamethrower","earthquake","dragonclaw","airslash"));}
    private static RaidAutomaticCatalog.Species arcanine(){return species("arcanine",true,90,110,80,100,80,95,1.9);}
    private static RaidAutomaticCatalog.Species gyarados(){return species("gyarados",true,95,125,79,60,100,81,6.5);}
    private static RaidAutomaticCatalog.Species tyranitar(){return species("tyranitar",true,100,134,110,95,100,61,2,"powerhouse");}
    private static RaidAutomaticCatalog.Species mewtwo(){return species("mewtwo",true,106,110,90,154,90,130,2,"legendary","restricted");}
    @Test void finalFormsMayHaveTwoStagesButBabiesAndIntermediateStagesNeverQualify(){
        var c=new RaidCatalogSettings();assertTrue(RaidAutomaticCatalog.eligible(arcanine(),c));assertTrue(RaidAutomaticCatalog.eligible(gyarados(),c));
        assertFalse(RaidAutomaticCatalog.eligible(species("charmander",false,39,52,43,60,50,65,.6),c));
        assertFalse(RaidAutomaticCatalog.eligible(species("charmeleon",false,58,64,58,80,65,80,1.1),c));
        assertFalse(RaidAutomaticCatalog.eligible(species("superstrong_intermediate",false,100,150,100,150,100,100,2),c));
    }
    @Test void smallPassiveWeakAndUnimplementedSpeciesAreExcluded(){
        var c=new RaidCatalogSettings();assertFalse(RaidAutomaticCatalog.eligible(species("mew",true,100,100,100,100,100,100,.4,"mythical"),c));
        assertFalse(RaidAutomaticCatalog.eligible(species("blissey",true,255,10,10,75,135,55,1.5),c));
        assertFalse(RaidAutomaticCatalog.eligible(species("weak",true,60,85,60,70,60,70,2),c));
        var a=arcanine();assertFalse(RaidAutomaticCatalog.eligible(new RaidAutomaticCatalog.Species(a.id(),a.name(),false,true,a.hp(),a.attack(),a.defence(),a.specialAttack(),a.specialDefence(),a.speed(),a.heightMetres(),a.labels(),a.moves()),c));
        assertFalse(RaidAutomaticCatalog.eligible(species("eternatus",true,140,85,95,145,95,130,20,"legendary"),c));
    }
    @Test void onlyEveryTwentiethSlotIsSpecialAcrossHundredsOfDrawsAndRestarts(){
        var c=new RaidCatalogSettings();var state=new RaidState();var pool=List.of(arcanine(),gyarados(),tyranitar(),mewtwo());int special=0;
        for(int i=1;i<=1000;i++){
            var draw=RaidAutomaticCatalog.draw(pool,c,state.automaticRaidStarts,state.catalogRecentNormal,state.catalogRecentSpecial,new Random(i));
            assertEquals(i%20==0,draw.special(),"slot "+i);assertEquals(draw.special(),draw.species().special());if(draw.special())special++;
            RaidAutomaticCatalog.record(state,draw,c);if(i%7==0)state=JsonFiles.copy(state,RaidState.class);
        }
        assertEquals(50,special);assertEquals(1000,state.automaticRaidStarts);state.validate();
    }
    @Test void specialLabelsDoNotLeakIntoTheNineteenRegularSlots(){
        for(String label:List.of("legendary","mythical","ultra_beast","paradox")){
            var special=species(label,true,100,130,100,130,100,100,2,label);
            var pool=List.of(arcanine(),special);var c=new RaidCatalogSettings();
            for(int i=0;i<19;i++)assertEquals(arcanine().id(),RaidAutomaticCatalog.draw(pool,c,i,List.of(),List.of(),new Random(i)).species().id());
            assertEquals(special.id(),RaidAutomaticCatalog.draw(pool,c,19,List.of(),List.of(),new Random(1)).species().id());
        }
        assertFalse(tyranitar().special());
    }
    @Test void emptySpecialPoolNeverFallsBackToRegisteredLegendaryOrNormalAndDoesNotAdvanceCounter(){
        var c=new RaidCatalogSettings();var state=new RaidState();state.automaticRaidStarts=19;
        assertThrows(IllegalStateException.class,()->RaidAutomaticCatalog.draw(List.of(arcanine()),c,state.automaticRaidStarts,List.of(),List.of(),new Random(1)));
        assertEquals(19,state.automaticRaidStarts);
        assertThrows(IllegalStateException.class,()->RaidAutomaticCatalog.draw(List.of(mewtwo()),c,0,List.of(),List.of(),new Random(1)));
    }
    @Test void difficultyMatchesNativePowerAndOrdinaryPowerhousesNeverBecomeLegendary(){
        assertEquals("normal",RaidAutomaticCatalog.difficulty(species("blastoise",true,79,83,100,85,105,78,1.6)));
        assertEquals("raro",RaidAutomaticCatalog.difficulty(arcanine()));assertEquals("raro",RaidAutomaticCatalog.difficulty(gyarados()));assertEquals("epico",RaidAutomaticCatalog.difficulty(tyranitar()));
        assertEquals("lendario",RaidAutomaticCatalog.difficulty(species("articuno",true,90,85,100,95,125,85,1.7,"legendary")));assertEquals("supremo",RaidAutomaticCatalog.difficulty(mewtwo()));
    }
    @Test void profilesTrainCombatAndScaleHpByExpectedGroupWithoutUsingMaximumCapacity(){
        var c=new RaidCatalogSettings();var d=RaidAutomaticCatalog.draw(List.of(arcanine()),c,0,List.of(),List.of(),new Random(1));
        assertEquals("auto_arcanine",d.boss().id);assertEquals("raro",d.boss().difficulty);assertEquals(90,d.boss().level);assertEquals(28,d.boss().iv);assertEquals(196,d.boss().offenseEv);assertEquals(4,d.boss().moves.size());assertTrue(d.boss().competitiveNature);
        c.expectedPlayers*=2;var bigger=RaidAutomaticCatalog.draw(List.of(arcanine()),c,0,List.of(),List.of(),new Random(1));assertTrue(Math.abs(bigger.boss().hp-2*d.boss().hp)<=1);assertEquals(d.boss().damageMultiplier,bigger.boss().damageMultiplier);
        assertTrue(d.boss().hp>=29_750 && d.boss().hp<=40_250);assertTrue(d.boss().scale>=.75&&d.boss().scale<=3);
    }
    @Test void boundedHistoriesAvoidRepetitionsButSmallPoolsRemainUsable(){
        var c=new RaidCatalogSettings();c.recentNormal=1;var state=new RaidState();
        var first=RaidAutomaticCatalog.draw(List.of(arcanine(),gyarados()),c,0,List.of(),List.of(),new Random(1));RaidAutomaticCatalog.record(state,first,c);
        var second=RaidAutomaticCatalog.draw(List.of(arcanine(),gyarados()),c,1,state.catalogRecentNormal,List.of(),new Random(1));assertNotEquals(first.species().id(),second.species().id());RaidAutomaticCatalog.record(state,second,c);assertEquals(1,state.catalogRecentNormal.size());
        assertEquals(arcanine().id(),RaidAutomaticCatalog.draw(List.of(arcanine()),c,1,List.of(arcanine().id()),List.of(),new Random(1)).species().id());
        c.recentNormal=0;assertEquals(RaidAutomaticCatalog.draw(List.of(arcanine(),gyarados()),c,2,List.of(),List.of(),new Random(2)).species().id(),RaidAutomaticCatalog.draw(List.of(arcanine(),gyarados()),c,2,List.of(arcanine().id()),List.of(),new Random(2)).species().id());RaidAutomaticCatalog.record(state,first,c);assertTrue(state.catalogRecentNormal.isEmpty());
    }
    @Test void filtersAndShinyChanceAreConfigurableWithoutChangingCadence(){
        var c=new RaidCatalogSettings();c.blocked=List.of(arcanine().id());assertFalse(RaidAutomaticCatalog.eligible(arcanine(),c));c.blocked=List.of();c.shinyChance=1;
        assertTrue(RaidAutomaticCatalog.draw(List.of(arcanine()),c,0,List.of(),List.of(),new Random()).boss().shiny);
        c.shinyChance=0;assertFalse(RaidAutomaticCatalog.draw(List.of(arcanine()),c,0,List.of(),List.of(),new Random()).boss().shiny);
        assertThrows(IllegalArgumentException.class,()->RaidAutomaticCatalog.specialDue(-1));assertThrows(IllegalArgumentException.class,()->RaidAutomaticCatalog.specialDue(Long.MAX_VALUE));
    }
    @Test void loadIsEditableAndInvalidProfilesAreRejectedWithoutOverwritingFiles()throws Exception{
        Path path=dir.resolve("catalog.json");var c=RaidCatalogSettings.load(path);assertEquals(5,c.profiles.size());
        c.profiles.get("supremo").targetTurns=1;JsonFiles.write(path,c);assertThrows(IllegalArgumentException.class,()->RaidCatalogSettings.load(path));assertEquals(1,JsonFiles.read(path,RaidCatalogSettings.class).profiles.get("supremo").targetTurns);
        c=new RaidCatalogSettings();c.profiles.get("normal").healthEv=252;c.profiles.get("normal").offenseEv=252;c.profiles.get("normal").speedEv=252;assertThrows(IllegalArgumentException.class,c::validate);
    }
    @Test void legacyStateStartsAtZeroAndEmptyManualBossDirectoryIsAllowedOnlyInCatalogMode()throws Exception{
        var legacy=new com.google.gson.Gson().fromJson("{}",RaidState.class);assertEquals(0,legacy.automaticRaidStarts);legacy.validate();
        assertTrue(RaidBossCatalog.load(dir,true).isEmpty());assertThrows(java.io.IOException.class,()->RaidBossCatalog.load(dir));
        legacy.automaticRaidStarts=-1;assertThrows(IllegalArgumentException.class,legacy::validate);
    }
}
