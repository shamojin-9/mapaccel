package com.shamoji.mapaccel.server;

import com.mojang.brigadier.CommandDispatcher;
import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MapAccelCommands {
    private MapAccelCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mapaccel")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("trust")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    MapAccelServerState.TRUST.attach(player.getServer());
                                    int trust = MapAccelServerState.TRUST.trustOf(player.getUUID());
                                    int mismatches = MapAccelServerState.TRUST.mismatchCount(player.getUUID());
                                    ClientResourceLedger.ClientResource resource = MapAccelServerState.CLIENT_RESOURCES.get(player.getUUID());
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "MapAccel " + player.getGameProfile().getName()
                                                    + " trust=" + trust
                                                    + " mismatches=" + mismatches
                                                    + " freeMemoryMb=" + resource.freeMemoryMb()
                                                    + " usedMemoryMb=" + resource.usedMemoryMb()
                                                    + " fps=" + resource.fpsEstimate()
                                    ), false);
                                    return 1;
                                })))
                .then(Commands.literal("config")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "MapAccel radius=" + MapAccelConfig.MIN_RADIUS.get()
                                            + " forward=" + MapAccelConfig.MAX_FORWARD_RADIUS.get()
                                            + " budget=" + MapAccelConfig.CHUNKS_PER_TICK.get()
                                            + " apiBudget=" + MapAccelConfig.API_CHUNKS_PER_TICK.get()
                                            + " apiRadius=" + MapAccelConfig.API_LOAD_RADIUS.get()
                                            + " validators=" + MapAccelConfig.VALIDATOR_COUNT.get()
                                            + " autoBan=" + MapAccelConfig.AUTO_BAN.get()
                                            + " remoteWorkers=" + MapAccelConfig.REMOTE_WORKER_ENABLED.get()
                            ), false);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "MapAccel pendingApiChunks=" + ExternalLoadRequestQueue.pendingRequests()
                                            + " activeApiRequesters=" + ExternalLoadRequestQueue.activeRequesters()
                                            + " remoteWorkerUrl=" + RemoteWorkerGateway.workerUrlHint()
                                            + " clientResourceReports=" + MapAccelServerState.CLIENT_RESOURCES.summary(0).clients()
                            ), false);
                            return 1;
                        })));
    }
}
