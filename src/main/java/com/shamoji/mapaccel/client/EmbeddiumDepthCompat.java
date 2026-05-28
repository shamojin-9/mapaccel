package com.shamoji.mapaccel.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.shamoji.mapaccel.MapAccel;
import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EmbeddiumDepthCompat {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EmbeddiumDepthCompat() {
    }

    public static void patchIfNeeded() {
        if (!MapAccelConfig.PATCH_EMBEDDIUM_DEPTH_COMPAT.get()) {
            return;
        }
        if (!ModList.get().isLoaded("embeddium") && !ModList.get().isLoaded("rubidium")) {
            return;
        }

        Path path = FMLPaths.CONFIGDIR.get().resolve("embeddium-options.json");
        try {
            JsonObject root = readOrCreate(path);
            JsonObject performance = getOrCreateObject(root, "performance");
            boolean changed = setFalse(performance, "use_translucent_face_sorting_v2");
            changed |= setFalse(performance, "always_defer_chunk_updates_v2");

            if (changed) {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, writer);
                }
                MapAccel.LOGGER.warn("MapAccel patched Embeddium options to avoid depth-format OpenGL spam. Restart the client if Embeddium already loaded its config.");
            }
        } catch (Exception ex) {
            MapAccel.LOGGER.warn("MapAccel could not patch Embeddium depth compatibility options", ex);
        }
    }

    private static JsonObject readOrCreate(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }
    }

    private static JsonObject getOrCreateObject(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonObject()) {
            return root.getAsJsonObject(key);
        }
        JsonObject object = new JsonObject();
        root.add(key, object);
        return object;
    }

    private static boolean setFalse(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            JsonPrimitive primitive = object.getAsJsonPrimitive(key);
            if (primitive.isBoolean() && !primitive.getAsBoolean()) {
                return false;
            }
        }
        object.addProperty(key, false);
        return true;
    }
}
