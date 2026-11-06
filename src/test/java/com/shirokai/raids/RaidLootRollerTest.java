package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidLootRollerTest {
    private static final class Draws extends Random {
        final int[] values; int index;
        Draws(int... values) { this.values=values; }
        @Override public int nextInt(int bound) {
            int value=values[index++];
            assertTrue(value>=0 && value<bound,"Draw fora do intervalo"); return value;
        }
    }
    @Test void defaultRarityDistributionMatchesEachConfiguredContributionBandExactly() {
        var table=RaidConfig.Rewards.defaults();
        for(var tier:table.tiers) {
            var counts=new HashMap<String,Integer>();
            for(int percentile=0;percentile<100;percentile++) {
                var drops=RaidLootRoller.roll(table,tier.minimumContribution,new Draws(percentile,0,0));
                assertEquals(1,drops.size());assertEquals(tier.id,drops.getFirst().tier());
                counts.merge(drops.getFirst().rarity(),1,Integer::sum);
            }
            assertEquals(tier.rarityChances,counts);
        }
        assertTrue(RaidLootRoller.roll(table,.009,new Random(1)).isEmpty());
        assertTrue(RaidLootRoller.roll(table,Double.NaN,new Random(1)).isEmpty());
    }
    @Test void itemWeightsAreASecondIndependentDrawWithinChosenRarity() {
        var table=RaidConfig.Rewards.defaults();
        for(var tier:table.tiers) tier.rarityChances=Map.of("common",0,"rare",0,"epic",0,"legendary",100);
        table.itemPools=new HashMap<>(table.itemPools);
        table.itemPools.put("legendary",List.of(new RaidConfig.Reward("minecraft:apple",1,1,1),new RaidConfig.Reward("minecraft:diamond",2,2,3)));
        var counts=new HashMap<String,Integer>();
        for(int choice=0;choice<4;choice++) {
            var drop=RaidLootRoller.roll(table,.05,new Draws(0,choice,0)).getFirst();
            assertEquals("legendary",drop.rarity());counts.merge(drop.item(),1,Integer::sum);
        }
        assertEquals(Map.of("minecraft:apple",1,"minecraft:diamond",3),counts);
    }
    @Test void itemBudgetAndRollCountAreBoundedAndSameAcrossTiers() {
        var table=RaidConfig.Rewards.defaults();table.rollsPerPlayer=3;table.maximumItems=2;
        for(var tier:table.tiers) {
            var drops=RaidLootRoller.roll(table,tier.minimumContribution,new Random(42));
            assertTrue(drops.size()<=3);assertTrue(drops.stream().mapToInt(RaidLootRoller.Drop::count).sum()<=2);
        }
    }
    @Test void legacyCustomItemsAndRollRulesRemainAvailable() {
        var table=new RaidConfig.Rewards();table.items=List.of(new RaidConfig.Reward("minecraft:apple",2,2,1));
        var drops=RaidLootRoller.roll(table,.5,new Random(42));
        assertEquals(4,drops.size());assertTrue(drops.stream().allMatch(d -> d.item().equals("minecraft:apple") && d.count()==2));
    }
    @Test void invalidProbabilitiesRangesReferencesAndPoolsFailBeforeRolling() {
        var table=RaidConfig.Rewards.defaults();
        table.tiers.getFirst().rarityChances=Map.of("common",99,"rare",0,"epic",0,"legendary",0);
        assertThrows(IllegalArgumentException.class,table::validate);
        var missing=RaidConfig.Rewards.defaults();missing.itemPools=Map.of("common",missing.itemPools.get("common"));
        assertThrows(IllegalArgumentException.class,missing::validate);
        var order=RaidConfig.Rewards.defaults();order.tiers.get(1).minimumContribution=.001;
        assertThrows(IllegalArgumentException.class,order::validate);
        var badItem=new RaidConfig.Rewards();badItem.items=List.of(new RaidConfig.Reward("minecraft:air",1,1,1));
        assertThrows(IllegalArgumentException.class,badItem::validate);
    }
    @Test void bossControlsValidateFiniteDamageHpScaleAndDefaults() {
        var boss=new RaidConfig.Boss();boss.validate();assertEquals(1,boss.damageMultiplier);
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,101}) {
            boss.damageMultiplier=value;assertThrows(IllegalArgumentException.class,boss::validate);
        }
        boss.damageMultiplier=0;boss.validate();boss.damageMultiplier=100;boss.validate();
        boss.hp=0;assertThrows(IllegalArgumentException.class,boss::validate);
    }
    @Test void jsonRoundTripPreservesOldFilesAndNewTieredTables() throws Exception {
        var gson=new com.google.gson.Gson();
        var old=gson.fromJson("{\"items\":[{\"item\":\"minecraft:apple\",\"minimum\":1,\"maximum\":2,\"weight\":1}]}",RaidConfig.Rewards.class);
        old.validate();assertNull(old.tiers);assertEquals("minecraft:apple",old.items.getFirst().item);
        var restored=gson.fromJson(gson.toJson(RaidConfig.Rewards.defaults()),RaidConfig.Rewards.class);
        restored.validate();assertEquals(4,restored.tiers.size());assertEquals(4,restored.itemPools.size());
        var boss=gson.fromJson("{\"hp\":5000,\"scale\":3,\"rewards\":\"custom\"}",RaidConfig.Boss.class);
        boss.validate();assertEquals(5000,boss.hp);assertEquals(3,boss.scale);assertEquals(1,boss.damageMultiplier);
    }
}
