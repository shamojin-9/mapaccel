package com.shamoji.mapaccel.preview;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public record SurfacePreview(
        ResourceLocation dimension,
        ChunkPos chunkPos,
        PreviewMode mode,
        int minHeight,
        int maxHeight,
        int averageHeight,
        int hash
) {
}
