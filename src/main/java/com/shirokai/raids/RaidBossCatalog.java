package com.shirokai.raids;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.random.RandomGenerator;

final class RaidBossCatalog {
    private RaidBossCatalog() {}
    static Map<String, RaidConfig.Boss> load(Path directory) throws IOException {
        var bosses = new TreeMap<String, RaidConfig.Boss>();
        try (var paths = Files.list(directory)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                var boss = JsonFiles.read(path, RaidConfig.Boss.class);
                boss.name = boss.name == null ? null : boss.name.replace(" — World Boss", " — Raid");
                boss.validate();
                if (bosses.putIfAbsent(boss.id, boss) != null) throw new IOException("ID de boss duplicado: " + boss.id);
            }
        }
        if (bosses.isEmpty()) throw new IOException("Cadastre pelo menos um boss");
        return Collections.unmodifiableMap(bosses);
    }
    static String randomId(Map<String, RaidConfig.Boss> bosses, RandomGenerator random) {
        if (bosses.isEmpty()) throw new IllegalArgumentException("Lista de bosses vazia");
        var ids = new ArrayList<>(bosses.keySet());
        return ids.get(random.nextInt(ids.size()));
    }
}
