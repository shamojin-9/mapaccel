package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.net.PreviewAssistResultPacket;
import com.shamoji.mapaccel.preview.PreviewMode;
import com.shamoji.mapaccel.preview.SurfacePreview;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PreviewAssistLedger {
    private static final int MAX_CACHE_SIZE = 8192;
    private static final int MAX_PENDING_REQUESTS = 2048;
    private static final int REQUEST_TTL_TICKS = 400;
    private final AtomicLong nextRequestId = new AtomicLong(1L);
    private final Map<Key, SurfacePreview> previews = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, SurfacePreview> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private final Map<Long, PendingRequest> pendingRequests = new LinkedHashMap<>(256, 0.75F, true);
    private int requestedChunks;
    private int receivedChunks;
    private int acceptedChunks;

    public long nextRequestId() {
        return nextRequestId.getAndIncrement();
    }

    public synchronized void requested(long requestId, UUID receiverId, ResourceLocation dimension, long seed, PreviewMode mode, int[] chunkXs, int[] chunkZs, int serverTick) {
        cleanupExpired(serverTick);
        trimPending();
        int count = Math.min(chunkXs.length, chunkZs.length);
        pendingRequests.put(requestId, new PendingRequest(receiverId, dimension, seed, mode, chunkXs, chunkZs, serverTick));
        requestedChunks += count;
    }

    public synchronized void requestedRemote(long requestId, ResourceLocation dimension, long seed, PreviewMode mode, int[] chunkXs, int[] chunkZs, int serverTick) {
        cleanupExpired(serverTick);
        trimPending();
        int count = Math.min(chunkXs.length, chunkZs.length);
        pendingRequests.put(requestId, new PendingRequest(null, dimension, seed, mode, chunkXs, chunkZs, serverTick));
        requestedChunks += count;
    }

    public synchronized void accept(UUID senderId, PreviewAssistResultPacket packet, int serverTick) {
        cleanupExpired(serverTick);
        PendingRequest request = pendingRequests.remove(packet.requestId());
        if (request == null || (request.receiverId != null && !request.receiverId.equals(senderId))) {
            return;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(packet.dimension());
        if (dimension == null || !dimension.equals(request.dimension) || packet.seed() != request.seed) {
            return;
        }
        PreviewMode mode = safeMode(packet.mode(), dimension);
        if (mode != request.mode) {
            return;
        }
        int count = packet.count();
        receivedChunks += count;
        for (int i = 0; i < count; i++) {
            ChunkPos pos = new ChunkPos(packet.chunkXs()[i], packet.chunkZs()[i]);
            if (!request.chunks.contains(pos.toLong()) || !validHeights(packet.minHeights()[i], packet.maxHeights()[i], packet.averageHeights()[i])) {
                continue;
            }
            SurfacePreview preview = new SurfacePreview(
                    dimension,
                    pos,
                    mode,
                    packet.minHeights()[i],
                    packet.maxHeights()[i],
                    packet.averageHeights()[i],
                    packet.hashes()[i]
            );
            previews.put(new Key(dimension, packet.seed(), preview.chunkPos().x, preview.chunkPos().z, mode), preview);
            acceptedChunks++;
        }
    }

    public synchronized void acceptRemote(PreviewAssistResultPacket packet, int serverTick) {
        accept(null, packet, serverTick);
    }

    private void cleanupExpired(int serverTick) {
        pendingRequests.entrySet().removeIf(entry -> serverTick - entry.getValue().createdTick >= REQUEST_TTL_TICKS);
    }

    private void trimPending() {
        while (pendingRequests.size() >= MAX_PENDING_REQUESTS && !pendingRequests.isEmpty()) {
            Long firstKey = pendingRequests.keySet().iterator().next();
            pendingRequests.remove(firstKey);
        }
    }

    private static boolean validHeights(int minHeight, int maxHeight, int averageHeight) {
        return minHeight >= -4096
                && maxHeight <= 4096
                && minHeight <= averageHeight
                && averageHeight <= maxHeight;
    }

    public synchronized SurfacePreview take(ServerLevel level, ChunkPos pos) {
        ResourceLocation dimension = level.dimension().location();
        PreviewMode mode = com.shamoji.mapaccel.preview.PreviewComputer.modeFor(dimension);
        return previews.remove(new Key(dimension, level.getSeed(), pos.x, pos.z, mode));
    }

    public synchronized Stats snapshotAndReset() {
        Stats stats = new Stats(requestedChunks, receivedChunks, acceptedChunks, previews.size());
        requestedChunks = 0;
        receivedChunks = 0;
        acceptedChunks = 0;
        return stats;
    }

    private static PreviewMode safeMode(String value, ResourceLocation dimension) {
        try {
            return PreviewMode.valueOf(value);
        } catch (RuntimeException ignored) {
            return com.shamoji.mapaccel.preview.PreviewComputer.modeFor(dimension);
        }
    }

    private record Key(ResourceLocation dimension, long seed, int chunkX, int chunkZ, PreviewMode mode) {
    }

    private static final class PendingRequest {
        final UUID receiverId;
        final ResourceLocation dimension;
        final long seed;
        final PreviewMode mode;
        final Set<Long> chunks;
        final int createdTick;

        PendingRequest(UUID receiverId, ResourceLocation dimension, long seed, PreviewMode mode, int[] chunkXs, int[] chunkZs, int createdTick) {
            this.receiverId = receiverId;
            this.dimension = dimension;
            this.seed = seed;
            this.mode = mode;
            this.createdTick = createdTick;
            int count = Math.min(chunkXs.length, chunkZs.length);
            this.chunks = new HashSet<>(count);
            for (int i = 0; i < count; i++) {
                this.chunks.add(new ChunkPos(chunkXs[i], chunkZs[i]).toLong());
            }
        }
    }

    public record Stats(int requestedChunks, int receivedChunks, int acceptedChunks, int cachedChunks) {
    }
}
