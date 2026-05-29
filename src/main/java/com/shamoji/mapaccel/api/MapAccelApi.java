package com.shamoji.mapaccel.api;

import com.shamoji.mapaccel.config.MapAccelConfig;
import com.shamoji.mapaccel.server.ExternalLoadRequestQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class MapAccelApi {
    private MapAccelApi() {
    }

    /**
     * Requests that MapAccel preload the projected chunk around a high-speed object.
     * This is bounded by server config and never bypasses the server tick budget.
     */
    public static MapAccelLoadRisk requestHighSpeedLoad(ServerLevel level, UUID requesterId, Vec3 position, Vec3 velocity, int horizonTicks) {
        if (level == null || requesterId == null || position == null || velocity == null) {
            return MapAccelLoadRisk.DISABLED;
        }

        int clampedHorizon = Mth.clamp(horizonTicks, 1, MapAccelConfig.API_MAX_HORIZON_TICKS.get());
        Vec3 projected = position.add(velocity.scale(clampedHorizon));
        return requestChunkLoad(level, requesterId, Mth.floor(projected.x) >> 4, Mth.floor(projected.z) >> 4);
    }

    /**
     * Requests the configured radius around one chunk.
     */
    public static MapAccelLoadRisk requestChunkLoad(ServerLevel level, UUID requesterId, int chunkX, int chunkZ) {
        if (level == null || requesterId == null) {
            return MapAccelLoadRisk.DISABLED;
        }
        ChunkPos center = new ChunkPos(chunkX, chunkZ);
        if (ExternalLoadRequestQueue.isAreaLoaded(level, center, MapAccelConfig.API_LOAD_RADIUS.get())) {
            return MapAccelLoadRisk.READY;
        }
        return ExternalLoadRequestQueue.enqueue(level, requesterId, center, MapAccelConfig.API_LOAD_RADIUS.get());
    }

    /**
     * Requests a rectangular chunk range. Coordinates are chunk coordinates, not block coordinates.
     */
    public static MapAccelLoadRisk requestAreaLoad(ServerLevel level, UUID requesterId, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        return requestPriorityAreaLoad(level, requesterId, minChunkX, minChunkZ, maxChunkX, maxChunkZ, 0);
    }

    /**
     * Requests a rectangular chunk range with caller-supplied priority.
     * Higher priority chunks drain first, but all security budgets and range caps still apply.
     */
    public static MapAccelLoadRisk requestPriorityAreaLoad(ServerLevel level, UUID requesterId, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int priority) {
        if (level == null || requesterId == null) {
            return MapAccelLoadRisk.DISABLED;
        }
        if (ExternalLoadRequestQueue.isAreaLoaded(level, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
            return MapAccelLoadRisk.READY;
        }
        return ExternalLoadRequestQueue.enqueueArea(level, requesterId, minChunkX, minChunkZ, maxChunkX, maxChunkZ, priority, false);
    }

    /**
     * Requests a rectangular block-coordinate range. The range is converted to covering chunks.
     */
    public static MapAccelLoadRisk requestBlockAreaLoad(ServerLevel level, UUID requesterId, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, int priority) {
        return requestPriorityAreaLoad(
                level,
                requesterId,
                Math.min(minBlockX, maxBlockX) >> 4,
                Math.min(minBlockZ, maxBlockZ) >> 4,
                Math.max(minBlockX, maxBlockX) >> 4,
                Math.max(minBlockZ, maxBlockZ) >> 4,
                priority
        );
    }

    /**
     * Requests a rectangular chunk range at the highest configured API priority.
     * "Forced" means preferred over normal API requests; it does not ignore range, rate, or global chunk budgets.
     */
    public static MapAccelLoadRisk requestForcedAreaLoad(ServerLevel level, UUID requesterId, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        if (level == null || requesterId == null) {
            return MapAccelLoadRisk.DISABLED;
        }
        if (ExternalLoadRequestQueue.isAreaLoaded(level, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
            return MapAccelLoadRisk.READY;
        }
        return ExternalLoadRequestQueue.enqueueArea(level, requesterId, minChunkX, minChunkZ, maxChunkX, maxChunkZ, MapAccelConfig.API_MAX_PRIORITY.get(), true);
    }

    /**
     * Requests a forced rectangular block-coordinate range. The range is converted to covering chunks.
     */
    public static MapAccelLoadRisk requestForcedBlockAreaLoad(ServerLevel level, UUID requesterId, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        return requestForcedAreaLoad(
                level,
                requesterId,
                Math.min(minBlockX, maxBlockX) >> 4,
                Math.min(minBlockZ, maxBlockZ) >> 4,
                Math.max(minBlockX, maxBlockX) >> 4,
                Math.max(minBlockZ, maxBlockZ) >> 4
        );
    }
}
