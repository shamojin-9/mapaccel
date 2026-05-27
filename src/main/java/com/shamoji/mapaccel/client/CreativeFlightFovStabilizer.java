package com.shamoji.mapaccel.client;

import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CreativeFlightFovStabilizer {
    @SubscribeEvent
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!MapAccelConfig.STABILIZE_CREATIVE_FLIGHT_FOV.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.getAbilities().flying || !player.getAbilities().mayfly) {
            return;
        }
        event.setFOV(minecraft.options.fov().get());
    }
}
