package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidRewardOddsTest {
    private static RaidRewardOdds.Result odds(RaidRewardSettings s,RaidRewardOdds.Scope scope,int rank,double share){return RaidRewardOdds.calculate(s,"normal","",scope,rank,share,RaidConfig.Rewards.defaults());}
    @Test void generalDisplaysIndependentChancesRatherThanNormalizingToOneHundredPercent(){
        var r=odds(RaidRewardSettings.defaults(),RaidRewardOdds.Scope.GENERAL,1,.1);
        assertEquals(List.of(1d,1d,.35),r.lines().stream().map(RaidRewardOdds.Line::chance).toList());
        assertEquals(1,r.types().get("impactor:dollars"));assertEquals(1,r.types().get("impactor:tokens"));assertEquals(.35,r.types().get("fragment"));assertEquals(0,r.none());assertEquals(1,r.rarities().get("0"));
    }
    @Test void topThreeIncludesPositionGateWeightAndItemChance(){
        var s=RaidRewardSettings.defaults();var first=odds(s,RaidRewardOdds.Scope.SPECIAL,1,.1);
        assertTrue(first.lines().stream().allMatch(l->Math.abs(l.chance()-.1)<1e-10));assertEquals(.7,first.none(),1e-10);assertEquals(.3,first.rarities().get("0"),1e-10);
        assertTrue(odds(s,RaidRewardOdds.Scope.SPECIAL,2,.1).lines().stream().allMatch(l->Math.abs(l.chance()-.06)<1e-10));
        var fourth=odds(s,RaidRewardOdds.Scope.SPECIAL,4,.1);assertEquals(1,fourth.none());assertTrue(fourth.lines().stream().allMatch(l->!l.eligible()&&l.chance()==0));
    }
    @Test void contributionAndRankConditionsRenormalizeOnlyEligibleWeights(){
        var s=RaidRewardSettings.defaults();var pool=s.special.pools.get("normal");pool.getFirst().minimumContribution=.2;pool.get(1).maximumRank=1;
        var first=odds(s,RaidRewardOdds.Scope.SPECIAL,1,.1);assertEquals(0,first.lines().getFirst().chance());assertEquals(0,first.lines().getFirst().selectionChance());assertEquals(.15,first.lines().get(1).chance(),1e-10);
        var second=odds(s,RaidRewardOdds.Scope.SPECIAL,2,.1);assertEquals(.18,second.lines().get(2).chance(),1e-10);
        assertTrue(odds(s,RaidRewardOdds.Scope.GENERAL,1,.001).lines().stream().allMatch(l->l.chance()==0));
    }
    @Test void zeroChanceStillOccupiesWeightAndDoesNotGetRerolledInThePreview(){
        var s=RaidRewardSettings.defaults();s.special.pools.get("normal").getFirst().chance=0;
        var r=odds(s,RaidRewardOdds.Scope.SPECIAL,1,.1);assertEquals(.8,r.none(),1e-10);assertEquals(0,r.lines().getFirst().chance());assertEquals(.1,r.lines().get(1).chance(),1e-10);
    }
    @Test void typeAndRaritySummariesComputeAtLeastOneForIndependentDraws(){
        var s=RaidRewardSettings.defaults();var a=new RaidRewardSettings.Reward("a","fragment","shiny1",1,1);a.chance=.3;var b=new RaidRewardSettings.Reward("b","fragment","shiny2",1,1);b.chance=.4;
        s.general.pools.put("normal",List.of(a,b));var r=odds(s,RaidRewardOdds.Scope.GENERAL,1,.1);assertEquals(.58,r.types().get("fragment"),1e-10);assertEquals(.58,r.rarities().get("0"),1e-10);assertEquals(.42,r.none(),1e-10);
    }
    @Test void disabledModularRewardsDoNotHideLegacyDropsAndLegacyUsesItsOwnContributionThreshold(){
        var s=RaidRewardSettings.defaults();s.integrations.enabled=false;assertEquals(1,odds(s,RaidRewardOdds.Scope.GENERAL,1,.1).none());
        var legacy=odds(s,RaidRewardOdds.Scope.LEGACY,1,.1);assertEquals(0,legacy.none(),1e-10);assertEquals(.55,legacy.rarities().get("common"),1e-10);
        s.integrations.legacyItems=false;assertEquals(1,odds(s,RaidRewardOdds.Scope.LEGACY,1,.1).none());
    }
    @Test void legacyRarityAndItemWeightsArePerDrawAndAllTiersRemainInspectable(){
        var s=RaidRewardSettings.defaults();var table=RaidConfig.Rewards.defaults();table.rollsPerPlayer=3;
        var r=RaidRewardOdds.calculate(s,"normal","",RaidRewardOdds.Scope.LEGACY,1,.5,table);
        assertEquals(3,r.rolls());assertEquals(.2,r.rarities().get("common"),1e-10);assertEquals(.45,r.rarities().get("rare"),1e-10);
        var below=RaidRewardOdds.calculate(s,"normal","",RaidRewardOdds.Scope.LEGACY,1,0,table);assertFalse(below.lines().isEmpty());assertEquals(1,below.none());assertEquals(0,below.rolls());
    }
    @Test void bossOverridesAndQuantityBoundsMatchTheDeliveryFormula(){
        var s=RaidRewardSettings.defaults();var o=new RaidRewardSettings.Override();o.difficulty="supremo";o.general="normal";o.quantityMultiplier=2;s.bosses.values.put("auto_arcanine",o);
        var r=RaidRewardOdds.calculate(s,"normal","auto_arcanine",RaidRewardOdds.Scope.GENERAL,1,.5,null);
        assertEquals("supremo",r.difficulty());assertEquals(6,r.multiplier());assertEquals(s.top3.chances.get("supremo"),r.topChances());
        assertEquals(300,r.lines().getFirst().minimum());assertEquals(300,r.lines().getFirst().maximum());
        var fragment=r.lines().get(2);
        assertEquals(10,fragment.minimum());assertEquals(21,fragment.maximum());
    }
    @Test void previewDoesNotMutateConfigurationAndPercentFormattingPreservesTinyNonzeroValues(){
        var s=RaidRewardSettings.defaults();var before=new com.google.gson.Gson().toJson(s);for(var scope:RaidRewardOdds.Scope.values())odds(s,scope,1,.25);assertEquals(before,new com.google.gson.Gson().toJson(s));
        assertEquals("<0,01%",RaidRewardOdds.percent(.0000001));assertEquals("33,33%",RaidRewardOdds.percent(1d/3));assertEquals("100%",RaidRewardOdds.percent(1));
        assertTrue(RaidAdminRewardMenu.allowedCommand("raidadmin recompensas auto_arcanine"));assertFalse(RaidAdminRewardMenu.allowedCommand("raidadmin iniciar rayquaza"));assertFalse(RaidAdminRewardMenu.allowedCommand("raidadmin rewardreview x repetir y"));
    }
}
