package com.shirokai.raids;

import com.shirokai.raids.core.RaidRewardManager;
import java.util.*;

/** Exact initial-roll probabilities. No RNG, account lookup, inventory or reward-state mutations. */
final class RaidRewardOdds {
    enum Scope { GENERAL, SPECIAL, LEGACY }
    record Line(String id,String type,String reference,String rarity,double chance,double configuredChance,double selectionChance,long minimum,long maximum,boolean eligible,double minimumContribution,int maximumRank) {}
    record Result(String difficulty,double multiplier,List<Double> topChances,List<Line> lines,Map<String,Double> types,Map<String,Double> rarities,double none,int rolls,boolean enabled,String basis) {}
    static Result calculate(RaidRewardSettings settings,String difficulty,String boss,Scope scope,int rank,double contribution,RaidConfig.Rewards legacy){
        if(rank<1||!RaidRewardSettings.probability(contribution))throw new IllegalArgumentException("Simulação inválida");
        var override=settings.bosses.values.get(boss);
        String effective=override!=null&&override.difficulty!=null?override.difficulty:difficulty;
        var d=settings.difficulties.values.get(effective);
        if(d==null)throw new IllegalArgumentException("Dificuldade desconhecida");
        double multiplier=d.quantityMultiplier*(override==null?1:override.quantityMultiplier);
        var top=settings.top3.chances.get(effective);
        if(scope==Scope.LEGACY)return legacy(legacy,contribution,settings.integrations.legacyItems,effective,multiplier,top);
        var pool=scope==Scope.GENERAL?settings.general.pools.get(override!=null&&override.general!=null?override.general:d.general):settings.special.pools.get(override!=null&&override.special!=null?override.special:d.special);
        boolean qualifies=contribution>0&&contribution>=settings.integrations.minimumContribution;
        int total=pool.stream().filter(r->RaidRewardJournal.eligible(r,contribution,rank)).mapToInt(r->r.weight).sum();
        var lines=new ArrayList<Line>();
        for(var r:pool){
            boolean eligible=qualifies&&RaidRewardJournal.eligible(r,contribution,rank)&&(scope==Scope.GENERAL||rank<=3);
            double selection=scope==Scope.GENERAL?1:total==0||!RaidRewardJournal.eligible(r,contribution,rank)?0:(double)r.weight/total;
            double gate=scope==Scope.GENERAL?1:rank<=3?top.get(rank-1):0;
            double chance=settings.integrations.enabled&&eligible?r.chance*selection*gate:0;
            long min=RaidRewardSettings.scaledAmount(r,r.minimum,contribution,multiplier,settings.integrations.maximumAmount).longValueExact();
            long max=RaidRewardSettings.scaledAmount(r,r.maximum,contribution,multiplier,settings.integrations.maximumAmount).longValueExact();
            lines.add(new Line(r.id,r.type,r.reference,Integer.toString(r.rarity),chance,r.chance,selection,min,max,eligible,Math.max(settings.integrations.minimumContribution,r.minimumContribution),r.maximumRank));
        }
        return result(effective,multiplier,top,lines,scope!=Scope.GENERAL,1,settings.integrations.enabled,scope==Scope.GENERAL?"Por vitória; sorteios independentes":"Por vitória e posição; antes de duplicatas");
    }
    private static Result legacy(RaidConfig.Rewards table,double contribution,boolean enabled,String difficulty,double multiplier,List<Double> top){
        if(table==null)return result(difficulty,multiplier,top,List.of(),true,0,enabled,"Nenhuma tabela de itens vinculada");
        boolean eligible=contribution>0&&contribution>=table.minimumContribution;
        RaidConfig.RewardTier tier=null;
        if(table.tiers!=null)for(var t:table.tiers)if(contribution>=t.minimumContribution)tier=t;
        if(table.tiers!=null&&tier==null)tier=table.tiers.getFirst();
        int rolls=eligible&&enabled?(tier==null?RaidRewardManager.rolls(contribution,table.minimumContribution):table.rollsPerPlayer):0;
        var lines=new ArrayList<Line>();
        if(tier==null)addLegacy(lines,table.items,"legacy",1,eligible&&enabled,table.minimumContribution);
        else for(var e:new TreeMap<>(tier.rarityChances).entrySet())addLegacy(lines,table.itemPools.get(e.getKey()),e.getKey(),e.getValue()/100d,eligible&&enabled,table.minimumContribution);
        return result(difficulty,multiplier,top,lines,true,rolls,enabled,"Por sorteio; até "+rolls+" sorteio(s), limite "+table.maximumItems+" itens");
    }
    private static void addLegacy(List<Line> lines,List<RaidConfig.Reward> items,String rarity,double chance,boolean eligible,double minimumContribution){
        if(items==null)return;int total=items.stream().mapToInt(r->r.weight).sum(),index=0;
        for(var r:items){double selection=total==0?0:(double)r.weight/total;lines.add(new Line("legacy_"+rarity+"_"+index++,"item",r.item,rarity,eligible?chance*selection:0,chance,selection,r.minimum,r.maximum,eligible,minimumContribution,0));}
    }
    private static Result result(String difficulty,double multiplier,List<Double> top,List<Line> lines,boolean exclusive,int rolls,boolean enabled,String basis){
        var types=new TreeMap<String,Double>();var rarities=new TreeMap<String,Double>();double none=1;
        for(var l:lines){
            merge(types,typeKey(l.type,l.reference),l.chance,exclusive);merge(rarities,l.rarity,l.chance,exclusive);
            none=exclusive?none-l.chance:none*(1-l.chance);
        }
        return new Result(difficulty,multiplier,List.copyOf(top),List.copyOf(lines),Collections.unmodifiableMap(types),Collections.unmodifiableMap(rarities),Math.clamp(none,0,1),rolls,enabled,basis);
    }
    private static void merge(Map<String,Double> values,String key,double chance,boolean exclusive){values.merge(key,chance,(a,b)->Math.clamp(exclusive?a+b:1-(1-a)*(1-b),0,1));}
    static String typeKey(String type,String reference){return type.equals("currency")?reference:type;}
    static String typeName(String key){return switch(key){case "impactor:dollars"->"Dollars";case "impactor:tokens"->"Tokens";case "currency"->"Moedas";case "fragment"->"Fragmentos de chave";case "title"->"Títulos";case "particle"->"Partículas";case "item"->"Itens";default->key;};}
    static String rarityName(String key){return switch(key){case "common"->"Comum";case "rare"->"Raro";case "epic"->"Épico";case "legendary"->"Lendário";case "legacy"->"Itens tradicionais";case "0"->"Padrão (raridade 0)";default->key.matches("[0-9]+")?"Raridade "+key:key;};}
    static String percent(double value){if(value>0&&value<.0001)return "<0,01%";return java.math.BigDecimal.valueOf(Math.clamp(value,0,1)*100).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.',',')+"%";}
}
