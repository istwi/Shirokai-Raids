package com.shirokai.raids.core;

import java.util.List;
import java.util.random.RandomGenerator;

public final class RaidRewardManager {
    private RaidRewardManager() {}
    public static int rolls(double contribution, double minimum) {
        if (!Double.isFinite(contribution) || contribution < minimum || contribution <= 0) return 0;
        return contribution >= .3 ? 4 : contribution >= .15 ? 3 : contribution >= .05 ? 2 : 1;
    }
    public static int select(List<Integer> weights, RandomGenerator random) {
        if (weights.isEmpty() || weights.stream().anyMatch(w -> w == null || w < 1 || w > 100000))
            throw new IllegalArgumentException("Pesos inválidos");
        int selected = random.nextInt(weights.stream().mapToInt(Integer::intValue).reduce(0, Math::addExact));
        for (int i=0;i<weights.size();i++) { selected -= weights.get(i); if(selected<0) return i; }
        throw new IllegalStateException("Sorteio inválido");
    }
}
