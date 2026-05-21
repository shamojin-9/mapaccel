package com.shamoji.mapaccel;

import com.shamoji.mapaccel.client.MapAccelClient;
import com.shamoji.mapaccel.config.MapAccelConfig;
import com.shamoji.mapaccel.net.MapAccelNetwork;
import com.shamoji.mapaccel.server.MapAccelServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MapAccel.MOD_ID)
public final class MapAccel {
    public static final String MOD_ID = "mapaccel";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MapAccel() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MapAccelConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(MapAccelNetwork::setup);
        MinecraftForge.EVENT_BUS.register(new MapAccelServer());
        DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> MapAccelClient::init);
    }
}
