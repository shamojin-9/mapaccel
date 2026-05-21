package com.shamoji.mapaccel.net;

import com.shamoji.mapaccel.server.MapAccelServerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientMetricsPacket(int freeMemoryMb, int maxMemoryMb, int usedMemoryMb, int fpsEstimate) {
    public static void encode(ClientMetricsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.freeMemoryMb);
        buffer.writeVarInt(packet.maxMemoryMb);
        buffer.writeVarInt(packet.usedMemoryMb);
        buffer.writeVarInt(packet.fpsEstimate);
    }

    public static ClientMetricsPacket decode(FriendlyByteBuf buffer) {
        return new ClientMetricsPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(ClientMetricsPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                MapAccelServerState.CLIENT_RESOURCES.update(ctx.getSender().getUUID(), packet.freeMemoryMb, packet.maxMemoryMb, packet.usedMemoryMb, packet.fpsEstimate, ctx.getSender().getServer().getTickCount());
            }
        });
        ctx.setPacketHandled(true);
    }
}
