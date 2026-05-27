package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HotChunkCache {
    private static final int TICKET_LEVEL = 2;
    private static final TicketType<ChunkPos> HOT_TICKET = TicketType.create("mapaccel_hot", Comparator.comparingLong(ChunkPos::toLong));

    private final Map<Key, Entry> hotChunks = new LinkedHashMap<>(256, 0.75F, true);
    private final Map<UUID, ArrayDeque<TrailPoint>> trails = new HashMap<>();
    private int pinnedWindow;
    private int refreshedWindow;
    private int releasedWindow;
    private int hotHitWindow;

    public void keepAround(ServerLevel level, ServerPlayer player, int serverTick) {
        if (!MapAccelConfig.HOT_CHUNK_CACHE_ENABLED.get() || MapAccelConfig.HOT_CHUNK_CACHE_MAX_CHUNKS.get() <= 0) {
            return;
        }
        int radius = MapAccelConfig.HOT_CHUNK_CACHE_RADIUS.get();
        ChunkPos center = player.chunkPosition();
        ArrayDeque<TrailPoint> trail = trails.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        rememberTrail(trail, level.dimension(), center, serverTick);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                touchIfLoaded(level, new ChunkPos(center.x + dx, center.z + dz), serverTick);
            }
        }
        trimTrail(trail, Math.max(32, MapAccelConfig.HOT_CHUNK_TRAIL_PLAN_CHUNKS.get() * 2));
    }

    public void touchIfLoaded(ServerLevel level, ChunkPos pos, int serverTick) {
        if (!MapAccelConfig.HOT_CHUNK_CACHE_ENABLED.get() || MapAccelConfig.HOT_CHUNK_CACHE_MAX_CHUNKS.get() <= 0 || !level.hasChunk(pos.x, pos.z)) {
            return;
        }
        Key key = new Key(level.dimension(), pos.toLong());
        Entry existing = hotChunks.get(key);
        int expiresAt = serverTick + MapAccelConfig.HOT_CHUNK_CACHE_TTL_TICKS.get();
        if (existing != null) {
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
            refreshedWindow++;
            return;
        }
        level.getChunkSource().addRegionTicket(HOT_TICKET, pos, TICKET_LEVEL, pos);
        hotChunks.put(key, new Entry(level.dimension(), pos, expiresAt));
        pinnedWindow++;
        trimToMax(level.getServer());
    }

    public boolean isHot(ServerLevel level, ChunkPos pos) {
        return hotChunks.containsKey(new Key(level.dimension(), pos.toLong()));
    }

    public void countHotHit() {
        hotHitWindow++;
    }

    public List<ChunkPos> recentTrail(ServerLevel level, UUID playerId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        ArrayDeque<TrailPoint> trail = trails.get(playerId);
        if (trail == null || trail.isEmpty()) {
            return List.of();
        }
        ArrayList<ChunkPos> result = new ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        Iterator<TrailPoint> iterator = trail.descendingIterator();
        while (iterator.hasNext() && result.size() < limit) {
            TrailPoint point = iterator.next();
            if (!point.dimension.equals(level.dimension())) {
                continue;
            }
            if (seen.add(point.chunkPos)) {
                result.add(new ChunkPos(point.chunkPos));
            }
        }
        return result;
    }

    public void cleanup(MinecraftServer server, int serverTick) {
        if (hotChunks.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Key, Entry>> iterator = hotChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.expiresAt > serverTick) {
                continue;
            }
            release(server, entry);
            iterator.remove();
        }
    }

    public void removePlayer(UUID playerId) {
        trails.remove(playerId);
    }

    public Stats snapshotAndReset() {
        Stats stats = new Stats(pinnedWindow, refreshedWindow, releasedWindow, hotHitWindow, hotChunks.size());
        pinnedWindow = 0;
        refreshedWindow = 0;
        releasedWindow = 0;
        hotHitWindow = 0;
        return stats;
    }

    private void trimToMax(MinecraftServer server) {
        int max = MapAccelConfig.HOT_CHUNK_CACHE_MAX_CHUNKS.get();
        Iterator<Entry> iterator = hotChunks.values().iterator();
        while (hotChunks.size() > max && iterator.hasNext()) {
            Entry entry = iterator.next();
            release(server, entry);
            iterator.remove();
        }
    }

    private void release(MinecraftServer server, Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension);
        if (level != null) {
            level.getChunkSource().removeRegionTicket(HOT_TICKET, entry.pos, TICKET_LEVEL, entry.pos);
        }
        releasedWindow++;
    }

    private void rememberTrail(ArrayDeque<TrailPoint> trail, ResourceKey<Level> dimension, ChunkPos pos, int serverTick) {
        TrailPoint last = trail.peekLast();
        long chunkPos = pos.toLong();
        if (last != null && last.dimension.equals(dimension) && last.chunkPos == chunkPos) {
            return;
        }
        trail.addLast(new TrailPoint(dimension, chunkPos, serverTick));
    }

    private void trimTrail(ArrayDeque<TrailPoint> trail, int maxSize) {
        while (trail.size() > maxSize) {
            trail.removeFirst();
        }
    }

    private record Key(ResourceKey<Level> dimension, long chunkPos) {
    }

    private static final class Entry {
        final ResourceKey<Level> dimension;
        final ChunkPos pos;
        int expiresAt;

        Entry(ResourceKey<Level> dimension, ChunkPos pos, int expiresAt) {
            this.dimension = dimension;
            this.pos = pos;
            this.expiresAt = expiresAt;
        }
    }

    private record TrailPoint(ResourceKey<Level> dimension, long chunkPos, int tick) {
    }

    public record Stats(int pinned, int refreshed, int released, int hotHits, int retained) {
    }
}
