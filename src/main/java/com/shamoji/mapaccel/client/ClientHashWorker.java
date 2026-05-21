package com.shamoji.mapaccel.client;

import com.shamoji.mapaccel.net.HashChallengePacket;
import com.shamoji.mapaccel.net.HashResultPacket;
import com.shamoji.mapaccel.net.MapAccelNetwork;
import com.shamoji.mapaccel.world.ChunkHasher;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ClientHashWorker {
    private ClientHashWorker() {
    }

    public static void handle(HashChallengePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().toString().equals(packet.dimension())) {
            MapAccelNetwork.sendToServer(new HashResultPacket(packet.challengeId(), "", false));
            return;
        }
        ChunkPos pos = new ChunkPos(packet.chunkX(), packet.chunkZ());
        if (!minecraft.level.hasChunk(pos.x, pos.z)) {
            MapAccelNetwork.sendToServer(new HashResultPacket(packet.challengeId(), "", false));
            return;
        }
        LevelChunk chunk = minecraft.level.getChunk(pos.x, pos.z);
        String hash = ChunkHasher.hashClientChunk(minecraft.level, chunk, pos);
        MapAccelNetwork.sendToServer(new HashResultPacket(packet.challengeId(), hash, true));
    }
}
