package com.shamoji.mapaccel.net;

import com.shamoji.mapaccel.client.ClientPreviewWorker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PreviewAssistRequestPacket(long requestId, String dimension, long seed, String mode, int[] chunkXs, int[] chunkZs) {
    private static final int MAX_CHUNKS = 256;

    public static void encode(PreviewAssistRequestPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(packet.chunkXs.length, packet.chunkZs.length);
        buffer.writeLong(packet.requestId);
        buffer.writeUtf(packet.dimension);
        buffer.writeLong(packet.seed);
        buffer.writeUtf(packet.mode);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buffer.writeInt(packet.chunkXs[i]);
            buffer.writeInt(packet.chunkZs[i]);
        }
    }

    public static PreviewAssistRequestPacket decode(FriendlyByteBuf buffer) {
        long requestId = buffer.readLong();
        String dimension = buffer.readUtf();
        long seed = buffer.readLong();
        String mode = buffer.readUtf();
        int count = Math.min(MAX_CHUNKS, buffer.readVarInt());
        int[] chunkXs = new int[count];
        int[] chunkZs = new int[count];
        for (int i = 0; i < count; i++) {
            chunkXs[i] = buffer.readInt();
            chunkZs[i] = buffer.readInt();
        }
        return new PreviewAssistRequestPacket(requestId, dimension, seed, mode, chunkXs, chunkZs);
    }

    public static void handle(PreviewAssistRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientPreviewWorker.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
