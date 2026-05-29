package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.api.MapAccelLoadRisk;
import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;

public final class ExternalLoadRequestQueue {
    private static final Object LOCK = new Object();
    private static final PriorityQueue<Request> REQUESTS = new PriorityQueue<>(
            Comparator.comparingInt((Request request) -> request.priority).reversed()
                    .thenComparingInt(request -> request.distanceSquared)
                    .thenComparingLong(request -> request.sequence)
    );
    private static final Map<RequestKey, Request> PENDING = new HashMap<>();
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();
    private static long nextSequence;
    private static int acceptedWindow;
    private static int rejectedWindow;

    private ExternalLoadRequestQueue() {
    }

    public static boolean isAreaLoaded(ServerLevel level, ChunkPos center, int radius) {
        int clampedRadius = Math.max(0, radius);
        return isAreaLoaded(level, center.x - clampedRadius, center.z - clampedRadius, center.x + clampedRadius, center.z + clampedRadius);
    }

    public static boolean isAreaLoaded(ServerLevel level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        Bounds bounds = Bounds.of(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
            for (int x = bounds.minX; x <= bounds.maxX; x++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static MapAccelLoadRisk enqueue(ServerLevel level, UUID requesterId, ChunkPos center, int radius) {
        int clampedRadius = Math.max(0, radius);
        return enqueueArea(
                level,
                requesterId,
                center.x - clampedRadius,
                center.z - clampedRadius,
                center.x + clampedRadius,
                center.z + clampedRadius,
                0,
                false
        );
    }

    public static MapAccelLoadRisk enqueueArea(ServerLevel level, UUID requesterId, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int priority, boolean forced) {
        if (level == null || requesterId == null) {
            return MapAccelLoadRisk.DISABLED;
        }

        Bounds bounds = Bounds.of(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        ArrayList<ChunkPos> candidates = candidates(level, bounds);
        if (candidates.isEmpty()) {
            return MapAccelLoadRisk.READY;
        }
        if (candidates.size() > MapAccelConfig.API_MAX_AREA_CHUNKS.get()) {
            countRejected();
            return MapAccelLoadRisk.RANGE_TOO_LARGE;
        }

        long gameTime = level.getGameTime();
        int safePriority = forced
                ? MapAccelConfig.API_MAX_PRIORITY.get()
                : Mth.clamp(priority, 0, MapAccelConfig.API_MAX_PRIORITY.get());

        boolean accepted = false;
        synchronized (LOCK) {
            cleanupExpiredLocked(gameTime);
            for (ChunkPos pos : candidates) {
                if (!tryAcquireLocked(requesterId, gameTime)) {
                    rejectedWindow++;
                    return accepted ? MapAccelLoadRisk.LOADING : MapAccelLoadRisk.BUDGET_EXCEEDED;
                }
                RequestKey key = new RequestKey(level.dimension(), pos.toLong());
                Request existing = PENDING.get(key);
                if (existing != null) {
                    if (safePriority > existing.priority) {
                        Request upgraded = new Request(level.dimension(), pos, requesterId, gameTime, nextSequence++, safePriority, bounds.distanceSquared(pos));
                        PENDING.put(key, upgraded);
                        REQUESTS.add(upgraded);
                    }
                    accepted = true;
                    continue;
                }
                if (PENDING.size() >= MapAccelConfig.API_MAX_PENDING_CHUNKS.get()) {
                    rejectedWindow++;
                    return accepted ? MapAccelLoadRisk.LOADING : MapAccelLoadRisk.QUEUE_FULL;
                }
                Request request = new Request(level.dimension(), pos, requesterId, gameTime, nextSequence++, safePriority, bounds.distanceSquared(pos));
                PENDING.put(key, request);
                REQUESTS.add(request);
                acceptedWindow++;
                accepted = true;
            }
        }
        return accepted ? MapAccelLoadRisk.LOADING : MapAccelLoadRisk.READY;
    }

    public static DrainStats drain(MinecraftServer server, int maxChunks) {
        if (server == null || maxChunks <= 0) {
            synchronized (LOCK) {
                return snapshotStatsLocked(0, 0);
            }
        }

        int loaded = 0;
        int skipped = 0;
        long gameTime = server.overworld().getGameTime();
        while (loaded < maxChunks) {
            Request request;
            synchronized (LOCK) {
                cleanupExpiredLocked(gameTime);
                request = pollValidLocked();
            }
            if (request == null) {
                break;
            }

            ServerLevel level = server.getLevel(request.dimension);
            if (level == null || level.hasChunk(request.pos.x, request.pos.z)) {
                skipped++;
                continue;
            }
            level.getChunkSource().getChunk(request.pos.x, request.pos.z, ChunkStatus.FULL, true);
            loaded++;
        }
        synchronized (LOCK) {
            cleanupWindowsLocked(gameTime);
            return snapshotStatsLocked(loaded, skipped);
        }
    }

    public static int pendingRequests() {
        synchronized (LOCK) {
            return PENDING.size();
        }
    }

    public static int activeRequesters() {
        synchronized (LOCK) {
            return WINDOWS.size();
        }
    }

    private static Request pollValidLocked() {
        while (!REQUESTS.isEmpty()) {
            Request request = REQUESTS.poll();
            RequestKey key = new RequestKey(request.dimension, request.pos.toLong());
            if (PENDING.get(key) != request) {
                continue;
            }
            PENDING.remove(key);
            return request;
        }
        return null;
    }

    private static ArrayList<ChunkPos> candidates(ServerLevel level, Bounds bounds) {
        ArrayList<ChunkPos> chunks = new ArrayList<>();
        for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
            for (int x = bounds.minX; x <= bounds.maxX; x++) {
                if (!level.hasChunk(x, z)) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
        chunks.sort(Comparator.comparingInt(bounds::distanceSquared));
        return chunks;
    }

    private static boolean tryAcquireLocked(UUID requesterId, long gameTime) {
        int windowTicks = Math.max(1, MapAccelConfig.API_REQUEST_WINDOW_TICKS.get());
        int maxRequests = Math.max(1, MapAccelConfig.API_REQUESTS_PER_WINDOW.get());
        Window window = WINDOWS.computeIfAbsent(requesterId, ignored -> new Window(gameTime));
        if (gameTime - window.startTick >= windowTicks) {
            window.startTick = gameTime;
            window.used = 0;
        }
        if (window.used >= maxRequests) {
            return false;
        }
        window.used++;
        return true;
    }

    private static void cleanupExpiredLocked(long gameTime) {
        int ttl = MapAccelConfig.API_REQUEST_TTL_TICKS.get();
        PENDING.entrySet().removeIf(entry -> gameTime - entry.getValue().createdAtTick >= ttl);
    }

    private static void cleanupWindowsLocked(long gameTime) {
        int windowTicks = Math.max(1, MapAccelConfig.API_REQUEST_WINDOW_TICKS.get());
        WINDOWS.entrySet().removeIf(entry -> gameTime - entry.getValue().startTick > windowTicks * 4L);
    }

    private static void countRejected() {
        synchronized (LOCK) {
            rejectedWindow++;
        }
    }

    private static DrainStats snapshotStatsLocked(int loaded, int skipped) {
        DrainStats stats = new DrainStats(loaded, skipped, PENDING.size(), acceptedWindow, rejectedWindow);
        acceptedWindow = 0;
        rejectedWindow = 0;
        return stats;
    }

    private static final class Window {
        private long startTick;
        private int used;

        private Window(long startTick) {
            this.startTick = startTick;
        }
    }

    private static final class Bounds {
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final int centerX;
        final int centerZ;

        private Bounds(int minX, int minZ, int maxX, int maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
            this.centerX = this.minX + (this.maxX - this.minX) / 2;
            this.centerZ = this.minZ + (this.maxZ - this.minZ) / 2;
        }

        static Bounds of(int minX, int minZ, int maxX, int maxZ) {
            return new Bounds(minX, minZ, maxX, maxZ);
        }

        int distanceSquared(ChunkPos pos) {
            int dx = pos.x - centerX;
            int dz = pos.z - centerZ;
            return dx * dx + dz * dz;
        }
    }

    private record Request(ResourceKey<Level> dimension, ChunkPos pos, UUID requesterId, long createdAtTick, long sequence, int priority, int distanceSquared) {
    }

    private record RequestKey(ResourceKey<Level> dimension, long chunkPos) {
        private RequestKey {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    public record DrainStats(int loadedChunks, int skippedChunks, int pendingChunks, int acceptedChunks, int rejectedChunks) {
    }
}
