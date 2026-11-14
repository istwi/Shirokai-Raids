package com.shirokai.raids;

import com.shirokai.raids.core.RaidDamageTracker;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/** Durable outbox embedded in the same state.json transaction as raid completion. */
final class RaidRewardJournal {
    enum Status { PENDING, IN_FLIGHT, DONE, UNCERTAIN, SKIPPED }
    static final class Choice {
        RaidRewardSettings.Reward reward;
        BigDecimal amount;
        Choice(RaidRewardSettings.Reward reward,BigDecimal amount){this.reward=JsonFiles.copy(reward,RaidRewardSettings.Reward.class);this.amount=amount;}
    }
    static final class Grant {
        String id,raid,player,scope,difficulty,originalReward;
        int rank;
        double contribution;
        Choice choice;
        List<Choice> alternatives=new ArrayList<>();
        RaidRewardSettings.Policy policy;
        Status status=Status.PENDING;
        boolean resolved;
        List<String> audit=new ArrayList<>();
    }
    static final class Data {
        Set<String> raids=new HashSet<>();
        Map<String,Grant> grants=new LinkedHashMap<>();
        Map<String,Set<String>> unlocks=new HashMap<>();
        void validate() {
            RaidRewardSettings.require(raids!=null&&grants!=null&&unlocks!=null,"Journal modular ausente");
            for(var e:grants.entrySet()) {
                var g=e.getValue();RaidRewardSettings.require(g!=null&&e.getKey().equals(g.id)&&g.status!=null&&g.choice!=null&&g.scope!=null&&Set.of("general","special").contains(g.scope)&&g.rank>0&&g.rank<=1_000_000&&RaidRewardSettings.probability(g.contribution)&&g.alternatives!=null&&g.alternatives.size()<=64&&g.audit!=null&&g.policy!=null&&g.policy.policy!=null&&g.policy.maximumAttempts>=1&&g.policy.maximumAttempts<=64&&RaidRewardSettings.DIFFICULTIES.contains(g.difficulty)&&g.originalReward!=null,"Entrega modular inválida");
                UUID.fromString(g.id);UUID.fromString(g.player);UUID.fromString(g.raid);
                validateChoice(g.choice);for(var c:g.alternatives)validateChoice(c);
            }
            for(var e:unlocks.entrySet()){UUID.fromString(e.getKey());RaidRewardSettings.require(e.getValue()!=null,"Desbloqueios ausentes");}
        }
        private static void validateChoice(Choice c){RaidRewardSettings.require(c!=null&&c.reward!=null&&c.amount!=null&&c.amount.signum()>0&&c.amount.compareTo(BigDecimal.valueOf(1_000_000_000L))<=0,"Valor de entrega inválido");c.reward.validate();}
    }
    private final Data data;
    private final Runnable persist;
    private final Executor executor;
    private final Consumer<String> audit;
    private final Map<String,RaidRewardHandler> handlers=new HashMap<>();
    private final Set<String> busy=new HashSet<>();
    private boolean failed;
    private int cursor;
    private Consumer<Grant> delivered = grant -> {};
    void onDelivered(Consumer<Grant> listener){delivered=listener;}
    RaidRewardJournal(Data data,Runnable persist,Executor executor,Consumer<String> audit){this.data=data;this.persist=persist;this.executor=executor;this.audit=audit;}
    void register(String type,RaidRewardHandler handler){if(type==null||handler==null||handlers.putIfAbsent(type,handler)!=null)throw new IllegalArgumentException("Handler duplicado: "+type);}
    Map<String,RaidRewardHandler> handlers(){return Map.copyOf(handlers);}
    void recover() {
        for(var g:data.grants.values()) if(g.status==Status.IN_FLIGHT) {
            var handler=handlers.get(g.choice.reward.type);
            g.status=handler!=null&&handler.retrySafe()?Status.PENDING:Status.UNCERTAIN;
            log(g,"Recuperação: "+g.status+"; efeito externo sem recibo não é repetido");
        }
        save();
    }
    static boolean hasCapacity(long pending,int participants,RaidRewardSettings.Integrations limits) {
        return !limits.enabled || pending+(long)participants*limits.maximumRewardsPerPlayer<=limits.maximumPending;
    }
    static boolean eligible(RaidRewardSettings.Reward reward,double contribution,int rank){return contribution>=reward.minimumContribution&&(reward.maximumRank==0||rank<=reward.maximumRank);}
    void award(String raid,String boss,String difficulty,long maxHp,List<RaidDamageTracker.Entry> ranking,RaidRewardSettings settings,RandomGenerator random) {
        if(!settings.integrations.enabled||data.raids.contains(raid))return;
        var override=settings.bosses.values.get(boss);
        String category=override!=null&&override.difficulty!=null?override.difficulty:difficulty;
        var d=settings.difficulties.values.get(category);
        if(d==null)throw new IllegalArgumentException("Dificuldade desconhecida: "+category);
        var general=settings.general.pools.get(override!=null&&override.general!=null?override.general:d.general);
        var special=settings.special.pools.get(override!=null&&override.special!=null?override.special:d.special);
        double multiplier=d.quantityMultiplier*(override==null?1:override.quantityMultiplier);
        var generated=new ArrayList<Grant>();
        int rank=0;
        // Defensive sorting: callers cannot change deterministic tie-breaking or promote an ineligible TOP 4.
        var ordered=ranking.stream().sorted(Comparator.comparingLong(RaidDamageTracker.Entry::damage).reversed().thenComparing(e->e.player().toString())).toList();
        for(var entry:ordered) {
            rank++;double contribution=maxHp>0?Math.min(1,(double)entry.damage()/maxHp):0;
            if(contribution<=0||contribution<settings.integrations.minimumContribution)continue;
            int perPlayer=0;
            for(var reward:general) if(eligible(reward,contribution,rank)&&random.nextDouble()<reward.chance) {
                if(++perPlayer>settings.integrations.maximumRewardsPerPlayer)throw new IllegalArgumentException("Prêmios por jogador excedem limite");
                generated.add(grant(raid,entry.player(),"general",category,rank,contribution,reward,general,multiplier,settings,random));
            }
            if(rank<=3 && random.nextDouble()<settings.top3.chances.get(category).get(rank-1)) {
                final int place=rank;final double share=contribution;
                var candidates=special.stream().filter(r->eligible(r,share,place)).toList();
                if(!candidates.isEmpty()) {
                    var reward=select(candidates,random);
                    if(random.nextDouble()<reward.chance) {
                        if(++perPlayer>settings.integrations.maximumRewardsPerPlayer)throw new IllegalArgumentException("Prêmios por jogador excedem limite");
                        generated.add(grant(raid,entry.player(),"special",category,rank,contribution,reward,special,multiplier,settings,random));
                    }
                }
            }
        }
        long outstanding=data.grants.values().stream().filter(g->g.status!=Status.DONE&&g.status!=Status.SKIPPED).count();
        if(outstanding+generated.size()>settings.integrations.maximumPending)throw new IllegalStateException("Limite de recompensas pendentes atingido");
        for(var g:generated){data.grants.put(g.id,g);log(g,"Sorteio: "+g.choice.reward.id+" x"+g.choice.amount+" posição="+g.rank+" contribuição="+g.contribution);}
        data.raids.add(raid); // caller persists this with completed raid and legacy loot, before delivery
    }
    private static Grant grant(String raid,UUID player,String scope,String difficulty,int rank,double contribution,RaidRewardSettings.Reward r,List<RaidRewardSettings.Reward> pool,double multiplier,RaidRewardSettings settings,RandomGenerator random){
        var g=new Grant();g.raid=raid;g.player=player.toString();g.scope=scope;g.difficulty=difficulty;g.rank=rank;g.contribution=contribution;g.originalReward=r.id;
        g.id=UUID.nameUUIDFromBytes((raid+":"+player+":"+scope+":"+r.id).getBytes(StandardCharsets.UTF_8)).toString();
        g.choice=new Choice(r,RaidRewardSettings.scopedAmount(scope,r,contribution,multiplier,settings.integrations.maximumAmount,random));
        var policy=r.type.equals("title")?settings.duplicates.titles:settings.duplicates.particles;
        g.policy=JsonFiles.copy(policy,RaidRewardSettings.Policy.class);
        if(g.policy.compensation!=null){
            long cap=g.policy.compensation.type.equals("fragment")?Math.min(64,settings.integrations.maximumAmount):settings.integrations.maximumAmount;
            g.policy.compensation.maximum=Math.min(cap,g.policy.compensation.maximum);
            g.policy.compensation.minimum=Math.min(cap,g.policy.compensation.minimum);
        }
        for(var alternative:pool)if(alternative.rarity<=r.rarity&&eligible(alternative,contribution,rank))g.alternatives.add(new Choice(alternative,RaidRewardSettings.scopedAmount(scope,alternative,contribution,multiplier,settings.integrations.maximumAmount,random)));
        g.resolved=!r.cosmetic();return g;
    }
    private static RaidRewardSettings.Reward select(List<RaidRewardSettings.Reward> pool,RandomGenerator random){int n=random.nextInt(pool.stream().mapToInt(r->r.weight).sum());for(var r:pool){n-=r.weight;if(n<0)return r;}throw new AssertionError();}
    void tick(int budget) {
        if(failed)return;
        int started=0;
        var snapshot=List.copyOf(data.grants.values());
        if(snapshot.isEmpty())return;
        int start=Math.floorMod(cursor,snapshot.size());
        for(int offset=0;offset<snapshot.size() && started<budget;offset++) {
            int index=(start+offset)%snapshot.size();
            var g=snapshot.get(index);
            cursor=(index+1)%snapshot.size();
            if(g.status!=Status.PENDING||busy.contains(g.id))continue;
            var handler=handlers.get(g.choice.reward.type);
            if(handler==null||!handler.available(g.choice.reward.reference))continue;
            if(busy.stream().anyMatch(id->data.grants.get(id).player.equals(g.player)))continue;
            busy.add(g.id);started++;
            resolve(g,new Random(g.id.hashCode())).whenCompleteAsync((choice,error)->{
                if(error!=null){busy.remove(g.id);audit.accept("Consulta de recompensa adiada: "+g.id+" "+error);return;}
                try {
                    if(choice==null){g.status=Status.SKIPPED;log(g,"Duplicata sem alternativa: ignorada");save();busy.remove(g.id);return;}
                    if(!g.resolved){g.choice=choice;g.resolved=true;log(g,"Resolução de duplicata: "+choice.reward.id);save();}
                    deliver(g);
                }catch(Throwable failure){failed=true;busy.remove(g.id);audit.accept("Journal suspenso: "+failure);}
            },executor);
        }
    }
    private CompletionStage<Choice> resolve(Grant g,Random random) {
        if(g.resolved||!g.choice.reward.cosmetic())return CompletableFuture.completedFuture(g.choice);
        return owned(g,g.choice).thenComposeAsync(owned->{
            if(!owned)return CompletableFuture.completedFuture(g.choice);
            log(g,"Cosmético já possuído: "+g.choice.reward.reference);
            if(g.policy.policy==RaidRewardSettings.Duplicate.SKIP)return CompletableFuture.completedFuture(null);
            if(g.policy.policy==RaidRewardSettings.Duplicate.COMPENSATE)return CompletableFuture.completedFuture(compensation(g,random));
            return reroll(g,random,0,new HashSet<>());
        },executor);
    }
    private CompletionStage<Boolean> owned(Grant g,Choice c) {
        var handler=handlers.get(c.reward.type);
        if(handler==null||!handler.available(c.reward.reference))return CompletableFuture.failedFuture(new IllegalStateException("Provedor cosmético indisponível"));
        return handler.owns(UUID.fromString(g.player),c.reward.reference).thenApplyAsync(value->value||data.unlocks.getOrDefault(g.player,Set.of()).contains(c.reward.type+":"+c.reward.reference),executor);
    }
    private CompletionStage<Choice> reroll(Grant g,Random random,int attempt,Set<String> excluded) {
        var available=g.alternatives.stream().filter(c->!excluded.contains(c.reward.id)&&!c.reward.id.equals(g.originalReward)).filter(c->{var h=handlers.get(c.reward.type);return h!=null&&h.available(c.reward.reference);}).toList();
        if(attempt>=g.policy.maximumAttempts||available.isEmpty())return CompletableFuture.completedFuture(compensation(g,random));
        var selected=select(available.stream().map(c->c.reward).toList(),random);
        var choice=available.stream().filter(c->c.reward.id.equals(selected.id)).findFirst().orElseThrow();excluded.add(selected.id);
        if(random.nextDouble()>=choice.reward.chance)return reroll(g,random,attempt+1,excluded);
        if(!choice.reward.cosmetic())return CompletableFuture.completedFuture(choice);
        return owned(g,choice).thenComposeAsync(owned->owned?reroll(g,random,attempt+1,excluded):CompletableFuture.completedFuture(choice),executor);
    }
    private Choice compensation(Grant g,Random random){
        var r=g.policy.compensation;
        // Fixed compensation is independent of contribution and cannot promote scope/rarity.
        return r==null?null:new Choice(r,BigDecimal.valueOf(random.nextLong(r.minimum,r.maximum+1)));
    }
    private void deliver(Grant g) {
        var h=handlers.get(g.choice.reward.type);
        if(h==null||!h.available(g.choice.reward.reference)){busy.remove(g.id);return;}
        g.status=Status.IN_FLIGHT;save(); // Durable barrier before any external effect.
        CompletionStage<RaidRewardHandler.Result> future;
        try{future=h.deliver(UUID.fromString(g.player),g.id,g.choice.reward.reference,g.choice.amount);}catch(Throwable error){future=CompletableFuture.failedFuture(error);}
        future.whenCompleteAsync((result,error)->{
            try{
                g.status=error!=null||result==null||result==RaidRewardHandler.Result.UNCERTAIN?Status.UNCERTAIN:result==RaidRewardHandler.Result.APPLIED?Status.DONE:Status.PENDING;
                if(g.status==Status.DONE&&g.choice.reward.cosmetic())data.unlocks.computeIfAbsent(g.player,k->new HashSet<>()).add(g.choice.reward.type+":"+g.choice.reward.reference);
                log(g,"Entrega: "+g.status+(error==null?"":" "+error));save();
                if(g.status==Status.DONE)try{delivered.accept(g);}catch(RuntimeException notification){audit.accept("Aviso de entrega não enviado: "+g.id);}
            }catch(Throwable failure){failed=true;audit.accept("Journal suspenso: "+failure);}finally{busy.remove(g.id);}
        },executor);
    }
    void review(String id,boolean paid,String reason){
        var g=data.grants.get(id);
        if(g==null||g.status!=Status.UNCERTAIN||reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Entrega não incerta ou motivo inválido");
        g.status=paid?Status.DONE:Status.PENDING;
        if(paid&&g.choice.reward.cosmetic())data.unlocks.computeIfAbsent(g.player,k->new HashSet<>()).add(g.choice.reward.type+":"+g.choice.reward.reference);
        log(g,"Conferência administrativa: "+g.status+" motivo="+reason);save();
    }
    private void log(Grant g,String event){String line="[rewards] transaction="+g.id+" raid="+g.raid+" player="+g.player+" scope="+g.scope+" "+event;if(g.audit.size()<100)g.audit.add(event);audit.accept(line);}
    private void save(){try{persist.run();}catch(RuntimeException error){failed=true;throw error;}}
}
