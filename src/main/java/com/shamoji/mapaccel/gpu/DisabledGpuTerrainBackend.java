package com.shamoji.mapaccel.gpu;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

final class DisabledGpuTerrainBackend implements GpuTerrainBackend {
    static final DisabledGpuTerrainBackend INSTANCE = new DisabledGpuTerrainBackend("disabled");
    private final String reason;

    private DisabledGpuTerrainBackend(String reason) {
        this.reason = reason;
    }

    static DisabledGpuTerrainBackend withReason(String reason) {
        return new DisabledGpuTerrainBackend(reason);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void warmup(ServerLevel level, ChunkPos pos) {
        // Intentionally empty: OpenGL belongs to the render thread and is not a safe worldgen backend.
    }

    @Override
    public String name() {
        return "disabled";
    }

    @Override
    public GpuStats snapshotAndReset() {
        return new GpuStats(0, 0, 0L, reason);
    }
}
