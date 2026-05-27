package com.shamoji.mapaccel.client;

import com.shamoji.mapaccel.net.MapAccelNetwork;
import com.shamoji.mapaccel.net.PreviewAssistRequestPacket;
import com.shamoji.mapaccel.net.PreviewAssistResultPacket;
import com.shamoji.mapaccel.preview.PreviewComputer;
import com.shamoji.mapaccel.preview.PreviewMode;
import com.shamoji.mapaccel.preview.SurfacePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public final class ClientPreviewWorker {
    private ClientPreviewWorker() {
    }

    public static void handle(PreviewAssistRequestPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().toString().equals(packet.dimension())) {
            sendEmpty(packet);
            return;
        }
        int count = Math.min(packet.chunkXs().length, packet.chunkZs().length);
        int[] minHeights = new int[count];
        int[] maxHeights = new int[count];
        int[] averageHeights = new int[count];
        int[] hashes = new int[count];
        ResourceLocation dimension = new ResourceLocation(packet.dimension());
        PreviewMode mode = safeMode(packet.mode(), dimension);
        for (int i = 0; i < count; i++) {
            SurfacePreview preview = PreviewComputer.compute(dimension, packet.seed(), new ChunkPos(packet.chunkXs()[i], packet.chunkZs()[i]), mode);
            minHeights[i] = preview.minHeight();
            maxHeights[i] = preview.maxHeight();
            averageHeights[i] = preview.averageHeight();
            hashes[i] = preview.hash();
        }
        MapAccelNetwork.sendToServer(new PreviewAssistResultPacket(
                packet.requestId(),
                packet.dimension(),
                packet.seed(),
                mode.name(),
                packet.chunkXs(),
                packet.chunkZs(),
                minHeights,
                maxHeights,
                averageHeights,
                hashes
        ));
    }

    private static void sendEmpty(PreviewAssistRequestPacket packet) {
        MapAccelNetwork.sendToServer(new PreviewAssistResultPacket(
                packet.requestId(),
                packet.dimension(),
                packet.seed(),
                packet.mode(),
                new int[0],
                new int[0],
                new int[0],
                new int[0],
                new int[0],
                new int[0]
        ));
    }

    private static PreviewMode safeMode(String value, ResourceLocation dimension) {
        try {
            return PreviewMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PreviewComputer.modeFor(dimension);
        }
    }
}
