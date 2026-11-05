package com.shirokai.raids;

import java.util.List;

public final class RaidConfig {
    public long intervalSeconds = 7200;
    public int durationSeconds = 1200;
    public String dimension = "minecraft:overworld";
    public double x = 0, y = 80, z = 0;
    public boolean spawnConfigured = false;
    public double arenaRadius = 48, attackRange = 32;
    public int attackCooldownTicks = 40, maximumParticipants = 32;
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
        public String rewards = "default";
        public void validate() {
            if (id == null || !id.matches("[a-z0-9_-]+") || species == null || !species.matches("[a-z0-9_:.-]+")
                || form == null || !form.matches("[a-zA-Z0-9_-]*") || name == null || name.isBlank()
                || level < 1 || level > 100 || hp < 1 || hp > 1_000_000_000_000L || !Float.isFinite(scale) || scale < .5 || scale > 5
                || rewards == null || !rewards.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Boss inválido: " + id);
        }
    }
    public static final class Rewards {
        public double minimumContribution = .01;
        public int maximumItems = 64;
        public List<Reward> items = List.of(new Reward());
        public void validate() {
            if (!Double.isFinite(minimumContribution) || minimumContribution < 0 || minimumContribution > 1
                || maximumItems < 1 || maximumItems > 256 || items == null || items.isEmpty() || items.size() > 100)
                throw new IllegalArgumentException("Tabela de recompensas inválida");
            for (var item : items) {
                if (item.item == null || !item.item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || item.minimum < 1
                    || item.maximum < item.minimum || item.maximum > 64 || item.weight < 1 || item.weight > 100000)
                    throw new IllegalArgumentException("Item de recompensa inválido");
            }
        }
    }
    public static final class Reward {
        public String item = "minecraft:diamond";
        public int minimum = 1, maximum = 3, weight = 1;
    }
}
