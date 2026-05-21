package com.shamoji.mapaccel.gpu;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public interface GpuTerrainBackend {
    boolean isAvailable();

    void warmup(ServerLevel level, ChunkPos pos);

    default void warmupBatch(ServerLevel level, List<ChunkPos> positions) {
        for (ChunkPos pos : positions) {
            warmup(level, pos);
        }
    }

    String name();

    GpuStats snapshotAndReset();

    static GpuTerrainBackend disabled() {
        return DisabledGpuTerrainBackend.INSTANCE;
    }

    static GpuTerrainBackend create() {
        try {
            return OpenClTerrainBackend.createOrDisabled();
        } catch (Throwable throwable) {
            return DisabledGpuTerrainBackend.withReason(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    record GpuStats(int computeRequests, int computedChunks, long computeNanos, String detail) {
        public double computeMillis() {
            return computeNanos / 1_000_000.0D;
        }
    }
}
