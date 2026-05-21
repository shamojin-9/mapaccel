package com.shamoji.mapaccel.client;

import net.minecraftforge.common.MinecraftForge;

public final class MapAccelClient {
    private MapAccelClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ClientMetricsReporter());
    }
}
