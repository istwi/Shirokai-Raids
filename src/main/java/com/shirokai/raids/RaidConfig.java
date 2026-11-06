package com.shirokai.raids;

import java.util.*;

public final class RaidConfig {
    public long intervalSeconds = 7200;
    public int durationSeconds = 1200;
    public String dimension = "minecraft:overworld";
    public double x = 0, y = 80, z = 0;
    public boolean spawnConfigured = false;
    public double arenaRadius = 48, attackRange = 32;
    public int attackCooldownTicks = 40, maximumParticipants = 32;
    public int maxConcurrentPlayers = 100;
    public int bossAttackSeconds = 5, bossDamage = 10;
    public String restartPolicy = "SKIP_MISSED";
    public List<Integer> warningsSeconds = List.of(900,300,60);
    public List<String> supportedMoves = List.of("tackle", "scratch", "pound", "quickattack", "dragonclaw", "dragonpulse", "airslash", "flamethrower", "watergun", "thunderbolt", "icebeam", "psychic", "shadowball", "energyball", "earthpower", "rockslide", "ironhead", "moonblast", "aerialace");
    public void validate() {
        com.shirokai.raids.core.RaidSchedule.next(0, intervalSeconds);
        if (durationSeconds < 30 || durationSeconds > 7200 || !Double.isFinite(x+y+z)
            || !Double.isFinite(arenaRadius) || arenaRadius < 8 || arenaRadius > 128
            || !Double.isFinite(attackRange) || attackRange < 1 || attackRange > arenaRadius
            || attackCooldownTicks < 10 || attackCooldownTicks > 1200 || maximumParticipants < 1 || maximumParticipants > 100
            || maxConcurrentPlayers < 1 || maxConcurrentPlayers > 100
            || bossAttackSeconds < 1 || bossAttackSeconds > 120 || bossDamage < 1 || bossDamage > 1000
            || !List.of("SKIP_MISSED", "START_ONCE").contains(restartPolicy) || supportedMoves == null || supportedMoves.isEmpty()
            || warningsSeconds == null || warningsSeconds.stream().anyMatch(s -> s == null || s < 1 || s > intervalSeconds))
            throw new IllegalArgumentException("config.json: valores fora dos limites de segurança");
    }
    public static final class Boss {
        public String id = "rayquaza", species = "rayquaza", form = "", name = "Rayquaza — World Boss";
        public int level = 100;
        public long hp = 10_000_000;
        public boolean shiny = false;
        public float scale = 2;
        public double damageMultiplier = 1.0;
        public String rewards = "default";
        public void validate() {
            if (id == null || !id.matches("[a-z0-9_-]+") || species == null || !species.matches("[a-z0-9_:.-]+")
                || form == null || !form.matches("[a-zA-Z0-9_-]*") || name == null || name.isBlank()
                || level < 1 || level > 100 || hp < 1 || hp > 1_000_000_000_000L || !Float.isFinite(scale) || scale < .5 || scale > 5
                || !Double.isFinite(damageMultiplier) || damageMultiplier < 0 || damageMultiplier > 100
                || rewards == null || !rewards.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Boss inválido: " + id);
        }
    }
    public static final class Rewards {
        public double minimumContribution = .01;
        public int maximumItems = 64;
        public List<Reward> items = List.of(new Reward());
        public int rollsPerPlayer = 1;
        public List<RewardTier> tiers;
        public Map<String,List<Reward>> itemPools;
        public static Rewards defaults() {
            var table=new Rewards();
            table.items=List.of();
            table.tiers=List.of(
                new RewardTier("bronze",.01,70,25,4,1),
                new RewardTier("silver",.10,55,33,10,2),
                new RewardTier("gold",.25,40,40,16,4),
                new RewardTier("diamond",.50,20,45,25,10));
            table.itemPools=Map.of(
                "common",List.of(new Reward("minecraft:iron_ingot",2,8,100)),
                "rare",List.of(new Reward("minecraft:diamond",1,3,100)),
                "epic",List.of(new Reward("minecraft:netherite_scrap",1,2,100)),
                "legendary",List.of(new Reward("minecraft:netherite_ingot",1,1,100)));
            return table;
        }
        public List<Reward> configuredItems() {
            return tiers==null ? items : itemPools.values().stream().flatMap(List::stream).toList();
        }
        public void validate() {
            if (!Double.isFinite(minimumContribution) || minimumContribution < 0 || minimumContribution > 1
                || maximumItems < 1 || maximumItems > 256 || rollsPerPlayer<1 || rollsPerPlayer>32)
                throw new IllegalArgumentException("Tabela de recompensas inválida");
            if(tiers==null) {
                if(itemPools!=null) throw new IllegalArgumentException("itemPools requer tiers");
                validateItems(items); return;
            }
            if(tiers.isEmpty() || tiers.size()>16 || itemPools==null || itemPools.isEmpty() || itemPools.size()>16)
                throw new IllegalArgumentException("Faixas/pools inválidos");
            for(var pool:itemPools.entrySet()) {
                if(pool.getKey()==null || !pool.getKey().matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Raridade inválida");
                validateItems(pool.getValue());
            }
            double previous=-1;
            var ids=new HashSet<String>();
            for(var tier:tiers) {
                if(tier==null || tier.id==null || !tier.id.matches("[a-z0-9_-]+") || !ids.add(tier.id)
                    || !Double.isFinite(tier.minimumContribution) || tier.minimumContribution<0 || tier.minimumContribution>1
                    || tier.minimumContribution<=previous || tier.rarityChances==null
                    || !tier.rarityChances.keySet().equals(itemPools.keySet())
                    || tier.rarityChances.values().stream().anyMatch(w -> w==null || w<0 || w>100)
                    || tier.rarityChances.values().stream().mapToInt(Integer::intValue).sum()!=100)
                    throw new IllegalArgumentException("Faixa ou probabilidades inválidas");
                previous=tier.minimumContribution;
            }
            if(tiers.getFirst().minimumContribution>minimumContribution)
                throw new IllegalArgumentException("Primeira faixa não cobre a contribuição mínima");
        }
        private static void validateItems(List<Reward> entries) {
            if(entries==null || entries.isEmpty() || entries.size()>100) throw new IllegalArgumentException("Pool de itens inválido");
            for (var item : entries) {
                if (item==null || item.item == null || item.item.equals("minecraft:air") || !item.item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || item.minimum < 1
                    || item.maximum < item.minimum || item.maximum > 64 || item.weight < 1 || item.weight > 100000)
                    throw new IllegalArgumentException("Item de recompensa inválido");
            }
        }
    }
    public static final class RewardTier {
        public String id;
        public double minimumContribution;
        public Map<String,Integer> rarityChances;
        public RewardTier() {}
        public RewardTier(String id,double minimum,int common,int rare,int epic,int legendary) {
            this.id=id; minimumContribution=minimum;
            rarityChances=Map.of("common",common,"rare",rare,"epic",epic,"legendary",legendary);
        }
    }
    public static final class Reward {
        public String item = "minecraft:diamond";
        public int minimum = 1, maximum = 3, weight = 1;
        public Reward() {}
        public Reward(String item,int minimum,int maximum,int weight) {
            this.item=item; this.minimum=minimum; this.maximum=maximum; this.weight=weight;
        }
    }
}
