package com.shirokai.raids;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import java.util.Collection;

/** Server-side visuals only: no explosion damage or block changes. */
final class RaidPresentation {
    private RaidPresentation() {}

    static void glow(Entity entity, boolean capture) {
        var scoreboard = entity.getServer().getScoreboard();
        String name = capture ? "sr_capture_glow" : "sr_boss_glow";
        var team = scoreboard.getPlayerTeam(name);
        if (team == null) team = scoreboard.addPlayerTeam(name);
        team.setColor(capture ? ChatFormatting.AQUA : ChatFormatting.RED);
        scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);
        entity.setGlowingTag(true);
    }

    static void clearGlow(Entity entity) {
        var scoreboard = entity.getServer().getScoreboard();
        var team = scoreboard.getPlayersTeam(entity.getScoreboardName());
        if (team != null && (team.getName().equals("sr_capture_glow") || team.getName().equals("sr_boss_glow"))) {
            scoreboard.removePlayerFromTeam(entity.getScoreboardName(), team);
            if (team.getPlayers().isEmpty()) scoreboard.removePlayerTeam(team);
        }
    }

    static void victory(Entity boss, String name, Collection<ServerPlayer> participants) {
        var world = (ServerLevel) boss.level();
        double x = boss.getX(), y = boss.getY() + boss.getBbHeight() / 2, z = boss.getZ();
        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.FIREWORK, x, y, z, 90, 1.5, 1, 1.5, 0.2);
        world.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 1.5, 1, 1.5, 0.12);
        world.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 1.1f);
        for (var player : participants) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(RaidMessages.component(RaidMessages.msg("vitoria.subtitulo", "boss", name))));
            player.connection.send(new ClientboundSetTitleTextPacket(RaidMessages.component(RaidMessages.msg("vitoria.titulo", "boss", name))));
        }
    }
}
