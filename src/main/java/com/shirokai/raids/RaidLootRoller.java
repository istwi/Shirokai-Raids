package com.shirokai.raids;

import com.shirokai.raids.core.RaidRewardManager;
import java.util.*;
import java.util.random.RandomGenerator;

/** Independent rarity and item draws. Legacy tables retain their original roll rules. */
public final class RaidLootRoller {
    public record Drop(String item,int count,String tier,String rarity) {}
    private RaidLootRoller() {}
    public static List<Drop> roll(RaidConfig.Rewards table,double contribution,RandomGenerator random) {
        table.validate();
        if(!Double.isFinite(contribution) || contribution<=0 || contribution<table.minimumContribution) return List.of();
        if(contribution>1) throw new IllegalArgumentException("Contribuição acima de 100%");
        RaidConfig.RewardTier tier=null;
        if(table.tiers!=null) for(var candidate:table.tiers)
            if(contribution>=candidate.minimumContribution) tier=candidate;
        int rolls=tier==null?RaidRewardManager.rolls(contribution,table.minimumContribution):table.rollsPerPlayer;
        var drops=new ArrayList<Drop>(); int remaining=table.maximumItems;
        for(int i=0;i<rolls && remaining>0;i++) {
            String rarity="legacy";
            List<RaidConfig.Reward> pool=table.items;
            if(tier!=null) {
                var chances=tier.rarityChances.entrySet().stream().filter(e -> e.getValue()>0)
                    .sorted(Map.Entry.comparingByKey()).toList();
                rarity=chances.get(RaidRewardManager.select(chances.stream().map(Map.Entry::getValue).toList(),random)).getKey();
                pool=table.itemPools.get(rarity);
            }
            var item=pool.get(RaidRewardManager.select(pool.stream().map(e -> e.weight).toList(),random));
            int count=Math.min(remaining,random.nextInt(item.maximum-item.minimum+1)+item.minimum);
            remaining-=count;
            drops.add(new Drop(item.item,count,tier==null?"legacy":tier.id,rarity));
        }
        return List.copyOf(drops);
    }
}
