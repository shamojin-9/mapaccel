package com.shamoji.mapaccel.net;

import com.shamoji.mapaccel.server.MapAccelServerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HashResultPacket(long challengeId, String hash, boolean available) {
    public static void encode(HashResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.challengeId);
        buffer.writeUtf(packet.hash);
        buffer.writeBoolean(packet.available);
    }

    public static HashResultPacket decode(FriendlyByteBuf buffer) {
        return new HashResultPacket(buffer.readLong(), buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(HashResultPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                MapAccelServerState.VALIDATION.acceptHash(ctx.getSender().getServer(), ctx.getSender().getUUID(), packet.challengeId, packet.hash, packet.available);
            }
        });
        ctx.setPacketHandled(true);
    }
}
