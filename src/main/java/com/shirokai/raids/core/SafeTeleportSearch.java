package com.shirokai.raids.core;

import java.util.Optional;
import java.util.function.Predicate;

/** Bounded search; the server supplies authoritative collision and world checks. */
public final class SafeTeleportSearch {
    private SafeTeleportSearch() {}
    public record Spot(int x, int feetY, int z) {}
    public static Optional<Spot> find(double centerX, double centerY, double centerZ,
                                     double radius, int offset, Predicate<Spot> safe) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)
            || !Double.isFinite(radius) || radius < 8 || radius > 128) return Optional.empty();
        int baseY = (int) Math.floor(centerY);
        for (int ring = 6; ring <= Math.min(22, radius - 2); ring += 4) {
            for (int direction=0;direction<8;direction++) {
                double angle = (Math.floorMod(direction + offset, 8)) * Math.PI / 4;
                int x=(int)Math.floor(centerX+Math.cos(angle)*ring);
                int z=(int)Math.floor(centerZ+Math.sin(angle)*ring);
                if (Math.hypot(x+.5-centerX,z+.5-centerZ) > radius - 1) continue;
                // Try the arena's height first, then both sides. Never scan the entire world.
                for(int vertical=0;vertical<=12;vertical++) {
                    int dy=vertical==0?0:(vertical%2==1?(vertical+1)/2:-(vertical/2));
                    var candidate=new Spot(x,baseY+dy,z);
                    if(safe.test(candidate)) return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
}
