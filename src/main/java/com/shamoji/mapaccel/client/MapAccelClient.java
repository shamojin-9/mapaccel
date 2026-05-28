package com.shamoji.mapaccel.client;

import net.minecraftforge.common.MinecraftForge;

public final class MapAccelClient {
    private MapAccelClient() {
    }

    public static void init() {
        EmbeddiumDepthCompat.patchIfNeeded();
        MinecraftForge.EVENT_BUS.register(new ClientMetricsReporter());
        MinecraftForge.EVENT_BUS.register(new CreativeFlightFovStabilizer());
    }
}
