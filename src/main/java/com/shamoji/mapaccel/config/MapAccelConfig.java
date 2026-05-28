package com.shamoji.mapaccel.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MapAccelConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MIN_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_FORWARD_RADIUS;
    public static final ForgeConfigSpec.IntValue SIDE_RADIUS;
    public static final ForgeConfigSpec.IntValue MEDIUM_SPEED_SIDE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue MEDIUM_SPEED_SIDE_MAX_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue EXTREME_SPEED_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue HIGH_SPEED_SIDE_RADIUS;
    public static final ForgeConfigSpec.IntValue BACKWARD_RADIUS;
    public static final ForgeConfigSpec.BooleanValue HOT_CHUNK_CACHE_ENABLED;
    public static final ForgeConfigSpec.IntValue HOT_CHUNK_CACHE_RADIUS;
    public static final ForgeConfigSpec.IntValue HOT_CHUNK_CACHE_TTL_TICKS;
    public static final ForgeConfigSpec.IntValue HOT_CHUNK_CACHE_MAX_CHUNKS;
    public static final ForgeConfigSpec.IntValue HOT_CHUNK_TRAIL_PLAN_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAX_PLAN_CHUNKS;
    public static final ForgeConfigSpec.IntValue HIGH_SPEED_PLAN_CHUNKS;
    public static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue HIGH_SPEED_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue GPU_PRECOMPUTE_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_SYNC_CHUNKS_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue OVERLOAD_TPS_THRESHOLD;
    public static final ForgeConfigSpec.IntValue SURFACE_PREVIEW_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue LOGIN_GPU_WARMUP_RADIUS;
    public static final ForgeConfigSpec.IntValue LOGIN_GRACE_TICKS;
    public static final ForgeConfigSpec.IntValue LOGIN_RAMP_TICKS;
    public static final ForgeConfigSpec.IntValue STRUCTURE_CACHE_CHANGE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue SAMPLE_TICKS;
    public static final ForgeConfigSpec.IntValue VELOCITY_MEMORY_TICKS;
    public static final ForgeConfigSpec.DoubleValue VELOCITY_MEMORY_MIN_SPEED;
    public static final ForgeConfigSpec.DoubleValue HIGH_SPEED_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ASSIST;
    public static final ForgeConfigSpec.IntValue CLIENT_ASSIST_PREVIEW_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue CLIENT_ASSIST_MAX_BATCH_CHUNKS;
    public static final ForgeConfigSpec.IntValue CLIENT_ASSIST_MIN_FREE_MEMORY_MB;
    public static final ForgeConfigSpec.IntValue CLIENT_ASSIST_MIN_FPS;
    public static final ForgeConfigSpec.IntValue VALIDATOR_COUNT;
    public static final ForgeConfigSpec.IntValue TRUST_PENALTY;
    public static final ForgeConfigSpec.IntValue BAN_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue AUTO_BAN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_OPENCL_BACKEND;
    public static final ForgeConfigSpec.BooleanValue DISABLE_OPENCL_WITH_EMBEDDIUM;
    public static final ForgeConfigSpec.IntValue LOG_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOG_CHUNK_REQUESTS;
    public static final ForgeConfigSpec.BooleanValue LOG_RESOURCE_SUMMARY;
    public static final ForgeConfigSpec.BooleanValue STABILIZE_CREATIVE_FLIGHT_FOV;
    public static final ForgeConfigSpec.BooleanValue PATCH_EMBEDDIUM_DEPTH_COMPAT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("prediction");
        MIN_RADIUS = builder.comment("Minimum circular preload radius in chunks.").defineInRange("minRadius", 4, 1, 32);
        MAX_FORWARD_RADIUS = builder.comment("Maximum forward prediction distance in chunks at high speed.").defineInRange("maxForwardRadius", 56, 2, 128);
        SIDE_RADIUS = builder.comment("Side radius used when the shape stretches in the movement direction.").defineInRange("sideRadius", 8, 1, 64);
        MEDIUM_SPEED_SIDE_RADIUS = builder.comment("Side radius for controllable fast flight. This widens 3x-4x flight without flooding 5x+ flight.").defineInRange("mediumSpeedSideRadius", 6, 1, 64);
        MEDIUM_SPEED_SIDE_MAX_BLOCKS_PER_TICK = builder.comment("Use mediumSpeedSideRadius up to this measured speed. Raise it if 4x flight is still too narrow.").defineInRange("mediumSpeedSideMaxBlocksPerTick", 45.0D, 0.1D, 200.0D);
        EXTREME_SPEED_BLOCKS_PER_TICK = builder.comment("Speed where prediction fully shrinks to highSpeedSideRadius. Lower values preserve 5x+ straight-line performance.").defineInRange("extremeSpeedBlocksPerTick", 75.0D, 0.1D, 400.0D);
        HIGH_SPEED_SIDE_RADIUS = builder.comment("Side radius at high speed; lower values create a thinner forward corridor.").defineInRange("highSpeedSideRadius", 3, 1, 32);
        BACKWARD_RADIUS = builder.comment("Backward preload radius while moving. Higher values make sudden turn-backs less likely to outrun cached chunks.").defineInRange("backwardRadius", 8, 0, 32);
        HOT_CHUNK_CACHE_ENABLED = builder.comment("Keep recently visited/generated chunks hot in memory for a short time with non-persistent region tickets.").define("hotChunkCacheEnabled", true);
        HOT_CHUNK_CACHE_RADIUS = builder.comment("Radius around each player to keep hot when chunks are already loaded.").defineInRange("hotChunkCacheRadius", 4, 0, 16);
        HOT_CHUNK_CACHE_TTL_TICKS = builder.comment("Ticks to keep hot chunks loaded after they were last seen. 600 ticks is about 30 seconds.").defineInRange("hotChunkCacheTtlTicks", 600, 20, 6000);
        HOT_CHUNK_CACHE_MAX_CHUNKS = builder.comment("Maximum hot chunks kept in memory across all dimensions.").defineInRange("hotChunkCacheMaxChunks", 768, 0, 20000);
        HOT_CHUNK_TRAIL_PLAN_CHUNKS = builder.comment("Recently visited chunks prepended to the prediction plan so turn-backs prefer memory-hot chunks.").defineInRange("hotChunkTrailPlanChunks", 192, 0, 4096);
        MAX_PLAN_CHUNKS = builder.comment("Candidate cap while slow or medium speed.").defineInRange("maxPlanChunks", 1024, 64, 100000);
        HIGH_SPEED_PLAN_CHUNKS = builder.comment("Candidate cap at high speed; keeps long flights from flooding planning work.").defineInRange("highSpeedPlanChunks", 640, 64, 100000);
        CHUNKS_PER_TICK = builder.comment("Server chunk preload budget per tick per player.").defineInRange("chunksPerTick", 4, 1, 64);
        HIGH_SPEED_CHUNKS_PER_TICK = builder.comment("Maximum preload budget per tick per player at high speed.").defineInRange("highSpeedChunksPerTick", 16, 1, 256);
        GPU_PRECOMPUTE_CHUNKS_PER_TICK = builder.comment("GPU terrain preview computations per tick per player, independent from server chunk load requests.").defineInRange("gpuPrecomputeChunksPerTick", 128, 0, 2048);
        MAX_SYNC_CHUNKS_PER_SECOND = builder.comment("Global synchronous chunk generation budget per second. Lower values reduce server stalls.").defineInRange("maxSyncChunksPerSecond", 96, 1, 2000);
        OVERLOAD_TPS_THRESHOLD = builder.comment("Reduce preload pressure when measured server TPS drops below this value.").defineInRange("overloadTpsThreshold", 17.0D, 1.0D, 20.0D);
        SURFACE_PREVIEW_CHUNKS_PER_TICK = builder.comment("Seed-based preview computations per tick for normal ungenerated chunks.").defineInRange("surfacePreviewChunksPerTick", 48, 0, 2048);
        LOGIN_GPU_WARMUP_RADIUS = builder.comment("GPU preview radius around a player when they join or enter a world. Set to 0 to avoid join-time stalls.").defineInRange("loginGpuWarmupRadius", 0, 0, 64);
        LOGIN_GRACE_TICKS = builder.comment("Ticks after login before predictive synchronous chunk generation starts.").defineInRange("loginGraceTicks", 40, 0, 1200);
        LOGIN_RAMP_TICKS = builder.comment("Ticks after the grace period used to gradually ramp chunk generation budget.").defineInRange("loginRampTicks", 100, 0, 2400);
        STRUCTURE_CACHE_CHANGE_THRESHOLD = builder.comment("Dirty change score that marks a chunk as a large structure/snapshot candidate.").defineInRange("structureCacheChangeThreshold", 128, 1, 100000);
        SAMPLE_TICKS = builder.comment("Movement samples used for average speed.").defineInRange("sampleTicks", 60, 10, 400);
        VELOCITY_MEMORY_TICKS = builder.comment("Ticks to keep the last strong movement vector after a temporary stop.").defineInRange("velocityMemoryTicks", 80, 0, 400);
        VELOCITY_MEMORY_MIN_SPEED = builder.comment("Minimum speed remembered for prediction inertia.").defineInRange("velocityMemoryMinSpeed", 0.8D, 0.0D, 20.0D);
        HIGH_SPEED_BLOCKS_PER_TICK = builder.comment("Speed treated as high speed for shape stretching.").defineInRange("highSpeedBlocksPerTick", 1.7D, 0.1D, 20.0D);
        builder.pop();

        builder.push("cooperativeSecurity");
        CLIENT_ASSIST = builder.comment("Ask installed clients for hash assistance and validation.").define("clientAssist", true);
        CLIENT_ASSIST_PREVIEW_CHUNKS_PER_TICK = builder.comment("Preview chunks per server tick that can be offloaded to connected MapAccel clients. These are cache hints only; the server still performs official worldgen.").defineInRange("clientAssistPreviewChunksPerTick", 64, 0, 2048);
        CLIENT_ASSIST_MAX_BATCH_CHUNKS = builder.comment("Maximum preview chunks sent to one assisting client in a single packet.").defineInRange("clientAssistMaxBatchChunks", 64, 1, 256);
        CLIENT_ASSIST_MIN_FREE_MEMORY_MB = builder.comment("Minimum reported free memory for a client to receive preview-assist work.").defineInRange("clientAssistMinFreeMemoryMb", 1024, 0, 262144);
        CLIENT_ASSIST_MIN_FPS = builder.comment("Minimum reported FPS for a client to receive preview-assist work.").defineInRange("clientAssistMinFps", 45, 0, 1000);
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

        builder.push("client");
        STABILIZE_CREATIVE_FLIGHT_FOV = builder.comment("Keep FOV stable while creative flight speed mods repeatedly adjust flying speed.").define("stabilizeCreativeFlightFov", true);
        PATCH_EMBEDDIUM_DEPTH_COMPAT = builder.comment("Patch Embeddium/Rubidium client options that can spam GL_INVALID_OPERATION depth-format errors during aggressive chunk updates.").define("patchEmbeddiumDepthCompat", true);
        builder.pop();

        SPEC = builder.build();
    }

    private MapAccelConfig() {
    }
}
