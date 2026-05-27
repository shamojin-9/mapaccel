package com.shamoji.mapaccel.net;

import com.shamoji.mapaccel.MapAccel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MapAccelNetwork {
    private static final String PROTOCOL = "2";
    private static int packetId;
    private static SimpleChannel channel;

    private MapAccelNetwork() {
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            channel = NetworkRegistry.newSimpleChannel(
                    new ResourceLocation(MapAccel.MOD_ID, "main"),
                    () -> PROTOCOL,
                    PROTOCOL::equals,
                    PROTOCOL::equals
            );
            channel.registerMessage(packetId++, ClientMetricsPacket.class, ClientMetricsPacket::encode, ClientMetricsPacket::decode, ClientMetricsPacket::handle);
            channel.registerMessage(packetId++, HashChallengePacket.class, HashChallengePacket::encode, HashChallengePacket::decode, HashChallengePacket::handle);
            channel.registerMessage(packetId++, HashResultPacket.class, HashResultPacket::encode, HashResultPacket::decode, HashResultPacket::handle);
            channel.registerMessage(packetId++, PreviewAssistRequestPacket.class, PreviewAssistRequestPacket::encode, PreviewAssistRequestPacket::decode, PreviewAssistRequestPacket::handle);
            channel.registerMessage(packetId++, PreviewAssistResultPacket.class, PreviewAssistResultPacket::encode, PreviewAssistResultPacket::decode, PreviewAssistResultPacket::handle);
        });
    }

    public static void send(PacketDistributor.PacketTarget target, Object packet) {
        if (channel != null) {
            channel.send(target, packet);
        }
    }

    public static void sendToServer(Object packet) {
        if (channel != null) {
            channel.sendToServer(packet);
        }
    }
}
