package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.net.PreviewAssistResultPacket;
import com.shamoji.mapaccel.preview.PreviewMode;
import com.shamoji.mapaccel.preview.SurfacePreview;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PreviewAssistLedger {
    private static final int MAX_CACHE_SIZE = 8192;
    private final AtomicLong nextRequestId = new AtomicLong(1L);
    private final Map<Key, SurfacePreview> previews = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, SurfacePreview> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private int requestedChunks;
    private int receivedChunks;
    private int acceptedChunks;

    public long nextRequestId() {
        return nextRequestId.getAndIncrement();
    }

    public synchronized void requested(int chunks) {
        requestedChunks += chunks;
    }

    public synchronized void accept(UUID senderId, PreviewAssistResultPacket packet) {
        ResourceLocation dimension = new ResourceLocation(packet.dimension());
        PreviewMode mode = safeMode(packet.mode(), dimension);
        int count = packet.count();
        receivedChunks += count;
        for (int i = 0; i < count; i++) {
            SurfacePreview preview = new SurfacePreview(
                    dimension,
                    new ChunkPos(packet.chunkXs()[i], packet.chunkZs()[i]),
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
        } catch (IllegalArgumentException ignored) {
            return com.shamoji.mapaccel.preview.PreviewComputer.modeFor(dimension);
        }
    }

    private record Key(ResourceLocation dimension, long seed, int chunkX, int chunkZ, PreviewMode mode) {
    }

    public record Stats(int requestedChunks, int receivedChunks, int acceptedChunks, int cachedChunks) {
    }
}
