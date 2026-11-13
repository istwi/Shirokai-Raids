package com.shirokai.raids;

import com.shirokai.raids.core.RaidDamageTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidModularRewardsTest {
    @TempDir Path dir;
    private static final UUID PLAYER=new UUID(0,1);
    private final String raid=UUID.randomUUID().toString();
    private static final class Always extends Random { @Override public double nextDouble(){return 0;} }
    private static final class Provider implements RaidRewardHandler {
        Set<String> owned=new HashSet<>(),receipts=new HashSet<>();
        Map<String,BigDecimal> paid=new HashMap<>();
        int calls,queries;boolean available=true,safe=false;
        CompletableFuture<Result> suspended;
        public boolean available(String reference){return available;}
        public boolean retrySafe(){return safe;}
        public CompletionStage<Boolean> owns(UUID player,String reference){queries++;return CompletableFuture.completedFuture(owned.contains(reference));}
        public CompletionStage<Result> deliver(UUID p,String id,String ref,BigDecimal amount){
            calls++;if(suspended!=null)return suspended;
            if(receipts.add(id)){paid.merge(ref,amount,BigDecimal::add);owned.add(ref);}
            return CompletableFuture.completedFuture(Result.APPLIED);
        }
    }
    private static RaidRewardSettings.Reward reward(String id,String type,String ref,int amount){var r=new RaidRewardSettings.Reward(id,type,ref,amount,amount);r.baseMultiplier=1;r.damageMultiplier=0;return r;}
    private static List<RaidDamageTracker.Entry> ranking(long... damage){var tracker=new RaidDamageTracker();for(int i=0;i<damage.length;i++)tracker.record(new UUID(0,i+1),"P"+i,new UUID(1,i),damage[i],0);return tracker.ranking();}
    private static RaidRewardSettings settings(){var s=RaidRewardSettings.defaults();s.general.pools.put("normal",List.of(reward("test_general","currency","impactor:dollars",100)));s.special.pools.put("normal",List.of(reward("test_special","currency","impactor:tokens",10)));s.top3.chances.put("normal",List.of(1d,1d,1d));return s;}
    private static RaidRewardJournal engine(RaidRewardJournal.Data data){return new RaidRewardJournal(data,()->{},Runnable::run,line->{});}
    private RaidRewardJournal.Data awards(RaidRewardSettings s,long... damage){var data=new RaidRewardJournal.Data();engine(data).award(raid,"rayquaza","normal",1000,ranking(damage),s,new Always());return data;}
    @Test void onlyOriginalTopThreeGetSpecialsAndAllEligiblePlayersGetGeneralRewards(){
        var data=awards(settings(),400,300,200,100);
        assertEquals(4,data.grants.values().stream().filter(g->g.scope.equals("general")).count());
        var special=data.grants.values().stream().filter(g->g.scope.equals("special")).toList();assertEquals(3,special.size());
        assertTrue(special.stream().allMatch(g->g.rank<=3));assertTrue(special.stream().noneMatch(g->g.player.equals(new UUID(0,4).toString())));
        data.validate();
    }
    @Test void noSpecialIsGuaranteedAndMinimumContributionIsApplied(){
        var s=settings();s.top3.chances.put("normal",List.of(0d,0d,0d));s.integrations.minimumContribution=.15;
        var data=awards(s,400,300,200,100);assertEquals(3,data.grants.size());assertTrue(data.grants.values().stream().allMatch(g->g.scope.equals("general")));
    }
    @Test void tiesAndAccumulatedDamageAreDeterministicAcrossLeaveReturnAndDisconnect(){
        var tracker=new RaidDamageTracker();tracker.record(new UUID(0,2),"two",UUID.randomUUID(),200,0);tracker.record(PLAYER,"one",UUID.randomUUID(),100,0);
        // Departure/disconnect never removes accumulated contribution; returning adds to the same UUID.
        tracker.record(PLAYER,"renamed",UUID.randomUUID(),100,1);
        assertEquals(PLAYER,tracker.ranking().getFirst().player());assertEquals(200,tracker.damage(PLAYER));assertEquals(.2,tracker.contribution(PLAYER,1000));
    }
    @Test void contributionsDifficultyAndBossOverridesScaleAmountsAndRespectCaps(){
        var s=settings();var r=s.general.pools.get("normal").getFirst();r.damageMultiplier=3;
        s.integrations.maximumAmount=250;
        var data=awards(s,600,300,100);
        var amounts=data.grants.values().stream().filter(g->g.scope.equals("general")).map(g->g.choice.amount.longValueExact()).toList();
        assertEquals(List.of(250L,190L,130L),amounts);
        var o=new RaidRewardSettings.Override();o.difficulty="supremo";o.general="normal";o.special="normal";o.quantityMultiplier=2;s.bosses.values.put("rayquaza",o);
        assertEquals(250L,awards(s,100).grants.values().iterator().next().choice.amount.longValueExact());
    }
    @Test void awardIsUniqueAndResolvedValuesSurviveSerializationAndConfigChanges(){
        var s=settings();var data=awards(s,600);var before=JsonFiles.copy(data,RaidRewardJournal.Data.class);
        engine(data).award(raid,"rayquaza","normal",1000,ranking(600),s,new Random(5));assertEquals(before.grants.keySet(),data.grants.keySet());
        s.general.pools.get("normal").getFirst().maximum=9999;
        var restored=JsonFiles.copy(data,RaidRewardJournal.Data.class);restored.validate();assertEquals(BigDecimal.valueOf(100),restored.grants.values().iterator().next().choice.amount);
    }
    @Test void missingIntegrationRemainsPendingAndOfflineUuidCanReceiveCurrencyLater(){
        var data=awards(settings(),600);var e=engine(data);e.tick(4);assertTrue(data.grants.values().stream().allMatch(g->g.status==RaidRewardJournal.Status.PENDING));
        var p=new Provider();e.register("currency",p);e.tick(4);assertEquals(2,p.calls);assertEquals(BigDecimal.valueOf(100),p.paid.get("impactor:dollars"));
        e.tick(4);assertEquals(2,p.calls);
    }
    @Test void uncertainExternalPaymentNeverAutomaticallyReplaysAfterRestart(){
        var data=awards(settings(),600);var p=new Provider();p.suspended=new CompletableFuture<>();var e=engine(data);e.register("currency",p);e.tick(1);
        assertEquals(1,p.calls);
        var restored=JsonFiles.copy(data,RaidRewardJournal.Data.class);var restarted=engine(restored);restarted.register("currency",p);restarted.recover();
        var uncertain=restored.grants.values().stream().filter(g->g.status==RaidRewardJournal.Status.UNCERTAIN).findFirst().orElseThrow();
        restarted.tick(1);assertEquals(2,p.calls); // Different grant only; original remains uncertain.
        assertEquals(RaidRewardJournal.Status.UNCERTAIN,uncertain.status);
        restarted.review(uncertain.id,true,"provider receipt checked");restarted.tick(4);assertEquals(2,p.calls);
        assertThrows(IllegalArgumentException.class,()->restarted.review(uncertain.id,false,"repeat"));
    }
    @Test void retrySafeReceiptRecoversInterruptedEffectWithoutDuplicate(){
        var data=awards(settings(),600);var p=new Provider();p.safe=true;
        var first=data.grants.values().iterator().next();first.status=RaidRewardJournal.Status.IN_FLIGHT;
        p.receipts.add(first.id);p.paid.put(first.choice.reward.reference,first.choice.amount);
        var restored=JsonFiles.copy(data,RaidRewardJournal.Data.class);var e=engine(restored);e.register("currency",p);e.recover();e.tick(4);
        assertEquals(BigDecimal.valueOf(100),p.paid.get("impactor:dollars"));assertTrue(restored.grants.values().stream().allMatch(g->g.status==RaidRewardJournal.Status.DONE));
    }
    private RaidRewardJournal.Data cosmetic(RaidRewardSettings.Duplicate policy,List<RaidRewardSettings.Reward> pool){
        var s=settings();s.general.pools.put("normal",List.of());s.special.pools.put("normal",pool);s.duplicates.titles.policy=policy;
        // First candidate chosen by weight; other candidates are only considered for bounded duplicate rerolls.
        var data=new RaidRewardJournal.Data();engine(data).award(raid,"rayquaza","normal",1000,ranking(600),s,new Random(0){@Override public int nextInt(int bound){return 0;}@Override public double nextDouble(){return 0;}});return data;
    }
    @Test void skipConsultsOwnershipAndDoesNotCompensate(){
        var title=reward("title","title","raid_champion",1);var data=cosmetic(RaidRewardSettings.Duplicate.SKIP,List.of(title));var e=engine(data);var p=new Provider();p.owned.add(title.reference);e.register("title",p);e.tick(4);
        assertEquals(1,p.queries);assertEquals(0,p.calls);assertEquals(RaidRewardJournal.Status.SKIPPED,data.grants.values().iterator().next().status);
    }
    @Test void compensationIsPersistedOnceAndNeverPaidAgainAfterRestart(){
        var title=reward("title","title","raid_champion",1);var data=cosmetic(RaidRewardSettings.Duplicate.COMPENSATE,List.of(title));var p=new Provider();p.owned.add(title.reference);var e=engine(data);e.register("title",p);e.register("currency",p);e.tick(4);
        assertEquals(BigDecimal.valueOf(2),p.paid.get("impactor:tokens"));assertEquals(1,p.calls);
        var e2=engine(JsonFiles.copy(data,RaidRewardJournal.Data.class));e2.register("currency",p);e2.recover();e2.tick(4);assertEquals(1,p.calls);
    }
    @Test void boundedRerollCannotPromoteRarityOrCrossGeneralSpecialPools(){
        var title=reward("title","title","raid_champion",1);title.rarity=1;
        var higher=reward("too_rare","currency","impactor:dollars",999);higher.rarity=2;
        var replacement=reward("same_scope","currency","impactor:tokens",5);replacement.rarity=1;
        var data=cosmetic(RaidRewardSettings.Duplicate.REROLL,List.of(title,higher,replacement));var p=new Provider();p.owned.add(title.reference);var e=engine(data);e.register("title",p);e.register("currency",p);e.tick(4);
        var grant=data.grants.values().iterator().next();assertEquals("special",grant.scope);assertEquals("same_scope",grant.choice.reward.id);assertFalse(p.paid.containsKey("impactor:dollars"));
    }
    @Test void exhaustedRerollCompensatesOrSkipsAndPermanentUnlocksPersistByUuid(){
        var title=reward("title","title","raid_champion",1);var data=cosmetic(RaidRewardSettings.Duplicate.REROLL,List.of(title));var p=new Provider();p.owned.add(title.reference);var e=engine(data);e.register("title",p);e.register("currency",p);e.tick(4);assertEquals(BigDecimal.valueOf(2),p.paid.get("impactor:tokens"));
        var fresh=cosmetic(RaidRewardSettings.Duplicate.SKIP,List.of(title));var q=new Provider();var e2=engine(fresh);e2.register("title",q);e2.tick(4);
        assertTrue(JsonFiles.copy(fresh,RaidRewardJournal.Data.class).unlocks.get(PLAYER.toString()).contains("title:raid_champion"));
        fresh.grants.clear();fresh.raids.clear();var s=settings();s.general.pools.put("normal",List.of());s.special.pools.put("normal",List.of(title));s.duplicates.titles.policy=RaidRewardSettings.Duplicate.SKIP;
        e2.award(UUID.randomUUID().toString(),"rayquaza","normal",1000,ranking(600),s,new Always());e2.tick(4);assertEquals(1,q.calls);assertTrue(q.queries>=2);
    }
    @Test void futureParticlePolicyIsIndependentAndExhaustedRerollCanEndWithoutCompensation(){
        var s=settings();s.general.pools.put("normal",List.of());
        var particle=reward("particle","particle","raid_aura",1);s.special.pools.put("normal",List.of(particle));
        s.duplicates.titles.policy=RaidRewardSettings.Duplicate.COMPENSATE;s.duplicates.particles.policy=RaidRewardSettings.Duplicate.SKIP;
        var data=awards(s,600);var p=new Provider();p.owned.add(particle.reference);var e=engine(data);e.register("particle",p);e.register("currency",p);e.tick(4);
        assertEquals(0,p.calls);assertEquals(RaidRewardJournal.Status.SKIPPED,data.grants.values().iterator().next().status);
        s.duplicates.particles.policy=RaidRewardSettings.Duplicate.REROLL;s.duplicates.particles.compensation=null;s.duplicates.particles.maximumAttempts=1;
        var second=awards(s,600);var e2=engine(second);e2.register("particle",p);e2.tick(4);assertEquals(0,p.calls);assertEquals(RaidRewardJournal.Status.SKIPPED,second.grants.values().iterator().next().status);
    }
    @Test void settingsLoadSevenFilesAndRejectInvalidReloadWithoutMutatingExistingSnapshot() throws Exception {
        var settings=RaidRewardSettings.load(dir);try(var files=Files.list(dir)){assertEquals(7,files.count());}assertEquals(5,settings.difficulties.values.size());
        Files.writeString(dir.resolve("top3.json"),"{\"chances\":{\"normal\":[0.1,0.8,0.5]}}");assertThrows(IllegalArgumentException.class,()->RaidRewardSettings.load(dir));assertEquals(.30,settings.top3.chances.get("normal").getFirst());
        var bad=RaidRewardSettings.defaults();bad.integrations.deliveriesPerTick=0;assertThrows(IllegalArgumentException.class,bad::validate);
    }
    @Test void reservesCapacityBeforeAdmissionAndAllowsRepeatedPlayersWithoutAdditionalRewards() {
        var c=RaidRewardSettings.defaults().integrations;c.maximumPending=128;c.maximumRewardsPerPlayer=4;
        assertTrue(RaidRewardJournal.hasCapacity(120,2,c));
        assertFalse(RaidRewardJournal.hasCapacity(120,3,c));
        c.enabled=false;assertTrue(RaidRewardJournal.hasCapacity(128,100,c));
    }
    @Test void persistBarrierPreventsEffectWhenJournalCannotBeSaved(){
        var data=awards(settings(),600);var p=new Provider();var e=new RaidRewardJournal(data,()->{throw new IllegalStateException("disk full");},Runnable::run,line->{});e.register("currency",p);e.tick(4);assertEquals(0,p.calls);
    }
}
