package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.MapAccel;
import com.shamoji.mapaccel.cache.DirtyChunkTracker;
import com.shamoji.mapaccel.cache.RegionSnapshotCache;
import com.shamoji.mapaccel.config.MapAccelConfig;
import com.shamoji.mapaccel.gpu.GpuTerrainBackend;
import com.shamoji.mapaccel.net.MapAccelNetwork;
import com.shamoji.mapaccel.net.PreviewAssistRequestPacket;
import com.shamoji.mapaccel.preview.PreviewComputer;
import com.shamoji.mapaccel.preview.PreviewMode;
import com.shamoji.mapaccel.preview.SurfacePreview;
import com.shamoji.mapaccel.preview.SurfacePreviewService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MapAccelServer {
    private final MovementTracker movementTracker = new MovementTracker();
    private final PredictiveChunkPlanner planner = new PredictiveChunkPlanner();
    private GpuTerrainBackend gpuBackend;
    private final SurfacePreviewService previewService = new SurfacePreviewService();
    private final DirtyChunkTracker dirtyTracker = new DirtyChunkTracker();
    private final RegionSnapshotCache snapshotCache = new RegionSnapshotCache();
    private final HotChunkCache hotChunkCache = new HotChunkCache();
    private final Map<UUID, Integer> loginTicks = new ConcurrentHashMap<>();
    private int tickCursor;
    private int lastLogTick;
    private int plannedChunksWindow;
    private int requestedChunksWindow;
    private int estimatedDiskWriteKbWindow;
    private int skippedLoadedChunksWindow;
    private int gpuWarmupsWindow;
    private int surfacePreviewWindow;
    private int dirtyChunkWindow;
    private int snapshotHitWindow;
    private int previewAssistAppliedWindow;
    private int activePlayersWindow;
    private int syncBudgetRemaining;
    private int syncBudgetSecond;
    private int lastTickTimeTick;
    private long lastTickTimeNanos;
    private double estimatedTps = 20.0D;
    private double maxSpeedWindow;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MapAccelCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ServerLifecycleHooks.getCurrentServer() == null || ServerLifecycleHooks.getCurrentServer().getTickCount() % 2 != 0) {
            return;
        }
        List<ServerPlayer> players = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
        updateServerPacing(ServerLifecycleHooks.getCurrentServer().getTickCount());
        drainExternalRequests();
        activePlayersWindow = Math.max(activePlayersWindow, players.size());
        for (ServerPlayer player : players) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            int age = ticksSinceLogin(player);
            if (age < MapAccelConfig.LOGIN_GRACE_TICKS.get()) {
                movementTracker.update(player);
                continue;
            }
            MovementTracker.Prediction prediction = movementTracker.update(player);
            maxSpeedWindow = Math.max(maxSpeedWindow, prediction.speedBlocksPerTick());
            hotChunkCache.keepAround(level, player, ServerLifecycleHooks.getCurrentServer().getTickCount());
            List<ChunkPos> plan = mergeHotTrail(level, player, planner.plan(prediction));
            preload(level, player, players, prediction, plan, age);
        }
        hotChunkCache.cleanup(ServerLifecycleHooks.getCurrentServer(), ServerLifecycleHooks.getCurrentServer().getTickCount());
        tickCursor++;
        logSummaryIfDue(ServerLifecycleHooks.getCurrentServer().getTickCount());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        movementTracker.remove(event.getEntity().getUUID());
        MapAccelServerState.CLIENT_RESOURCES.remove(event.getEntity().getUUID());
        MapAccelServerState.RATE_LIMITER.remove(event.getEntity().getUUID());
        hotChunkCache.removePlayer(event.getEntity().getUUID());
        loginTicks.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            MapAccelServerState.TRUST.attach(player.getServer());
            loginTicks.put(player.getUUID(), player.getServer().getTickCount());
            warmupLoginArea(level, player.chunkPosition());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        dirtyTracker.onBreak(event);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        dirtyTracker.onPlace(event);
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        dirtyTracker.onExplosion(event);
    }

    private void preload(ServerLevel level, ServerPlayer player, List<ServerPlayer> onlinePlayers, MovementTracker.Prediction prediction, List<ChunkPos> plan, int ticksSinceLogin) {
        int budget = rampedBudget(adaptiveBudget(prediction), ticksSinceLogin);
        Set<Long> seen = new HashSet<>();
        int generated = 0;
        plannedChunksWindow += plan.size();
        gpuPrecompute(level, plan);
        requestPreviewAssist(level, onlinePlayers, plan);
        previewAndClassify(level, plan);
        for (ChunkPos pos : plan) {
            if (!seen.add(pos.toLong())) {
                continue;
            }
            if (generated >= budget) {
                break;
            }
            if (syncBudgetRemaining <= 0) {
                break;
            }
            if (level.hasChunk(pos.x, pos.z)) {
                skippedLoadedChunksWindow++;
                if (hotChunkCache.isHot(level, pos)) {
                    hotChunkCache.countHotHit();
                }
                hotChunkCache.touchIfLoaded(level, pos, ServerLifecycleHooks.getCurrentServer().getTickCount());
                if (dirtyTracker.isDirty(level, pos)) {
                    SurfacePreview preview = previewService.preview(level, pos);
                    snapshotCache.rememberLoadedChunk(level, pos, preview.hash(), true);
                }
                continue;
            }
            level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            generated++;
            requestedChunksWindow++;
            estimatedDiskWriteKbWindow += 64;
            syncBudgetRemaining--;
            hotChunkCache.touchIfLoaded(level, pos, ServerLifecycleHooks.getCurrentServer().getTickCount());
            if ((tickCursor + generated) % 5 == 0) {
                MapAccelServerState.VALIDATION.maybeChallenge(level, player, onlinePlayers, pos);
            }
        }
        if (generated > 0 && level.getGameTime() % 200 == 0) {
            MapAccel.LOGGER.debug("Preloaded {} predicted chunks for {}", generated, player.getGameProfile().getName());
        }
    }

    private int ticksSinceLogin(ServerPlayer player) {
        int now = player.getServer().getTickCount();
        int joinedAt = loginTicks.computeIfAbsent(player.getUUID(), id -> now);
        return Math.max(0, now - joinedAt);
    }

    private int rampedBudget(int budget, int ticksSinceLogin) {
        int grace = MapAccelConfig.LOGIN_GRACE_TICKS.get();
        int ramp = MapAccelConfig.LOGIN_RAMP_TICKS.get();
        if (ticksSinceLogin < grace) {
            return 0;
        }
        if (ramp <= 0) {
            return budget;
        }
        double ratio = Math.min(1.0D, (ticksSinceLogin - grace) / (double) ramp);
        return Math.max(1, (int) Math.ceil(budget * ratio));
    }

    private List<ChunkPos> mergeHotTrail(ServerLevel level, ServerPlayer player, List<ChunkPos> planned) {
        List<ChunkPos> trail = hotChunkCache.recentTrail(level, player.getUUID(), MapAccelConfig.HOT_CHUNK_TRAIL_PLAN_CHUNKS.get());
        if (trail.isEmpty()) {
            return planned;
        }
        ArrayList<ChunkPos> merged = new ArrayList<>(trail.size() + planned.size());
        Set<Long> seen = new HashSet<>();
        for (ChunkPos pos : trail) {
            if (seen.add(pos.toLong())) {
                merged.add(pos);
            }
        }
        for (ChunkPos pos : planned) {
            if (seen.add(pos.toLong())) {
                merged.add(pos);
            }
        }
        return merged;
    }

    private void previewAndClassify(ServerLevel level, List<ChunkPos> plan) {
        int budget = pressureBudget(MapAccelConfig.SURFACE_PREVIEW_CHUNKS_PER_TICK.get(), 16);
        if (budget <= 0) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        int previews = 0;
        for (ChunkPos pos : plan) {
            if (!seen.add(pos.toLong())) {
                continue;
            }
            boolean dirty = dirtyTracker.isDirty(level, pos);
            if (dirty) {
                dirtyChunkWindow++;
                if (snapshotCache.hasSnapshot(level, pos)) {
                    snapshotHitWindow++;
                }
                continue;
            }
            if (previews >= budget) {
                break;
            }
            SurfacePreview assisted = MapAccelServerState.PREVIEW_ASSIST.take(level, pos);
            if (assisted != null) {
                previewService.remember(level, assisted);
                surfacePreviewWindow++;
                previewAssistAppliedWindow++;
                previews++;
                continue;
            }
            previewService.preview(level, pos);
            surfacePreviewWindow++;
            previews++;
        }
    }

    private void requestPreviewAssist(ServerLevel level, List<ServerPlayer> onlinePlayers, List<ChunkPos> plan) {
        if (!MapAccelConfig.CLIENT_ASSIST.get()) {
            return;
        }
        int budget = pressureBudget(MapAccelConfig.CLIENT_ASSIST_PREVIEW_CHUNKS_PER_TICK.get(), 0);
        if (budget <= 0 || plan.isEmpty()) {
            return;
        }
        List<ServerPlayer> assistants = MapAccelServerState.CLIENT_RESOURCES.rankedAssistClients(
                onlinePlayers,
                ServerLifecycleHooks.getCurrentServer().getTickCount(),
                MapAccelConfig.CLIENT_ASSIST_MIN_FREE_MEMORY_MB.get(),
                MapAccelConfig.CLIENT_ASSIST_MIN_FPS.get()
        );
        if (assistants.isEmpty()) {
            return;
        }

        Set<Long> seen = new HashSet<>();
        ArrayList<ChunkPos> candidates = new ArrayList<>();
        for (ChunkPos pos : plan) {
            if (candidates.size() >= budget) {
                break;
            }
            if (!seen.add(pos.toLong()) || level.hasChunk(pos.x, pos.z)) {
                continue;
            }
            candidates.add(pos);
        }
        if (candidates.isEmpty()) {
            return;
        }

        int maxBatch = MapAccelConfig.CLIENT_ASSIST_MAX_BATCH_CHUNKS.get();
        int offset = 0;
        int assistantIndex = 0;
        PreviewMode mode = PreviewComputer.modeFor(level.dimension().location());
        while (offset < candidates.size()) {
            int count = Math.min(maxBatch, candidates.size() - offset);
            int[] chunkXs = new int[count];
            int[] chunkZs = new int[count];
            for (int i = 0; i < count; i++) {
                ChunkPos pos = candidates.get(offset + i);
                chunkXs[i] = pos.x;
                chunkZs[i] = pos.z;
            }
            long requestId = MapAccelServerState.PREVIEW_ASSIST.nextRequestId();
            PreviewAssistRequestPacket packet = new PreviewAssistRequestPacket(
                    requestId,
                    level.dimension().location().toString(),
                    level.getSeed(),
                    mode.name(),
                    chunkXs,
                    chunkZs
            );
            ServerPlayer assistant = assistants.get(assistantIndex % assistants.size());
            MapAccelNetwork.send(PacketDistributor.PLAYER.with(() -> assistant), packet);
            MapAccelServerState.PREVIEW_ASSIST.requested(
                    requestId,
                    assistant.getUUID(),
                    level.dimension().location(),
                    level.getSeed(),
                    mode,
                    chunkXs,
                    chunkZs,
                    ServerLifecycleHooks.getCurrentServer().getTickCount()
            );
            offset += count;
            assistantIndex++;
        }
    }

    private void gpuPrecompute(ServerLevel level, List<ChunkPos> plan) {
        GpuTerrainBackend backend = gpuBackend();
        if (!backend.isAvailable()) {
            return;
        }
        int budget = pressureBudget(MapAccelConfig.GPU_PRECOMPUTE_CHUNKS_PER_TICK.get(), 32);
        if (budget <= 0) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        int computed = 0;
        java.util.ArrayList<ChunkPos> batch = new java.util.ArrayList<>();
        for (ChunkPos pos : plan) {
            if (!seen.add(pos.toLong())) {
                continue;
            }
            if (computed >= budget) {
                break;
            }
            batch.add(pos);
            computed++;
        }
        backend.warmupBatch(level, batch);
        gpuWarmupsWindow += batch.size();
    }

    private void warmupLoginArea(ServerLevel level, ChunkPos center) {
        GpuTerrainBackend backend = gpuBackend();
        if (!backend.isAvailable()) {
            return;
        }
        int radius = MapAccelConfig.LOGIN_GPU_WARMUP_RADIUS.get();
        if (radius <= 0) {
            return;
        }
        ArrayList<ChunkPos> batch = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= radiusSquared) {
                    batch.add(new ChunkPos(center.x + dx, center.z + dz));
                }
            }
        }
        backend.warmupBatch(level, batch);
        gpuWarmupsWindow += batch.size();
        MapAccel.LOGGER.info("MapAccel login GPU warmup: chunks={} center={},{} backend={}", batch.size(), center.x, center.z, backend.name());
    }

    private void drainExternalRequests() {
        int budget = Math.min(syncBudgetRemaining, pressureBudget(MapAccelConfig.API_CHUNKS_PER_TICK.get(), 1));
        ExternalLoadRequestQueue.DrainStats stats = ExternalLoadRequestQueue.drain(ServerLifecycleHooks.getCurrentServer(), budget);
        if (stats.loadedChunks() <= 0) {
            return;
        }
        requestedChunksWindow += stats.loadedChunks();
        skippedLoadedChunksWindow += stats.skippedChunks();
        estimatedDiskWriteKbWindow += stats.loadedChunks() * 64;
        syncBudgetRemaining = Math.max(0, syncBudgetRemaining - stats.loadedChunks());
    }

    private int adaptiveBudget(MovementTracker.Prediction prediction) {
        int base = MapAccelConfig.CHUNKS_PER_TICK.get();
        int high = Math.max(base, MapAccelConfig.HIGH_SPEED_CHUNKS_PER_TICK.get());
        double ratio = Math.min(1.0D, prediction.speedBlocksPerTick() / MapAccelConfig.HIGH_SPEED_BLOCKS_PER_TICK.get());
        int budget = base + (int) Math.round((high - base) * ratio);
        if (estimatedTps < MapAccelConfig.OVERLOAD_TPS_THRESHOLD.get()) {
            double pressure = Math.max(0.25D, estimatedTps / MapAccelConfig.OVERLOAD_TPS_THRESHOLD.get());
            budget = Math.max(1, (int) Math.floor(budget * pressure));
        }
        return budget;
    }

    private int pressureBudget(int configuredBudget, int minimumWhenOverloaded) {
        if (estimatedTps >= MapAccelConfig.OVERLOAD_TPS_THRESHOLD.get()) {
            return configuredBudget;
        }
        double pressure = Math.max(0.10D, estimatedTps / MapAccelConfig.OVERLOAD_TPS_THRESHOLD.get());
        return Math.max(minimumWhenOverloaded, (int) Math.floor(configuredBudget * pressure));
    }

    private void updateServerPacing(int serverTick) {
        long now = System.nanoTime();
        if (lastTickTimeNanos != 0L) {
            long elapsedNanos = Math.max(1L, now - lastTickTimeNanos);
            double instantTps = 1_000_000_000.0D / elapsedNanos;
            estimatedTps = estimatedTps * 0.90D + Math.min(20.0D, instantTps) * 0.10D;
        }
        lastTickTimeNanos = now;

        int currentSecond = serverTick / 20;
        if (currentSecond != syncBudgetSecond) {
            syncBudgetSecond = currentSecond;
            syncBudgetRemaining = MapAccelConfig.MAX_SYNC_CHUNKS_PER_SECOND.get();
            if (estimatedTps < MapAccelConfig.OVERLOAD_TPS_THRESHOLD.get()) {
                double pressure = Math.max(0.25D, estimatedTps / MapAccelConfig.OVERLOAD_TPS_THRESHOLD.get());
                syncBudgetRemaining = Math.max(1, (int) Math.floor(syncBudgetRemaining * pressure));
            }
        }
        lastTickTimeTick = serverTick;
    }

    private void logSummaryIfDue(int serverTick) {
        int intervalTicks = MapAccelConfig.LOG_INTERVAL_SECONDS.get() * 20;
        if (serverTick - lastLogTick < intervalTicks) {
            return;
        }
        int elapsedTicks = Math.max(1, serverTick - lastLogTick);
        double elapsedSeconds = elapsedTicks / 20.0D;
        if (MapAccelConfig.LOG_CHUNK_REQUESTS.get()) {
            GpuTerrainBackend backend = gpuBackend();
            GpuTerrainBackend.GpuStats gpuStats = backend.snapshotAndReset();
            DirtyChunkTracker.DirtySummary dirtySummary = dirtyTracker.summary();
            PreviewAssistLedger.Stats assistStats = MapAccelServerState.PREVIEW_ASSIST.snapshotAndReset();
            HotChunkCache.Stats hotStats = hotChunkCache.snapshotAndReset();
            MapAccel.LOGGER.info(
                    "MapAccel chunk requests: planned={} requested={} skippedLoaded={} requestedPerSec={} estimatedDiskWriteKb={} maxSpeedBpt={} estimatedTps={} syncBudgetLeft={} surfacePreviews={} previewCache={} previewAssistRequested={} previewAssistReceived={} previewAssistAccepted={} previewAssistApplied={} previewAssistCache={} hotPinned={} hotRefreshed={} hotReleased={} hotHits={} hotRetained={} dirtySeen={} dirtyTotal={} largeDirty={} snapshots={} snapshotHits={} gpuWarmups={} gpuBackend={} gpuComputeRequests={} gpuComputed={} gpuComputeMs={} activePlayers={} gpuDetail={}",
                    plannedChunksWindow,
                    requestedChunksWindow,
                    skippedLoadedChunksWindow,
                    String.format("%.2f", requestedChunksWindow / elapsedSeconds),
                    estimatedDiskWriteKbWindow,
                    String.format("%.2f", maxSpeedWindow),
                    String.format("%.2f", estimatedTps),
                    syncBudgetRemaining,
                    surfacePreviewWindow,
                    previewService.size(),
                    assistStats.requestedChunks(),
                    assistStats.receivedChunks(),
                    assistStats.acceptedChunks(),
                    previewAssistAppliedWindow,
                    assistStats.cachedChunks(),
                    hotStats.pinned(),
                    hotStats.refreshed(),
                    hotStats.released(),
                    hotStats.hotHits(),
                    hotStats.retained(),
                    dirtyChunkWindow,
                    dirtySummary.chunks(),
                    dirtySummary.largeChangeCandidates(),
                    snapshotCache.size(),
                    snapshotHitWindow,
                    gpuWarmupsWindow,
                    backend.name(),
                    gpuStats.computeRequests(),
                    gpuStats.computedChunks(),
                    String.format("%.2f", gpuStats.computeMillis()),
                    activePlayersWindow,
                    gpuStats.detail()
            );
        }
        if (MapAccelConfig.LOG_RESOURCE_SUMMARY.get()) {
            ClientResourceLedger.ResourceSummary summary = MapAccelServerState.CLIENT_RESOURCES.summary(serverTick);
            MapAccel.LOGGER.info(
                    "MapAccel client resources: clients={} fresh={} freeMemoryMb={} usedMemoryMb={} maxMemoryMb={} avgFps={} metricPackets={}",
                    summary.clients(),
                    summary.freshClients(),
                    summary.freeMemoryMb(),
                    summary.usedMemoryMb(),
                    summary.maxMemoryMb(),
                    summary.averageFps(),
                    summary.packets()
            );
        }
        lastLogTick = serverTick;
        plannedChunksWindow = 0;
        requestedChunksWindow = 0;
        estimatedDiskWriteKbWindow = 0;
        skippedLoadedChunksWindow = 0;
        gpuWarmupsWindow = 0;
        surfacePreviewWindow = 0;
        dirtyChunkWindow = 0;
        snapshotHitWindow = 0;
        previewAssistAppliedWindow = 0;
        activePlayersWindow = 0;
        maxSpeedWindow = 0.0D;
    }

    private GpuTerrainBackend gpuBackend() {
        if (gpuBackend == null) {
            gpuBackend = GpuTerrainBackend.create();
        }
        return gpuBackend;
    }
}
