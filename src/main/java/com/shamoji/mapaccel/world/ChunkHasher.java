package com.shamoji.mapaccel.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChunkHasher {
    private ChunkHasher() {
    }

    public static String hashServerChunk(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        return hashChunk(level, chunk, pos);
    }

    public static String hashClientChunk(Level level, LevelChunk chunk, ChunkPos pos) {
        return hashChunk(level, chunk, pos);
    }

    private static String hashChunk(Level level, LevelChunk chunk, ChunkPos pos) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, level.dimension().location().toString());
            update(digest, pos.x + "," + pos.z);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight();
            for (int y = minY; y < maxY; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        cursor.set((pos.x << 4) + x, y, (pos.z << 4) + z);
                        BlockState state = chunk.getBlockState(cursor);
                        update(digest, state.toString());
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
