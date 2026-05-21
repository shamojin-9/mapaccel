package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MovementTracker {
    private final Map<UUID, Deque<Sample>> samples = new HashMap<>();
    private final Map<UUID, RememberedVelocity> rememberedVelocities = new HashMap<>();

    public Prediction update(ServerPlayer player) {
        Deque<Sample> positions = samples.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        positions.addLast(new Sample(player.position(), player.getServer().getTickCount()));
        while (positions.size() > MapAccelConfig.SAMPLE_TICKS.get()) {
            positions.removeFirst();
        }
        return predictionFor(player, positions);
    }

    public void remove(UUID playerId) {
        samples.remove(playerId);
        rememberedVelocities.remove(playerId);
    }

    private Prediction predictionFor(ServerPlayer player, Deque<Sample> positions) {
        if (positions.size() < 2) {
            return new Prediction(player.chunkPosition().x, player.chunkPosition().z, 0.0D, 0.0D, 0.0D);
        }

        Sample first = positions.peekFirst();
        Sample last = positions.peekLast();
        Sample previous = null;
        for (Sample sample : positions) {
            if (sample != last) {
                previous = sample;
            }
        }

        Vec3 windowDelta = last.position.subtract(first.position);
        double windowTicks = Math.max(1, last.tick - first.tick);
        double windowVx = windowDelta.x / windowTicks;
        double windowVz = windowDelta.z / windowTicks;

        double recentVx = windowVx;
        double recentVz = windowVz;
        if (previous != null) {
            Vec3 recentDelta = last.position.subtract(previous.position);
            double recentTicks = Math.max(1, last.tick - previous.tick);
            recentVx = recentDelta.x / recentTicks;
            recentVz = recentDelta.z / recentTicks;
        }

        Vec3 motion = player.getDeltaMovement();
        double motionSpeed = horizontalSpeed(motion.x, motion.z);
        double recentSpeed = horizontalSpeed(recentVx, recentVz);
        double windowSpeed = horizontalSpeed(windowVx, windowVz);
        double vx = recentSpeed >= windowSpeed ? recentVx : windowVx;
        double vz = recentSpeed >= windowSpeed ? recentVz : windowVz;
        if (motionSpeed > horizontalSpeed(vx, vz)) {
            vx = motion.x;
            vz = motion.z;
        }
        int nowTick = last.tick;
        double speed = horizontalSpeed(vx, vz);
        RememberedVelocity remembered = rememberedVelocities.get(player.getUUID());
        if (speed >= MapAccelConfig.VELOCITY_MEMORY_MIN_SPEED.get()) {
            rememberedVelocities.put(player.getUUID(), new RememberedVelocity(vx, vz, speed, nowTick));
        } else if (remembered != null) {
            int age = nowTick - remembered.tick;
            int memoryTicks = MapAccelConfig.VELOCITY_MEMORY_TICKS.get();
            if (memoryTicks > 0 && age <= memoryTicks) {
                double decay = 1.0D - (age / (double) memoryTicks);
                double rememberedSpeed = remembered.speed * Math.max(0.20D, decay);
                if (rememberedSpeed > speed) {
                    double scale = rememberedSpeed / Math.max(0.0001D, remembered.speed);
                    vx = remembered.vx * scale;
                    vz = remembered.vz * scale;
                    speed = horizontalSpeed(vx, vz);
                }
            } else {
                rememberedVelocities.remove(player.getUUID());
            }
        }
        return new Prediction(player.chunkPosition().x, player.chunkPosition().z, vx, vz, speed);
    }

    private double horizontalSpeed(double vx, double vz) {
        return Math.sqrt(vx * vx + vz * vz);
    }

    private record Sample(Vec3 position, int tick) {
    }

    private record RememberedVelocity(double vx, double vz, double speed, int tick) {
    }

    public record Prediction(int chunkX, int chunkZ, double vx, double vz, double speedBlocksPerTick) {
    }
}
