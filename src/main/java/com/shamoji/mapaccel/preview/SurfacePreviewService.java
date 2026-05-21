package com.shamoji.mapaccel.preview;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SurfacePreviewService {
    private static final int MAX_CACHE_SIZE = 8192;
    private final Map<Key, SurfacePreview> cache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, SurfacePreview> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public SurfacePreview preview(ServerLevel level, ChunkPos pos) {
        Key key = new Key(level.dimension().location(), level.getSeed(), pos.x, pos.z, modeFor(level));
        return cache.computeIfAbsent(key, ignored -> compute(level, pos, key.mode));
    }

    public int size() {
        return cache.size();
    }

    public void invalidate(ResourceLocation dimension, long chunkPos) {
        cache.keySet().removeIf(key -> key.dimension.equals(dimension) && ChunkPos.asLong(key.chunkX, key.chunkZ) == chunkPos);
    }

    private SurfacePreview compute(ServerLevel level, ChunkPos pos, PreviewMode mode) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int total = 0;
        int hash = 1;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = (pos.x << 4) + x;
                int worldZ = (pos.z << 4) + z;
                int height = previewHeight(level.getSeed(), worldX, worldZ, mode);
                min = Math.min(min, height);
                max = Math.max(max, height);
                total += height;
                hash = 31 * hash + height;
            }
        }
        return new SurfacePreview(level.dimension().location(), pos, mode, min, max, total / 256, hash);
    }

    private int previewHeight(long seed, int x, int z, PreviewMode mode) {
        long mixed = seed ^ (x * 341873128712L) ^ (z * 132897987541L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        int noise = (int) (mixed & 63L);
        return switch (mode) {
            case SURFACE -> 52 + noise;
            case SHELL -> 32 + noise;
            case ISLAND -> 48 + noise / 2;
        };
    }

    private PreviewMode modeFor(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        if ("the_nether".equals(dimension.getPath())) {
            return PreviewMode.SHELL;
        }
        if ("the_end".equals(dimension.getPath())) {
            return PreviewMode.ISLAND;
        }
        return PreviewMode.SURFACE;
    }

    private record Key(ResourceLocation dimension, long seed, int chunkX, int chunkZ, PreviewMode mode) {
    }
}
