package com.shirokai.raids;

import net.fabricmc.loader.api.FabricLoader;
import net.impactdev.impactor.api.economy.EconomyService;
import net.kyori.adventure.key.Key;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Impactor 5.3.5 public API. No commands, parallel balances or assumed transaction idempotency. */
final class ImpactorRaidRewards implements RaidRewardHandler {
    private final BooleanSupplier enabled;
    ImpactorRaidRewards(BooleanSupplier enabled){this.enabled=enabled;}
    public boolean available(String reference) {
        if(!enabled.getAsBoolean()||!FabricLoader.getInstance().isModLoaded("impactor"))return false;
        try {return EconomyService.instance().currencies().currency(Key.key(reference)).isPresent();}
        catch(RuntimeException|LinkageError unavailable){return false;}
    }
    public CompletionStage<Result> deliver(UUID player,String transaction,String reference,BigDecimal amount) {
        if(!available(reference))return CompletableFuture.completedFuture(Result.RETRY);
        var economy=EconomyService.instance();
        var currency=economy.currencies().currency(Key.key(reference)).orElseThrow();
        return economy.account(currency,player).thenCompose(account->account.depositAsync(amount)
            .thenCompose(report->report.successful()?economy.save(account).thenApply(ignored->Result.APPLIED):CompletableFuture.completedFuture(Result.UNCERTAIN)));
    }
}
