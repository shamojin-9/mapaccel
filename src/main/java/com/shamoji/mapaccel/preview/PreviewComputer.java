package com.shamoji.mapaccel.preview;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public final class PreviewComputer {
    private PreviewComputer() {
    }

    public static SurfacePreview compute(ResourceLocation dimension, long seed, ChunkPos pos) {
        return compute(dimension, seed, pos, modeFor(dimension));
    }

    public static SurfacePreview compute(ResourceLocation dimension, long seed, ChunkPos pos, PreviewMode mode) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int total = 0;
        int hash = 1;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = (pos.x << 4) + x;
                int worldZ = (pos.z << 4) + z;
                int height = previewHeight(seed, worldX, worldZ, mode);
                min = Math.min(min, height);
                max = Math.max(max, height);
                total += height;
                hash = 31 * hash + height;
            }
        }
        return new SurfacePreview(dimension, pos, mode, min, max, total / 256, hash);
    }

    public static PreviewMode modeFor(ResourceLocation dimension) {
        if ("the_nether".equals(dimension.getPath())) {
            return PreviewMode.SHELL;
        }
        if ("the_end".equals(dimension.getPath())) {
            return PreviewMode.ISLAND;
        }
        return PreviewMode.SURFACE;
    }

    private static int previewHeight(long seed, int x, int z, PreviewMode mode) {
        long mixed = seed ^ (x * 341873128712L) ^ (z * 132897987541L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        int noise = (int) (mixed & 63L);
        return switch (mode) {
            case SURFACE -> 52 + noise;
            case SHELL -> 32 + noise;
            case ISLAND -> 48 + noise / 2;
        };
    }
}
