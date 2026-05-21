package com.shamoji.mapaccel.security;

import com.shamoji.mapaccel.MapAccel;
import com.shamoji.mapaccel.config.MapAccelConfig;
import com.shamoji.mapaccel.net.HashChallengePacket;
import com.shamoji.mapaccel.net.MapAccelNetwork;
import com.shamoji.mapaccel.world.ChunkHasher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ValidationCoordinator {
    private final TrustLedger ledger;
    private final Map<Long, Challenge> challenges = new HashMap<>();

    public ValidationCoordinator(TrustLedger ledger) {
        this.ledger = ledger;
    }

    public void maybeChallenge(ServerLevel level, ServerPlayer requester, List<ServerPlayer> onlinePlayers, ChunkPos pos) {
        if (!MapAccelConfig.CLIENT_ASSIST.get() || level.getGameTime() % 80 != 0) {
            return;
        }
        List<ServerPlayer> validators = chooseValidators(requester, onlinePlayers);
        if (validators.isEmpty()) {
            return;
        }
        String serverHash = ChunkHasher.hashServerChunk(level, pos);
        long challengeId = level.random.nextLong();
        Challenge challenge = new Challenge(challengeId, requester.getUUID(), pos, serverHash, validators.stream().map(ServerPlayer::getUUID).toList());
        challenges.put(challengeId, challenge);

        HashChallengePacket packet = new HashChallengePacket(challengeId, level.dimension().location().toString(), pos.x, pos.z);
        MapAccelNetwork.send(PacketDistributor.PLAYER.with(() -> requester), packet);
        for (ServerPlayer validator : validators) {
            MapAccelNetwork.send(PacketDistributor.PLAYER.with(() -> validator), packet);
        }
    }

    public void acceptHash(MinecraftServer server, UUID senderId, long challengeId, String hash, boolean available) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            return;
        }
        if (!available) {
            return;
        }
        boolean matches = challenge.serverHash.equals(hash);
        ServerPlayer sender = server.getPlayerList().getPlayer(senderId);
        if (sender == null) {
            return;
        }
        if (matches) {
            ledger.reward(senderId);
        } else {
            ledger.penalize(server, sender, "chunk hash mismatch at " + challenge.pos.x + "," + challenge.pos.z);
            MapAccel.LOGGER.warn("Hash mismatch from {} for challenge {} expected={} actual={}", sender.getGameProfile().getName(), challengeId, challenge.serverHash, hash);
        }
        challenge.replies.add(senderId);
        if (challenge.complete()) {
            challenges.remove(challengeId);
        }
    }

    private List<ServerPlayer> chooseValidators(ServerPlayer requester, List<ServerPlayer> onlinePlayers) {
        int validatorCount = MapAccelConfig.VALIDATOR_COUNT.get();
        if (validatorCount <= 0) {
            return List.of();
        }
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : onlinePlayers) {
            if (!player.getUUID().equals(requester.getUUID())) {
                candidates.add(player);
            }
        }
        candidates.sort((a, b) -> {
            int trust = Integer.compare(ledger.trustOf(b.getUUID()), ledger.trustOf(a.getUUID()));
            if (trust != 0) {
                return trust;
            }
            return Integer.compare(
                    com.shamoji.mapaccel.server.MapAccelServerState.CLIENT_RESOURCES.score(b.getUUID()),
                    com.shamoji.mapaccel.server.MapAccelServerState.CLIENT_RESOURCES.score(a.getUUID())
            );
        });
        return candidates.stream().limit(validatorCount).toList();
    }

    public Optional<Integer> trust(UUID playerId) {
        return Optional.of(ledger.trustOf(playerId));
    }

    private static final class Challenge {
        final long id;
        final UUID requester;
        final ChunkPos pos;
        final String serverHash;
        final List<UUID> validators;
        final List<UUID> replies = new ArrayList<>();

        Challenge(long id, UUID requester, ChunkPos pos, String serverHash, List<UUID> validators) {
            this.id = id;
            this.requester = requester;
            this.pos = pos;
            this.serverHash = serverHash;
            this.validators = validators;
        }

        boolean complete() {
            return replies.contains(requester) && replies.containsAll(validators);
        }
    }
}
