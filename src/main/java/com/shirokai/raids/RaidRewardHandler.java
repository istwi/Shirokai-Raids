package com.shirokai.raids;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/** Optional providers register through the shirokai_raids:rewards Fabric entrypoint.
 * All callbacks run on the server executor. Never block it waiting for IO.
 * A retry-safe provider must persist transaction IDs atomically with its effect. */
public interface RaidRewardHandler {
    enum Result { APPLIED, RETRY, UNCERTAIN }
    boolean available(String reference);
    default boolean retrySafe() { return false; }
    default CompletionStage<Boolean> owns(UUID player, String reference) {
        return CompletableFuture.completedFuture(false);
    }
    CompletionStage<Result> deliver(UUID player, String transaction, String reference, BigDecimal amount);
    interface Provider { void register(Registry registry); }
    interface Registry { void register(String type, RaidRewardHandler handler); }
}
