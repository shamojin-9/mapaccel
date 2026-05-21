package com.shamoji.mapaccel.cache;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public final class RegionSnapshotCache {
    private final Map<Key, RegionSnapshot> snapshots = new HashMap<>();

    public void rememberLoadedChunk(ServerLevel level, ChunkPos pos, int previewHash, boolean dirty) {
        if (!dirty) {
            return;
        }
        Key key = new Key(level.dimension().location(), pos.toLong());
        snapshots.put(key, new RegionSnapshot(level.dimension().location(), pos, previewHash, System.currentTimeMillis()));
    }

    public boolean hasSnapshot(ServerLevel level, ChunkPos pos) {
        return snapshots.containsKey(new Key(level.dimension().location(), pos.toLong()));
    }

    public int size() {
        return snapshots.size();
    }

    public record RegionSnapshot(ResourceLocation dimension, ChunkPos chunkPos, int previewHash, long capturedAtMillis) {
    }

    private record Key(ResourceLocation dimension, long chunkPos) {
    }
}
