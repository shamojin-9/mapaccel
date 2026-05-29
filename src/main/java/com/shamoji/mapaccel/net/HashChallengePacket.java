package com.shamoji.mapaccel.net;

import com.shamoji.mapaccel.client.ClientHashWorker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HashChallengePacket(long challengeId, String dimension, int chunkX, int chunkZ) {
    public static void encode(HashChallengePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.challengeId);
        buffer.writeUtf(packet.dimension, 128);
        buffer.writeInt(packet.chunkX);
        buffer.writeInt(packet.chunkZ);
    }

    public static HashChallengePacket decode(FriendlyByteBuf buffer) {
        return new HashChallengePacket(buffer.readLong(), buffer.readUtf(128), buffer.readInt(), buffer.readInt());
    }

    public static void handle(HashChallengePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientHashWorker.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
