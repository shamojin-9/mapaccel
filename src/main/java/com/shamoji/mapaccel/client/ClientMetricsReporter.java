package com.shamoji.mapaccel.client;

import com.shamoji.mapaccel.net.ClientMetricsPacket;
import com.shamoji.mapaccel.net.MapAccelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ClientMetricsReporter {
    private int ticks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        ticks++;
        if (ticks % 100 != 0) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        int freeMemoryMb = (int) ((runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / 1024L / 1024L);
        int maxMemoryMb = (int) (runtime.maxMemory() / 1024L / 1024L);
        int usedMemoryMb = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L);
        int fps = minecraft.getFps();
        MapAccelNetwork.sendToServer(new ClientMetricsPacket(freeMemoryMb, maxMemoryMb, usedMemoryMb, fps));
    }
}
