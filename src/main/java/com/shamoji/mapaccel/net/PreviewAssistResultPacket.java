package com.shamoji.mapaccel.net;

import com.shamoji.mapaccel.config.MapAccelConfig;
import com.shamoji.mapaccel.security.InboundRateLimiter;
import com.shamoji.mapaccel.server.MapAccelServerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PreviewAssistResultPacket(
        long requestId,
        String dimension,
        long seed,
        String mode,
        int[] chunkXs,
        int[] chunkZs,
        int[] minHeights,
        int[] maxHeights,
        int[] averageHeights,
        int[] hashes
) {
    private static final int MAX_CHUNKS = 256;

    public static void encode(PreviewAssistResultPacket packet, FriendlyByteBuf buffer) {
        int count = packet.count();
        buffer.writeLong(packet.requestId);
        buffer.writeUtf(packet.dimension, 128);
        buffer.writeLong(packet.seed);
        buffer.writeUtf(packet.mode, 64);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buffer.writeInt(packet.chunkXs[i]);
            buffer.writeInt(packet.chunkZs[i]);
            buffer.writeInt(packet.minHeights[i]);
            buffer.writeInt(packet.maxHeights[i]);
            buffer.writeInt(packet.averageHeights[i]);
            buffer.writeInt(packet.hashes[i]);
        }
    }

    public static PreviewAssistResultPacket decode(FriendlyByteBuf buffer) {
        long requestId = buffer.readLong();
        String dimension = buffer.readUtf(128);
        long seed = buffer.readLong();
        String mode = buffer.readUtf(64);
        int encodedCount = Math.max(0, buffer.readVarInt());
        int count = Math.min(MAX_CHUNKS, encodedCount);
        int[] chunkXs = new int[count];
        int[] chunkZs = new int[count];
        int[] minHeights = new int[count];
        int[] maxHeights = new int[count];
        int[] averageHeights = new int[count];
        int[] hashes = new int[count];
        for (int i = 0; i < encodedCount; i++) {
            int chunkX = buffer.readInt();
            int chunkZ = buffer.readInt();
            int minHeight = buffer.readInt();
            int maxHeight = buffer.readInt();
            int averageHeight = buffer.readInt();
            int hash = buffer.readInt();
            if (i < count) {
                chunkXs[i] = chunkX;
                chunkZs[i] = chunkZ;
                minHeights[i] = minHeight;
                maxHeights[i] = maxHeight;
                averageHeights[i] = averageHeight;
                hashes[i] = hash;
            }
        }
        return new PreviewAssistResultPacket(requestId, dimension, seed, mode, chunkXs, chunkZs, minHeights, maxHeights, averageHeights, hashes);
    }

    public static void handle(PreviewAssistResultPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                int serverTick = ctx.getSender().getServer().getTickCount();
                if (MapAccelServerState.RATE_LIMITER.allow(ctx.getSender().getUUID(), InboundRateLimiter.Bucket.PREVIEW_RESULT, serverTick, MapAccelConfig.PREVIEW_RESULT_MIN_INTERVAL_TICKS.get())) {
                    MapAccelServerState.PREVIEW_ASSIST.accept(ctx.getSender().getUUID(), packet, serverTick);
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    public int count() {
        return Math.min(
                Math.min(chunkXs.length, chunkZs.length),
                Math.min(Math.min(minHeights.length, maxHeights.length), Math.min(averageHeights.length, hashes.length))
        );
    }
}
