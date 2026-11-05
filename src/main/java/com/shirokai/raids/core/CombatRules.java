package com.shirokai.raids.core;

public final class CombatRules {
    private CombatRules() {}
    public static boolean eligible(long now, long nextAttack, double squaredDistance, double range,
                                   boolean ownerMatches, boolean alive, boolean battling, boolean busy) {
        return ownerMatches && alive && !battling && !busy && now >= nextAttack
                && Double.isFinite(squaredDistance) && squaredDistance >= 0 && squaredDistance <= range * range;
    }
    public static long damage(int level, int attack, int defence, double power, double stab, double effectiveness) {
        if (level < 1 || attack < 1 || defence < 1 || !Double.isFinite(power) || power <= 0
                || !Double.isFinite(stab) || stab < 0 || !Double.isFinite(effectiveness) || effectiveness < 0)
            throw new IllegalArgumentException("Atributos inválidos");
        if (effectiveness == 0) return 0;
        double result = (((2.0 * level / 5 + 2) * power * attack / defence) / 50 + 2) * stab * effectiveness;
        return (long) Math.max(1, Math.min(1_000_000, result));
    }
    public static int phase(double fraction) { return fraction < .3 ? 3 : fraction < .7 ? 2 : 1; }
}
