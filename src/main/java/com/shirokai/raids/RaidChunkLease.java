package com.shirokai.raids;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** A temporary ticket owned only by this event; never edits vanilla /forceload state. */
final class RaidChunkLease implements AutoCloseable {
    private static final TicketType<UUID> TYPE = TicketType.create("shirokai_raids", Comparator.comparing(UUID::toString));
    private final ServerLevel world;
    private final ChunkPos center;
    private final UUID owner = UUID.randomUUID();
    private final int radius;
    private boolean closed;
    RaidChunkLease(ServerLevel world, ChunkPos center, double arenaRadius) {
        this.world = world; this.center = center;
        radius = radiusFor(arenaRadius);
        world.getChunkSource().addRegionTicket(TYPE, center, radius, owner);
    }
    static int radiusFor(double radius) {
        if (!Double.isFinite(radius) || radius < 8 || radius > 128) throw new IllegalArgumentException("Raio da arena inválido");
        return (int) Math.ceil(radius / 16) + 1;
    }
    int radius() { return radius; }
    boolean closed() { return closed; }
    @Override public void close() {
        if (closed) return;
        world.getChunkSource().removeRegionTicket(TYPE, center, radius, owner);
        closed = true;
    }
}
