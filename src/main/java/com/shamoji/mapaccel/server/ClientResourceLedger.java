package com.shamoji.mapaccel.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientResourceLedger {
    private final Map<UUID, ClientResource> resources = new HashMap<>();

    public void update(UUID playerId, int freeMemoryMb, int maxMemoryMb, int usedMemoryMb, int fpsEstimate, int serverTick) {
        ClientResource previous = get(playerId);
        resources.put(playerId, new ClientResource(freeMemoryMb, maxMemoryMb, usedMemoryMb, fpsEstimate, serverTick, previous.packetCount() + 1));
    }

    public ClientResource get(UUID playerId) {
        return resources.getOrDefault(playerId, ClientResource.UNKNOWN);
    }

    public void remove(UUID playerId) {
        resources.remove(playerId);
    }

    public int score(UUID playerId) {
        ClientResource resource = get(playerId);
        return resource.freeMemoryMb + resource.fpsEstimate * 16;
    }

    public List<ServerPlayer> rankedAssistClients(Collection<ServerPlayer> players, int serverTick, int minFreeMemoryMb, int minFps) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : players) {
            ClientResource resource = get(player.getUUID());
            if (resource == ClientResource.UNKNOWN) {
                continue;
            }
            if (serverTick - resource.serverTick > 140) {
                continue;
            }
            if (resource.freeMemoryMb < minFreeMemoryMb || resource.fpsEstimate < minFps) {
                continue;
            }
            candidates.add(player);
        }
        candidates.sort(Comparator.comparingInt((ServerPlayer player) -> score(player.getUUID())).reversed());
        return candidates;
    }

    public ResourceSummary summary(int serverTick) {
        if (resources.isEmpty()) {
            return ResourceSummary.EMPTY;
        }
        int clients = 0;
        int freeMemory = 0;
        int usedMemory = 0;
        int maxMemory = 0;
        int fps = 0;
        int freshClients = 0;
        int packets = 0;
        for (ClientResource resource : resources.values()) {
            clients++;
            freeMemory += resource.freeMemoryMb;
            usedMemory += resource.usedMemoryMb;
            maxMemory += resource.maxMemoryMb;
            fps += resource.fpsEstimate;
            packets += resource.packetCount;
            if (serverTick - resource.serverTick <= 140) {
                freshClients++;
            }
        }
        return new ResourceSummary(clients, freshClients, freeMemory, usedMemory, maxMemory, fps / Math.max(1, clients), packets);
    }

    public record ClientResource(int freeMemoryMb, int maxMemoryMb, int usedMemoryMb, int fpsEstimate, int serverTick, int packetCount) {
        static final ClientResource UNKNOWN = new ClientResource(0, 0, 0, 0, 0, 0);
    }

    public record ResourceSummary(int clients, int freshClients, int freeMemoryMb, int usedMemoryMb, int maxMemoryMb, int averageFps, int packets) {
        static final ResourceSummary EMPTY = new ResourceSummary(0, 0, 0, 0, 0, 0, 0);
    }
}
