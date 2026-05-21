package com.shamoji.mapaccel.security;

import com.mojang.authlib.GameProfile;
import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrustLedger {
    private final Map<UUID, Integer> trust = new HashMap<>();
    private final Map<UUID, Integer> mismatches = new HashMap<>();

    public int trustOf(UUID playerId) {
        return trust.getOrDefault(playerId, 100);
    }

    public void reward(UUID playerId) {
        trust.put(playerId, Math.min(100, trustOf(playerId) + 1));
    }

    public void penalize(MinecraftServer server, ServerPlayer player, String reason) {
        UUID playerId = player.getUUID();
        int next = trustOf(playerId) - MapAccelConfig.TRUST_PENALTY.get();
        trust.put(playerId, next);
        mismatches.merge(playerId, 1, Integer::sum);
        if (MapAccelConfig.AUTO_BAN.get() && next <= MapAccelConfig.BAN_THRESHOLD.get()) {
            ban(server, player, reason);
        }
    }

    public int mismatchCount(UUID playerId) {
        return mismatches.getOrDefault(playerId, 0);
    }

    private void ban(MinecraftServer server, ServerPlayer player, String reason) {
        GameProfile profile = player.getGameProfile();
        server.getPlayerList().getBans().add(new UserBanListEntry(profile, null, "MapAccel", null, reason));
        player.connection.disconnect(Component.literal("MapAccel validation failed: " + reason));
    }
}
