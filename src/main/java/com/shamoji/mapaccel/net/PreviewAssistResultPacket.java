package com.shamoji.mapaccel.net;

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
        buffer.writeUtf(packet.dimension);
        buffer.writeLong(packet.seed);
        buffer.writeUtf(packet.mode);
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
        String dimension = buffer.readUtf();
        long seed = buffer.readLong();
        String mode = buffer.readUtf();
        int count = Math.min(MAX_CHUNKS, buffer.readVarInt());
        int[] chunkXs = new int[count];
        int[] chunkZs = new int[count];
        int[] minHeights = new int[count];
        int[] maxHeights = new int[count];
        int[] averageHeights = new int[count];
        int[] hashes = new int[count];
        for (int i = 0; i < count; i++) {
            chunkXs[i] = buffer.readInt();
            chunkZs[i] = buffer.readInt();
            minHeights[i] = buffer.readInt();
            maxHeights[i] = buffer.readInt();
            averageHeights[i] = buffer.readInt();
            hashes[i] = buffer.readInt();
        }
        return new PreviewAssistResultPacket(requestId, dimension, seed, mode, chunkXs, chunkZs, minHeights, maxHeights, averageHeights, hashes);
    }

    public static void handle(PreviewAssistResultPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                MapAccelServerState.PREVIEW_ASSIST.accept(ctx.getSender().getUUID(), packet);
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
