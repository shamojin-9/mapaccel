package com.shamoji.mapaccel.cache;

import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DirtyChunkTracker {
    private final Map<ResourceLocation, Map<Long, DirtyChunk>> dirtyChunks = new HashMap<>();

    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            mark(level, new ChunkPos(event.getPos()), 1);
        }
    }

    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            mark(level, new ChunkPos(event.getPos()), 1);
        }
    }

    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Set<Long> touched = new HashSet<>();
            event.getAffectedBlocks().forEach(pos -> touched.add(new ChunkPos(pos).toLong()));
            for (long chunk : touched) {
                mark(level.dimension().location(), chunk, 64);
            }
        }
    }

    public boolean isDirty(ServerLevel level, ChunkPos pos) {
        return dirtyChunks.getOrDefault(level.dimension().location(), Map.of()).containsKey(pos.toLong());
    }

    public DirtySummary summary() {
        int chunks = 0;
        int large = 0;
        int changes = 0;
        for (Map<Long, DirtyChunk> dimension : dirtyChunks.values()) {
            chunks += dimension.size();
            for (DirtyChunk chunk : dimension.values()) {
                changes += chunk.changeScore;
                if (chunk.changeScore >= MapAccelConfig.STRUCTURE_CACHE_CHANGE_THRESHOLD.get()) {
                    large++;
                }
            }
        }
        return new DirtySummary(chunks, large, changes);
    }

    public void clear(ServerLevel level, ChunkPos pos) {
        Map<Long, DirtyChunk> dimension = dirtyChunks.get(level.dimension().location());
        if (dimension != null) {
            dimension.remove(pos.toLong());
        }
    }

    private void mark(ServerLevel level, ChunkPos pos, int score) {
        mark(level.dimension().location(), pos.toLong(), score);
    }

    private void mark(ResourceLocation dimension, long chunk, int score) {
        dirtyChunks.computeIfAbsent(dimension, ignored -> new HashMap<>())
                .merge(chunk, new DirtyChunk(chunk, score), DirtyChunk::merge);
    }

    private record DirtyChunk(long chunkPos, int changeScore) {
        DirtyChunk merge(DirtyChunk other) {
            return new DirtyChunk(chunkPos, changeScore + other.changeScore);
        }
    }

    public record DirtySummary(int chunks, int largeChangeCandidates, int changeScore) {
    }
}
