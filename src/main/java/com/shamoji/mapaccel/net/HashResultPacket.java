package com.shamoji.mapaccel.net;

import com.shamoji.mapaccel.config.MapAccelConfig;
import com.shamoji.mapaccel.security.InboundRateLimiter;
import com.shamoji.mapaccel.server.MapAccelServerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HashResultPacket(long challengeId, String hash, boolean available) {
    public static void encode(HashResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.challengeId);
        buffer.writeUtf(packet.hash, 128);
        buffer.writeBoolean(packet.available);
    }

    public static HashResultPacket decode(FriendlyByteBuf buffer) {
        return new HashResultPacket(buffer.readLong(), buffer.readUtf(128), buffer.readBoolean());
    }

    public static void handle(HashResultPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                int serverTick = ctx.getSender().getServer().getTickCount();
                if (MapAccelServerState.RATE_LIMITER.allow(ctx.getSender().getUUID(), InboundRateLimiter.Bucket.HASH_RESULT, serverTick, MapAccelConfig.HASH_RESULT_MIN_INTERVAL_TICKS.get())) {
                    MapAccelServerState.VALIDATION.acceptHash(ctx.getSender().getServer(), ctx.getSender().getUUID(), packet.challengeId, packet.hash, packet.available);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
