package com.shamoji.mapaccel.client;

import com.shamoji.mapaccel.MapAccel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MapAccel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MapAccelClient {
    private MapAccelClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EmbeddiumDepthCompat.patchIfNeeded();
            MinecraftForge.EVENT_BUS.register(new ClientMetricsReporter());
            MinecraftForge.EVENT_BUS.register(new CreativeFlightFovStabilizer());
        });
    }
}
