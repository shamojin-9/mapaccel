package com.shamoji.mapaccel.security;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InboundRateLimiter {
    private final Map<Key, Integer> lastAcceptedTicks = new HashMap<>();

    public synchronized boolean allow(UUID playerId, Bucket bucket, int serverTick, int minIntervalTicks) {
        if (minIntervalTicks <= 0) {
            return true;
        }
        Key key = new Key(playerId, bucket);
        Integer lastTick = lastAcceptedTicks.get(key);
        if (lastTick != null && serverTick - lastTick < minIntervalTicks) {
            return false;
        }
        lastAcceptedTicks.put(key, serverTick);
        return true;
    }

    public synchronized void remove(UUID playerId) {
        lastAcceptedTicks.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    public enum Bucket {
        CLIENT_METRICS,
        HASH_RESULT,
        PREVIEW_RESULT
    }

    private record Key(UUID playerId, Bucket bucket) {
    }
}
