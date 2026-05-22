package com.shamoji.mapaccel.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MapAccelConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MIN_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_FORWARD_RADIUS;
    public static final ForgeConfigSpec.IntValue SIDE_RADIUS;
    public static final ForgeConfigSpec.IntValue HIGH_SPEED_SIDE_RADIUS;
    public static final ForgeConfigSpec.IntValue BACKWARD_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_PLAN_CHUNKS;
    public static final ForgeConfigSpec.IntValue HIGH_SPEED_PLAN_CHUNKS;
    public static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue HIGH_SPEED_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue GPU_PRECOMPUTE_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_SYNC_CHUNKS_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue OVERLOAD_TPS_THRESHOLD;
    public static final ForgeConfigSpec.IntValue SURFACE_PREVIEW_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue LOGIN_GPU_WARMUP_RADIUS;
    public static final ForgeConfigSpec.IntValue STRUCTURE_CACHE_CHANGE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue SAMPLE_TICKS;
    public static final ForgeConfigSpec.IntValue VELOCITY_MEMORY_TICKS;
    public static final ForgeConfigSpec.DoubleValue VELOCITY_MEMORY_MIN_SPEED;
    public static final ForgeConfigSpec.DoubleValue HIGH_SPEED_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ASSIST;
    public static final ForgeConfigSpec.IntValue VALIDATOR_COUNT;
    public static final ForgeConfigSpec.IntValue TRUST_PENALTY;
    public static final ForgeConfigSpec.IntValue BAN_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue AUTO_BAN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_OPENCL_BACKEND;
    public static final ForgeConfigSpec.BooleanValue DISABLE_OPENCL_WITH_EMBEDDIUM;
    public static final ForgeConfigSpec.IntValue LOG_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOG_CHUNK_REQUESTS;
    public static final ForgeConfigSpec.BooleanValue LOG_RESOURCE_SUMMARY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("prediction");
        MIN_RADIUS = builder.comment("Minimum circular preload radius in chunks.").defineInRange("minRadius", 4, 1, 32);
        MAX_FORWARD_RADIUS = builder.comment("Maximum forward prediction distance in chunks at high speed.").defineInRange("maxForwardRadius", 40, 2, 128);
        SIDE_RADIUS = builder.comment("Side radius used when the shape stretches in the movement direction.").defineInRange("sideRadius", 8, 1, 64);
        HIGH_SPEED_SIDE_RADIUS = builder.comment("Side radius at high speed; lower values create a thinner forward corridor.").defineInRange("highSpeedSideRadius", 3, 1, 32);
        BACKWARD_RADIUS = builder.comment("Backward preload radius while moving.").defineInRange("backwardRadius", 1, 0, 32);
        MAX_PLAN_CHUNKS = builder.comment("Candidate cap while slow or medium speed.").defineInRange("maxPlanChunks", 768, 64, 100000);
        HIGH_SPEED_PLAN_CHUNKS = builder.comment("Candidate cap at high speed; keeps long flights from flooding planning work.").defineInRange("highSpeedPlanChunks", 384, 64, 100000);
        CHUNKS_PER_TICK = builder.comment("Server chunk preload budget per tick per player.").defineInRange("chunksPerTick", 4, 1, 64);
        HIGH_SPEED_CHUNKS_PER_TICK = builder.comment("Maximum preload budget per tick per player at high speed.").defineInRange("highSpeedChunksPerTick", 16, 1, 256);
        GPU_PRECOMPUTE_CHUNKS_PER_TICK = builder.comment("GPU terrain preview computations per tick per player, independent from server chunk load requests.").defineInRange("gpuPrecomputeChunksPerTick", 128, 0, 1024);
        MAX_SYNC_CHUNKS_PER_SECOND = builder.comment("Global synchronous chunk generation budget per second. Lower values reduce server stalls.").defineInRange("maxSyncChunksPerSecond", 80, 1, 2000);
        OVERLOAD_TPS_THRESHOLD = builder.comment("Reduce preload pressure when measured server TPS drops below this value.").defineInRange("overloadTpsThreshold", 17.0D, 1.0D, 20.0D);
        SURFACE_PREVIEW_CHUNKS_PER_TICK = builder.comment("Seed-based preview computations per tick for normal ungenerated chunks.").defineInRange("surfacePreviewChunksPerTick", 96, 0, 2048);
        LOGIN_GPU_WARMUP_RADIUS = builder.comment("GPU preview radius around a player when they join or enter a world.").defineInRange("loginGpuWarmupRadius", 8, 0, 64);
        STRUCTURE_CACHE_CHANGE_THRESHOLD = builder.comment("Dirty change score that marks a chunk as a large structure/snapshot candidate.").defineInRange("structureCacheChangeThreshold", 128, 1, 100000);
        SAMPLE_TICKS = builder.comment("Movement samples used for average speed.").defineInRange("sampleTicks", 60, 10, 400);
        VELOCITY_MEMORY_TICKS = builder.comment("Ticks to keep the last strong movement vector after a temporary stop.").defineInRange("velocityMemoryTicks", 80, 0, 400);
        VELOCITY_MEMORY_MIN_SPEED = builder.comment("Minimum speed remembered for prediction inertia.").defineInRange("velocityMemoryMinSpeed", 0.8D, 0.0D, 20.0D);
        HIGH_SPEED_BLOCKS_PER_TICK = builder.comment("Speed treated as high speed for shape stretching.").defineInRange("highSpeedBlocksPerTick", 1.7D, 0.1D, 20.0D);
        builder.pop();

        builder.push("cooperativeSecurity");
        CLIENT_ASSIST = builder.comment("Ask installed clients for hash assistance and validation.").define("clientAssist", true);
        VALIDATOR_COUNT = builder.comment("Number of validator clients requested for each challenge.").defineInRange("validatorCount", 2, 0, 8);
        TRUST_PENALTY = builder.comment("Trust points removed on mismatched hash.").defineInRange("trustPenalty", 25, 1, 1000);
        BAN_THRESHOLD = builder.comment("Trust score at or below this value can trigger an automatic ban.").defineInRange("banThreshold", -100, -10000, 100);
        AUTO_BAN = builder.comment("Ban clients that repeatedly fail validation.").define("autoBan", false);
        builder.pop();

        builder.push("gpu");
        ENABLE_OPENCL_BACKEND = builder.comment("Use OpenCL for predictive terrain height precomputation when a GPU device is available.").define("enableOpenClBackend", true);
        DISABLE_OPENCL_WITH_EMBEDDIUM = builder.comment("Disable the OpenCL backend on clients when Embeddium/Rubidium is loaded to avoid LWJGL/OpenGL initialization conflicts.").define("disableOpenClWithEmbeddium", true);
        builder.pop();

        builder.push("logging");
        LOG_INTERVAL_SECONDS = builder.comment("Seconds between MapAccel summary log lines.").defineInRange("logIntervalSeconds", 5, 1, 300);
        LOG_CHUNK_REQUESTS = builder.comment("Log predictive chunk request totals.").define("logChunkRequests", true);
        LOG_RESOURCE_SUMMARY = builder.comment("Log client resource reports.").define("logResourceSummary", true);
        builder.pop();

        SPEC = builder.build();
    }

    private MapAccelConfig() {
    }
}
