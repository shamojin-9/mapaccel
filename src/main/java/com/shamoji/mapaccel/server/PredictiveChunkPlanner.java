package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PredictiveChunkPlanner {
    public List<ChunkPos> plan(MovementTracker.Prediction prediction) {
        int minRadius = MapAccelConfig.MIN_RADIUS.get();
        int maxForward = MapAccelConfig.MAX_FORWARD_RADIUS.get();
        double speedRatio = Math.min(1.0D, prediction.speedBlocksPerTick() / MapAccelConfig.HIGH_SPEED_BLOCKS_PER_TICK.get());

        double length = minRadius + (maxForward - minRadius) * speedRatio;
        double width = sideRadius(prediction.speedBlocksPerTick(), speedRatio);
        double backward = minRadius + (MapAccelConfig.BACKWARD_RADIUS.get() - minRadius) * speedRatio;
        double vx = prediction.vx();
        double vz = prediction.vz();
        double magnitude = Math.sqrt(vx * vx + vz * vz);
        double forwardX = magnitude > 0.0001D ? vx / magnitude : 0.0D;
        double forwardZ = magnitude > 0.0001D ? vz / magnitude : 0.0D;
        double sideX = -forwardZ;
        double sideZ = forwardX;

        int scan = (int) Math.ceil(Math.max(length, width)) + 1;
        List<ChunkPos> chunks = new ArrayList<>();
        for (int dz = -scan; dz <= scan; dz++) {
            for (int dx = -scan; dx <= scan; dx++) {
                double forward = dx * forwardX + dz * forwardZ;
                double side = dx * sideX + dz * sideZ;
                boolean circular = magnitude <= 0.0001D || speedRatio < 0.15D;
                boolean inside = circular
                        ? dx * dx + dz * dz <= minRadius * minRadius
                        : forward >= -backward && forward <= length && Math.abs(side) <= width;
                if (inside) {
                    chunks.add(new ChunkPos(prediction.chunkX() + dx, prediction.chunkZ() + dz));
                }
            }
        }

        chunks.sort(Comparator.comparingDouble(pos -> distanceScore(prediction, pos)));
        int maxPlan = (int) Math.round(MapAccelConfig.MAX_PLAN_CHUNKS.get()
                + (MapAccelConfig.HIGH_SPEED_PLAN_CHUNKS.get() - MapAccelConfig.MAX_PLAN_CHUNKS.get()) * speedRatio);
        maxPlan = Math.max(64, maxPlan);
        if (chunks.size() > maxPlan) {
            return new ArrayList<>(chunks.subList(0, maxPlan));
        }
        return chunks;
    }

    private double distanceScore(MovementTracker.Prediction prediction, ChunkPos pos) {
        double dx = pos.x - prediction.chunkX();
        double dz = pos.z - prediction.chunkZ();
        double magnitude = Math.sqrt(prediction.vx() * prediction.vx() + prediction.vz() * prediction.vz());
        if (magnitude <= 0.0001D) {
            return dx * dx + dz * dz;
        }
        double forwardX = prediction.vx() / magnitude;
        double forwardZ = prediction.vz() / magnitude;
        double forward = dx * forwardX + dz * forwardZ;
        double side = Math.abs(dx * -forwardZ + dz * forwardX);
        double behindPenalty = forward < 0.0D ? Math.abs(forward) * 80.0D : 0.0D;
        double sidePenalty = side * side * 2.25D;
        double arrivalOrder = Math.max(0.0D, forward) * 1.35D;
        double immediateCorridorBonus = forward >= 0.0D && forward <= 10.0D && side <= 2.0D ? -8.0D : 0.0D;
        return arrivalOrder + sidePenalty + behindPenalty + (dx * dx + dz * dz) * 0.01D + immediateCorridorBonus;
    }

    private double sideRadius(double speedBlocksPerTick, double speedRatio) {
        double normal = MapAccelConfig.SIDE_RADIUS.get();
        double medium = MapAccelConfig.MEDIUM_SPEED_SIDE_RADIUS.get();
        double high = MapAccelConfig.HIGH_SPEED_SIDE_RADIUS.get();
        if (speedRatio < 0.15D) {
            return normal;
        }

        double mediumMax = MapAccelConfig.MEDIUM_SPEED_SIDE_MAX_BLOCKS_PER_TICK.get();
        double extreme = Math.max(mediumMax + 0.1D, MapAccelConfig.EXTREME_SPEED_BLOCKS_PER_TICK.get());
        if (speedBlocksPerTick <= mediumMax) {
            double blend = Math.min(1.0D, speedRatio / 0.65D);
            return normal + (medium - normal) * blend;
        }
        double shrink = Math.min(1.0D, (speedBlocksPerTick - mediumMax) / (extreme - mediumMax));
        return medium + (high - medium) * shrink;
    }
}
