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

    public void remember(ServerLevel level, SurfacePreview preview) {
        Key key = new Key(level.dimension().location(), level.getSeed(), preview.chunkPos().x, preview.chunkPos().z, preview.mode());
        cache.put(key, preview);
    }

    public int size() {
        return cache.size();
    }

    public void invalidate(ResourceLocation dimension, long chunkPos) {
        cache.keySet().removeIf(key -> key.dimension.equals(dimension) && ChunkPos.asLong(key.chunkX, key.chunkZ) == chunkPos);
    }

    private SurfacePreview compute(ServerLevel level, ChunkPos pos, PreviewMode mode) {
        return PreviewComputer.compute(level.dimension().location(), level.getSeed(), pos, mode);
    }

    private PreviewMode modeFor(ServerLevel level) {
        return PreviewComputer.modeFor(level.dimension().location());
    }

    private record Key(ResourceLocation dimension, long seed, int chunkX, int chunkZ, PreviewMode mode) {
    }
}
