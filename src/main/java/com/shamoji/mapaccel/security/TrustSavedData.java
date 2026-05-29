package com.shamoji.mapaccel.security;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrustSavedData extends SavedData {
    public static final String ID = "mapaccel_trust";
    private final Map<UUID, Integer> trust = new HashMap<>();
    private final Map<UUID, Integer> mismatches = new HashMap<>();

    public static TrustSavedData load(CompoundTag tag) {
        TrustSavedData data = new TrustSavedData();
        ListTag entries = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID playerId = entry.getUUID("uuid");
            data.trust.put(playerId, entry.getInt("trust"));
            data.mismatches.put(playerId, entry.getInt("mismatches"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Integer> entry : trust.entrySet()) {
            CompoundTag player = new CompoundTag();
            UUID playerId = entry.getKey();
            player.putUUID("uuid", playerId);
            player.putInt("trust", entry.getValue());
            player.putInt("mismatches", mismatches.getOrDefault(playerId, 0));
            entries.add(player);
        }
        for (Map.Entry<UUID, Integer> entry : mismatches.entrySet()) {
            if (trust.containsKey(entry.getKey())) {
                continue;
            }
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", entry.getKey());
            player.putInt("trust", 100);
            player.putInt("mismatches", entry.getValue());
            entries.add(player);
        }
        tag.put("players", entries);
        return tag;
    }

    public Map<UUID, Integer> trust() {
        return trust;
    }

    public Map<UUID, Integer> mismatches() {
        return mismatches;
    }
}
