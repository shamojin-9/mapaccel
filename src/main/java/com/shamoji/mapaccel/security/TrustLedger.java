package com.shamoji.mapaccel.security;

import com.mojang.authlib.GameProfile;
import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrustLedger {
    private final Map<UUID, Integer> trust = new HashMap<>();
    private final Map<UUID, Integer> mismatches = new HashMap<>();
    private TrustSavedData savedData;

    public synchronized void attach(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        TrustSavedData data = overworld.getDataStorage().computeIfAbsent(
                TrustSavedData::load,
                TrustSavedData::new,
                TrustSavedData.ID
        );
        if (data == savedData) {
            return;
        }
        savedData = data;
        trust.clear();
        trust.putAll(data.trust());
        mismatches.clear();
        mismatches.putAll(data.mismatches());
    }

    public synchronized int trustOf(UUID playerId) {
        return trust.getOrDefault(playerId, 100);
    }

    public synchronized void reward(MinecraftServer server, UUID playerId) {
        attach(server);
        trust.put(playerId, Math.min(100, trustOf(playerId) + 1));
        save();
    }

    public synchronized void penalize(MinecraftServer server, ServerPlayer player, String reason) {
        attach(server);
        UUID playerId = player.getUUID();
        int next = trustOf(playerId) - MapAccelConfig.TRUST_PENALTY.get();
        trust.put(playerId, next);
        mismatches.merge(playerId, 1, Integer::sum);
        save();
        if (MapAccelConfig.AUTO_BAN.get() && next <= MapAccelConfig.BAN_THRESHOLD.get()) {
            ban(server, player, reason);
        }
    }

    public synchronized int mismatchCount(UUID playerId) {
        return mismatches.getOrDefault(playerId, 0);
    }

    private void save() {
        if (savedData == null) {
            return;
        }
        savedData.trust().clear();
        savedData.trust().putAll(trust);
        savedData.mismatches().clear();
        savedData.mismatches().putAll(mismatches);
        savedData.setDirty();
    }

    private void ban(MinecraftServer server, ServerPlayer player, String reason) {
        GameProfile profile = player.getGameProfile();
        server.getPlayerList().getBans().add(new UserBanListEntry(profile, null, "MapAccel", null, reason));
        player.connection.disconnect(Component.literal("MapAccel validation failed: " + reason));
    }
}
