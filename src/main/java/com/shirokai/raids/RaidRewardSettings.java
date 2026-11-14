package com.shirokai.raids;

import java.io.IOException;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;

/** Seven independent JSON files; load and validate a complete candidate before installing. */
public final class RaidRewardSettings {
    public static final Set<String> DIFFICULTIES=Set.of("normal","raro","epico","lendario","supremo");
    public enum Duplicate { REROLL, COMPENSATE, SKIP }
    public static final class Reward {
        public String id, type="currency", reference="impactor:dollars";
        public long minimum=1, maximum=1;
        public double chance=1, minimumContribution=0, baseMultiplier=.25, damageMultiplier=3;
        public int weight=1, rarity=0, maximumRank=0;
        public Reward() {}
        public Reward(String id,String type,String reference,long min,long max) {this.id=id;this.type=type;this.reference=reference;minimum=min;maximum=max;}
        public boolean cosmetic() { return type.equals("title") || type.equals("particle"); }
        public void validate() {
            require(id!=null&&id.matches("[a-z0-9_.-]+")&&type!=null&&type.matches("[a-z0-9_.-]+")&&reference!=null&&!reference.isBlank()&&reference.length()<=128,"Identificador de recompensa inválido");
            require(minimum>=1&&maximum>=minimum&&maximum<=1_000_000_000&&weight>=1&&weight<=100000&&rarity>=0&&rarity<=10&&maximumRank>=0&&maximumRank<=100,"Quantidade/raridade inválida: "+id);
            require(probability(chance)&&probability(minimumContribution)&&finite(baseMultiplier,0,100)&&finite(damageMultiplier,0,100),"Probabilidade/multiplicador inválido: "+id);
            require(!cosmetic()||(minimum==1&&maximum==1),"Cosmético deve ter quantidade 1");
            if(type.equals("currency")) require(Set.of("impactor:dollars","impactor:tokens").contains(reference),"Moeda não permitida");
        }
    }
    public static final class Pools { public Map<String,List<Reward>> pools=new TreeMap<>(); }
    public static final class Difficulty {
        public String general="normal",special="normal";
        public double quantityMultiplier=1;
        public Difficulty() {}
        Difficulty(String id,double multiplier){general=id;special=id;quantityMultiplier=multiplier;}
    }
    public static final class Difficulties {public Map<String,Difficulty> values=new TreeMap<>();}
    public static final class Top3 {public Map<String,List<Double>> chances=new TreeMap<>();}
    public static final class Override {
        public String difficulty, general, special;
        public double quantityMultiplier=1;
    }
    public static final class Bosses {public Map<String,Override> values=new TreeMap<>();}
    public static final class Policy {
        public Duplicate policy=Duplicate.COMPENSATE;
        public int maximumAttempts=8;
        public Reward compensation;
    }
    public static final class Duplicates {
        public Policy titles=defaultPolicy(),particles=defaultPolicy();
        private static Policy defaultPolicy(){var p=new Policy();p.compensation=new Reward("duplicate_compensation","currency","impactor:tokens",2,2);return p;}
    }
    public static final class Integrations {
        public boolean enabled=true, legacyItems=true, impactor=true, crates=true;
        public double minimumContribution=.01;
        public int deliveriesPerTick=4, maximumRewardsPerPlayer=32, maximumPending=100_000;
        public long maximumAmount=1_000_000;
    }
    public Pools general=new Pools(),special=new Pools();
    public Difficulties difficulties=new Difficulties();
    public Top3 top3=new Top3();
    public Bosses bosses=new Bosses();
    public Duplicates duplicates=new Duplicates();
    public Integrations integrations=new Integrations();
    static RaidRewardSettings defaults() {
        var c=new RaidRewardSettings();
        String[] ids={"normal","raro","epico","lendario","supremo"};
        String[] crates={"shiny1","shiny1","mega","lendaria","shiny2"};
        for(int i=0;i<ids.length;i++) {
            String id=ids[i];
            c.difficulties.values.put(id,new Difficulty(id,1+i*.5));
            c.top3.chances.put(id,List.of(.30+i*.03,.18+i*.02,.08+i*.01));
            var dollars=new Reward(id+"_dollars","currency","impactor:dollars",200,400);
            var tokens=new Reward(id+"_tokens","currency","impactor:tokens",1,3);
            var fragments=new Reward(id+"_fragment","fragment",crates[i],1,2);fragments.chance=.35;
            c.general.pools.put(id,List.of(dollars,tokens,fragments));
            var specialDollars=new Reward(id+"_bonus_dollars","currency","impactor:dollars",1000,2000);
            var specialTokens=new Reward(id+"_bonus_tokens","currency","impactor:tokens",5,10);
            var specialFragment=new Reward(id+"_bonus_fragment","fragment",crates[i],2,4);
            c.special.pools.put(id,List.of(specialDollars,specialTokens,specialFragment));
        }
        return c;
    }
    static RaidRewardSettings load(Path directory) throws IOException {
        var d=defaults();var c=new RaidRewardSettings();
        c.general=load(directory,"general.json",d.general,Pools.class);
        c.special=load(directory,"special.json",d.special,Pools.class);
        c.difficulties=load(directory,"difficulties.json",d.difficulties,Difficulties.class);
        c.top3=load(directory,"top3.json",d.top3,Top3.class);
        c.bosses=load(directory,"bosses.json",d.bosses,Bosses.class);
        c.duplicates=load(directory,"duplicates.json",d.duplicates,Duplicates.class);
        c.integrations=load(directory,"integrations.json",d.integrations,Integrations.class);
        c.validate();return c;
    }
    private static <T>T load(Path dir,String file,T defaults,Class<T> type)throws IOException {JsonFiles.defaultFile(dir.resolve(file),defaults);return JsonFiles.read(dir.resolve(file),type);}
    void validate() {
        require(general!=null&&special!=null&&difficulties!=null&&top3!=null&&bosses!=null&&duplicates!=null&&integrations!=null,"Configuração modular ausente");
        var ids=new HashSet<String>();validatePools(general,ids);validatePools(special,ids);
        require(difficulties.values!=null&&difficulties.values.keySet().equals(DIFFICULTIES)&&top3.chances!=null&&top3.chances.keySet().equals(DIFFICULTIES),"Configure as cinco dificuldades");
        for(var e:difficulties.values.entrySet()) {
            var v=e.getValue();require(v!=null&&general.pools.containsKey(v.general)&&special.pools.containsKey(v.special)&&finite(v.quantityMultiplier,.01,100),"Dificuldade inválida");
            var chances=top3.chances.get(e.getKey());
            require(chances!=null&&chances.size()==3&&chances.stream().allMatch(p->p!=null&&probability(p))&&chances.get(0)>=chances.get(1)&&chances.get(1)>=chances.get(2)&&(chances.get(0)==0||chances.get(0)>chances.get(1))&&(chances.get(1)==0||chances.get(1)>chances.get(2)),"Chances TOP 3 inválidas");
        }
        require(bosses.values!=null&&bosses.values.size()<=1000,"Overrides inválidos");
        for(var e:bosses.values.entrySet()) {var v=e.getValue();require(e.getKey().matches("[a-z0-9_-]+")&&v!=null&&(v.difficulty==null||DIFFICULTIES.contains(v.difficulty))&&(v.general==null||general.pools.containsKey(v.general))&&(v.special==null||special.pools.containsKey(v.special))&&finite(v.quantityMultiplier,.01,100),"Override inválido");}
        for(var p:new Policy[]{duplicates.titles,duplicates.particles}) {require(p!=null&&p.policy!=null&&p.maximumAttempts>=1&&p.maximumAttempts<=64,"Política de duplicata inválida");if(p.compensation!=null){p.compensation.validate();require(Set.of("currency","fragment").contains(p.compensation.type),"Compensação deve ser moeda ou fragmento");}}
        for(var d:difficulties.values.values()) require(general.pools.get(d.general).size()+(special.pools.get(d.special).isEmpty()?0:1)<=integrations.maximumRewardsPerPlayer,"Pool excede limite por jogador");
        for(var override:bosses.values.values()) for(var d:difficulties.values.values()) require(general.pools.get(override.general==null?d.general:override.general).size()+(special.pools.get(override.special==null?d.special:override.special).isEmpty()?0:1)<=integrations.maximumRewardsPerPlayer,"Override excede limite por jogador");
        var i=integrations;
        require(probability(i.minimumContribution)&&i.deliveriesPerTick>=1&&i.deliveriesPerTick<=100&&i.maximumRewardsPerPlayer>=1&&i.maximumRewardsPerPlayer<=128&&i.maximumPending>=128&&i.maximumPending<=1_000_000&&i.maximumAmount>=1&&i.maximumAmount<=1_000_000_000,"Limites de distribuição inválidos");
    }
    private static void validatePools(Pools pools,Set<String> ids) {
        require(pools.pools!=null&&!pools.pools.isEmpty()&&pools.pools.size()<=100,"Pools inválidos");
        for(var e:pools.pools.entrySet()) {require(e.getKey().matches("[a-z0-9_-]+")&&e.getValue()!=null&&e.getValue().size()<=64,"Pool inválido");for(var r:e.getValue()){require(r!=null,"Recompensa ausente");r.validate();require(ids.add(r.id),"ID de recompensa duplicado: "+r.id);}}
    }
    static boolean probability(double n){return finite(n,0,1);}
    static boolean finite(double n,double min,double max){return Double.isFinite(n)&&n>=min&&n<=max;}
    static void require(boolean value,String error){if(!value)throw new IllegalArgumentException(error);}
    /** General currency is a deterministic interpolation; other rewards retain their rolls. */
    static BigDecimal scopedAmount(String scope,Reward reward,double contribution,double multiplier,long cap,java.util.random.RandomGenerator random) {
        if (scope.equals("general") && reward.type.equals("currency"))
            return scopedScaledAmount(scope,reward,reward.minimum,contribution,multiplier,cap);
        return amount(reward,contribution,multiplier,cap,random);
    }
    static BigDecimal scopedScaledAmount(String scope,Reward reward,long base,double contribution,double multiplier,long cap) {
        if (scope.equals("general") && reward.type.equals("currency")) {
            var amount = BigDecimal.valueOf(reward.minimum).add(
                BigDecimal.valueOf(reward.maximum-reward.minimum).multiply(BigDecimal.valueOf(Math.clamp(contribution,0,1))));
            return BigDecimal.valueOf(Math.max(1,Math.min(cap,amount.longValue())));
        }
        return scaledAmount(reward,base,contribution,multiplier,cap);
    }
    static BigDecimal amount(Reward reward,double contribution,double multiplier,long cap,java.util.random.RandomGenerator random) {
        if(reward.cosmetic())return BigDecimal.ONE;
        long base=random.nextLong(reward.minimum,reward.maximum+1);
        return scaledAmount(reward,base,contribution,multiplier,cap);
    }
    static BigDecimal scaledAmount(Reward reward,long base,double contribution,double multiplier,long cap) {
        if(reward.cosmetic())return BigDecimal.ONE;
        double n=base*(reward.baseMultiplier+contribution*reward.damageMultiplier)*multiplier;
        return BigDecimal.valueOf(Math.max(1,Math.min(reward.type.equals("fragment")?Math.min(64,cap):cap,(long)Math.floor(n))));
    }
}
