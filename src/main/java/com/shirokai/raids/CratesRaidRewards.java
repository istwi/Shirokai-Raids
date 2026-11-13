package com.shirokai.raids;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Optional public Tokens factory adapter audited against Shirokai Crates 0.2.1.
 * Never reads/copies the secret itself, creates one, or fabricates NBT signatures. */
final class CratesRaidRewards implements RaidRewardHandler {
    private final MinecraftServer server;
    private final BooleanSupplier enabled;
    private Object tokens;
    CratesRaidRewards(MinecraftServer server,BooleanSupplier enabled){this.server=server;this.enabled=enabled;}
    public boolean retrySafe(){return true;}
    private Object crate(String id)throws ReflectiveOperationException {
        var owner=Class.forName("dev.shirokaicrates.ShirokaiCrates");
        var service=owner.getMethod("service").invoke(null);
        if(service==null || !(boolean)service.getClass().getMethod("healthy").invoke(service))return null;
        var snapshot=service.getClass().getMethod("snapshot").invoke(service);
        return ((Map<?,?>)snapshot.getClass().getMethod("crates").invoke(snapshot)).get(id);
    }
    public boolean available(String reference) {
        if(!enabled.getAsBoolean()||!FabricLoader.getInstance().isModLoaded("shirokaicrates"))return false;
        try {return crate(reference)!=null&&Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve("shirokaicrates/token-secret.bin"));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return false;}
    }
    ItemStack template(String reference)throws ReflectiveOperationException {
        var crate=crate(reference);if(crate==null)throw new IllegalStateException("Crate indisponível: "+reference);
        var secret=server.getWorldPath(LevelResource.ROOT).resolve("shirokaicrates/token-secret.bin");
        if(!Files.isRegularFile(secret)||Files.isSymbolicLink(secret))throw new IllegalStateException("Secret original de crates indisponível");
        var factory=Class.forName("dev.shirokaicrates.service.Tokens");
        if(tokens==null)tokens=factory.getConstructor(java.nio.file.Path.class).newInstance(secret);
        var model=Class.forName("dev.shirokaicrates.config.Models$Crate");
        var stack=(ItemStack)factory.getMethod("create",model,boolean.class,int.class).invoke(tokens,crate,true,1);
        if(!(boolean)factory.getMethod("matches",ItemStack.class,model,boolean.class).invoke(tokens,stack,crate,true))throw new IllegalStateException("Fábrica de crates recusou seu próprio fragmento");
        return stack;
    }
    public CompletionStage<Result> deliver(UUID id,String transaction,String reference,BigDecimal amount) {
        var player=server.getPlayerList().getPlayer(id);
        if(player==null||RaidInventory.locked(player)||!available(reference))return CompletableFuture.completedFuture(Result.RETRY);
        try {
            var target=new PlayerRewardTarget(server,player,"shirokai_modular:");
            if(!target.received(transaction)) {
                int count=amount.intValueExact();
                var transfer=target.plan(template(reference),count);
                if(transfer==null||transfer.count()!=count)return CompletableFuture.completedFuture(Result.RETRY);
                transfer.apply(transaction);
            }
            target.saveAndVerify(transaction);
            return CompletableFuture.completedFuture(Result.APPLIED);
        }catch(Exception error){return CompletableFuture.failedFuture(error);}
    }
}
