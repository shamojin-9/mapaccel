package com.shamoji.mapaccel.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shamoji.mapaccel.MapAccel;
import com.shamoji.mapaccel.config.MapAccelConfig;
import com.shamoji.mapaccel.net.PreviewAssistResultPacket;
import com.shamoji.mapaccel.preview.PreviewMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class RemoteWorkerGateway {
    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();
    private static final ArrayDeque<PreviewTask> TASKS = new ArrayDeque<>();
    private static HttpServer httpServer;
    private static MinecraftServer minecraftServer;
    private static String accessToken;
    private static int completedWindow;
    private static int rejectedWindow;

    private RemoteWorkerGateway() {
    }

    public static void ensureStarted(MinecraftServer server) {
        if (!MapAccelConfig.REMOTE_WORKER_ENABLED.get()) {
            stop();
            return;
        }
        if (httpServer != null) {
            minecraftServer = server;
            return;
        }
        minecraftServer = server;
        try {
            accessToken = configuredOrGeneratedToken();
            InetSocketAddress address = new InetSocketAddress(MapAccelConfig.REMOTE_WORKER_BIND_ADDRESS.get(), MapAccelConfig.REMOTE_WORKER_PORT.get());
            httpServer = HttpServer.create(address, 0);
            httpServer.createContext("/", RemoteWorkerGateway::handleIndex);
            httpServer.createContext("/task", RemoteWorkerGateway::handleTask);
            httpServer.createContext("/result", RemoteWorkerGateway::handleResult);
            httpServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "MapAccel Remote Worker");
                thread.setDaemon(true);
                return thread;
            }));
            httpServer.start();
            MapAccel.LOGGER.info("MapAccel remote worker gateway listening on http://{}:{}/?token={}", MapAccelConfig.REMOTE_WORKER_BIND_ADDRESS.get(), MapAccelConfig.REMOTE_WORKER_PORT.get(), accessToken);
        } catch (IOException | RuntimeException ex) {
            httpServer = null;
            MapAccel.LOGGER.warn("Failed to start MapAccel remote worker gateway", ex);
        }
    }

    public static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        minecraftServer = null;
        synchronized (LOCK) {
            TASKS.clear();
        }
    }

    public static boolean available() {
        return httpServer != null;
    }

    public static void enqueue(PreviewTask task) {
        if (!available()) {
            return;
        }
        synchronized (LOCK) {
            int limit = MapAccelConfig.REMOTE_WORKER_QUEUE_LIMIT.get();
            while (TASKS.size() >= limit) {
                TASKS.pollFirst();
                rejectedWindow++;
            }
            TASKS.addLast(task);
        }
    }

    public static Stats snapshotAndReset() {
        synchronized (LOCK) {
            Stats stats = new Stats(TASKS.size(), completedWindow, rejectedWindow, available(), MapAccelConfig.REMOTE_WORKER_PORT.get());
            completedWindow = 0;
            rejectedWindow = 0;
            return stats;
        }
    }

    public static String workerUrlHint() {
        if (accessToken == null) {
            return "disabled";
        }
        return "http://server-ip:" + MapAccelConfig.REMOTE_WORKER_PORT.get() + "/?token=" + accessToken;
    }

    private static void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "method not allowed");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", workerPage());
    }

    private static void handleTask(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            send(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"method\"}");
            return;
        }
        PreviewTask task;
        synchronized (LOCK) {
            task = TASKS.pollFirst();
        }
        if (task == null) {
            send(exchange, 200, "application/json", "{\"task\":\"idle\"}");
            return;
        }
        send(exchange, 200, "application/json", GSON.toJson(task.toJson()));
    }

    private static void handleResult(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            send(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"method\"}");
            return;
        }
        MinecraftServer server = minecraftServer;
        if (server == null) {
            send(exchange, 503, "application/json", "{\"error\":\"server\"}");
            return;
        }
        try (InputStream input = exchange.getRequestBody()) {
            JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            PreviewAssistResultPacket packet = packetFromJson(json);
            server.execute(() -> MapAccelServerState.PREVIEW_ASSIST.acceptRemote(packet, server.getTickCount()));
            synchronized (LOCK) {
                completedWindow += packet.count();
            }
            send(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (RuntimeException ex) {
            synchronized (LOCK) {
                rejectedWindow++;
            }
            send(exchange, 400, "application/json", "{\"error\":\"bad_result\"}");
        }
    }

    private static PreviewAssistResultPacket packetFromJson(JsonObject json) {
        JsonArray chunks = json.getAsJsonArray("chunks");
        int count = chunks.size();
        int[] chunkXs = new int[count];
        int[] chunkZs = new int[count];
        int[] minHeights = new int[count];
        int[] maxHeights = new int[count];
        int[] averageHeights = new int[count];
        int[] hashes = new int[count];
        for (int i = 0; i < count; i++) {
            JsonObject chunk = chunks.get(i).getAsJsonObject();
            chunkXs[i] = chunk.get("x").getAsInt();
            chunkZs[i] = chunk.get("z").getAsInt();
            minHeights[i] = chunk.get("min").getAsInt();
            maxHeights[i] = chunk.get("max").getAsInt();
            averageHeights[i] = chunk.get("avg").getAsInt();
            hashes[i] = chunk.get("hash").getAsInt();
        }
        return new PreviewAssistResultPacket(
                json.get("requestId").getAsLong(),
                json.get("dimension").getAsString(),
                Long.parseLong(json.get("seed").getAsString()),
                json.get("mode").getAsString(),
                chunkXs,
                chunkZs,
                minHeights,
                maxHeights,
                averageHeights,
                hashes
        );
    }

    private static boolean authorized(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-MapAccel-Token");
        if (token == null || token.isBlank()) {
            token = query(exchange).get("token");
        }
        return accessToken != null && accessToken.equals(token);
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String part : raw.split("&")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private static void send(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String configuredOrGeneratedToken() {
        String configured = MapAccelConfig.REMOTE_WORKER_ACCESS_TOKEN.get();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String workerPage() {
        return """
                <!doctype html>
                <html lang=\"ja\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>MapAccel Worker</title>
                  <style>
                    body{font-family:system-ui,sans-serif;margin:24px;line-height:1.45;background:#101418;color:#eef3f7}
                    code{background:#202832;padding:2px 5px;border-radius:4px}
                    .stat{font-size:32px;font-weight:700}
                    button{font:inherit;padding:10px 14px;margin-right:8px}
                  </style>
                </head>
                <body>
                  <h1>MapAccel Worker</h1>
                  <p>この端末を MapAccel の preview 計算に使います。スマホ/タブレットは充電しながらの使用を推奨します。</p>
                  <p>状態: <span id=\"state\">starting</span></p>
                  <p class=\"stat\"><span id=\"chunks\">0</span> chunks</p>
                  <button id=\"start\">Start</button><button id=\"stop\">Stop</button>
                  <pre id=\"log\"></pre>
                <script>
                const token = new URL(location.href).searchParams.get('token') || '';
                let running = true, chunks = 0;
                const state = document.getElementById('state');
                const count = document.getElementById('chunks');
                const log = document.getElementById('log');
                document.getElementById('start').onclick = () => { running = true; loop(); };
                document.getElementById('stop').onclick = () => { running = false; state.textContent = 'stopped'; };
                function line(s){ log.textContent = new Date().toLocaleTimeString() + ' ' + s + '\\n' + log.textContent.slice(0, 3000); }
                function asLong(n){ return BigInt.asIntN(64, n); }
                function ushr(n,b){ return BigInt.asUintN(64, n) >> BigInt(b); }
                function previewHeight(seed, x, z, mode){
                  let mixed = asLong(BigInt(seed) ^ asLong(BigInt(x) * 341873128712n) ^ asLong(BigInt(z) * 132897987541n));
                  mixed = asLong(mixed ^ ushr(mixed, 33));
                  mixed = asLong(mixed * 0xff51afd7ed558ccdn);
                  mixed = asLong(mixed ^ ushr(mixed, 33));
                  const noise = Number(BigInt.asUintN(64, mixed) & 63n);
                  if (mode === 'SHELL') return 32 + noise;
                  if (mode === 'ISLAND') return 48 + Math.trunc(noise / 2);
                  return 52 + noise;
                }
                function compute(task){
                  const out = [];
                  for (let i=0;i<task.chunkXs.length;i++){
                    const cx = task.chunkXs[i], cz = task.chunkZs[i];
                    let min = 2147483647, max = -2147483648, total = 0, hash = 1;
                    for (let z=0;z<16;z++) for (let x=0;x<16;x++){
                      const h = previewHeight(task.seed, (cx << 4) + x, (cz << 4) + z, task.mode);
                      min = Math.min(min, h); max = Math.max(max, h); total += h;
                      hash = (Math.imul(31, hash) + h) | 0;
                    }
                    out.push({x:cx,z:cz,min:min,max:max,avg:Math.trunc(total/256),hash:hash});
                  }
                  return {requestId:task.requestId, dimension:task.dimension, seed:String(task.seed), mode:task.mode, chunks:out};
                }
                async function loop(){
                  while(running){
                    try{
                      state.textContent = 'polling';
                      const r = await fetch('/task?token=' + encodeURIComponent(token), {cache:'no-store'});
                      const task = await r.json();
                      if(task.task === 'idle'){ await new Promise(res=>setTimeout(res, 600)); continue; }
                      state.textContent = 'computing';
                      const result = compute(task);
                      await fetch('/result?token=' + encodeURIComponent(token), {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(result)});
                      chunks += result.chunks.length; count.textContent = chunks; line('computed ' + result.chunks.length + ' chunks');
                    }catch(e){ state.textContent = 'error'; line(String(e)); await new Promise(res=>setTimeout(res, 1500)); }
                  }
                }
                loop();
                </script>
                </body>
                </html>
                """;
    }

    public record PreviewTask(long requestId, String dimension, long seed, String mode, int[] chunkXs, int[] chunkZs) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("task", "preview");
            json.addProperty("requestId", requestId);
            json.addProperty("dimension", dimension);
            json.addProperty("seed", Long.toString(seed));
            json.addProperty("mode", mode);
            JsonArray xs = new JsonArray();
            JsonArray zs = new JsonArray();
            for (int chunkX : chunkXs) {
                xs.add(chunkX);
            }
            for (int chunkZ : chunkZs) {
                zs.add(chunkZ);
            }
            json.add("chunkXs", xs);
            json.add("chunkZs", zs);
            return json;
        }
    }

    public record Stats(int queuedTasks, int completedChunks, int rejectedTasks, boolean enabled, int port) {
    }
}
