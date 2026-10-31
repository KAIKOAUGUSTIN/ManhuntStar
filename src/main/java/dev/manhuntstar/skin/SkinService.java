package dev.manhuntstar.skin;

import dev.manhuntstar.bot.SkinData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Skins dos bots: pega a skin do jogador com o mesmo nome na Mojang; se não existir, usa a primeira
 * imagem .png da pasta {@code plugins/ManhuntStar/skins/} ou a skin de robô do mod, enviada uma
 * vez ao MineSkin para ganhar assinatura (como o mod fazia). Tudo fica em cache em disco.
 * Corrige o mod, que procurava as skins numa pasta fixa do computador do autor.
 */
public final class SkinService {

    private static final long CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final String ROBOT_KEY = "#robo";

    private final File folder;
    private final File cacheFile;
    private final Logger logger;
    private final BooleanSupplier mineskinAllowed;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ManhuntStar-Skins");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, CachedSkin> cache = new ConcurrentHashMap<>();
    private volatile long lastRobotAttempt;

    private record CachedSkin(SkinData skin, long time) {
    }

    public SkinService(File dataFolder, Logger logger, BooleanSupplier mineskinAllowed) {
        this.folder = new File(dataFolder, "skins");
        this.cacheFile = new File(folder, "cache.yml");
        this.logger = logger;
        this.mineskinAllowed = mineskinAllowed;
        folder.mkdirs();
        loadCache();
    }

    /** Skin para o nome (assíncrono); nunca falha: no pior caso devolve null (skin padrão do jogo). */
    public CompletableFuture<SkinData> resolve(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        CachedSkin c = cache.get(key);
        if (c != null && System.currentTimeMillis() - c.time() < CACHE_TTL_MS) {
            return CompletableFuture.completedFuture(c.skin());
        }
        return CompletableFuture.supplyAsync(() -> {
            SkinData skin = null;
            if (key.matches("[a-z0-9_]{1,16}")) {
                skin = fetchMojang(key);
            }
            if (skin != null) {
                cache.put(key, new CachedSkin(skin, System.currentTimeMillis()));
                saveCache();
                return skin;
            }
            // A reserva não fica guardada com o nome: se a Mojang só estava fora do ar, a skin
            // certa volta na próxima criação em vez de ficar 7 dias no robô.
            return robotSkin();
        }, executor).exceptionally(t -> null);
    }

    /** Só a skin de um jogador real (usado por /manhunt skin). */
    public CompletableFuture<SkinData> fetchPlayerSkin(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        return CompletableFuture.supplyAsync(() -> {
            SkinData s = fetchMojang(key);
            if (s != null) {
                cache.put(key, new CachedSkin(s, System.currentTimeMillis()));
                saveCache();
            }
            return s;
        }, executor).exceptionally(t -> null);
    }

    private SkinData fetchMojang(String name) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/"
                    + URLEncoder.encode(name, StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200 || r.body().isBlank()) {
                return null;
            }
            String id = JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();
            HttpResponse<String> p = http.send(HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false"))
                    .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (p.statusCode() != 200) {
                return null;
            }
            JsonArray props = JsonParser.parseString(p.body()).getAsJsonObject().getAsJsonArray("properties");
            for (JsonElement e : props) {
                JsonObject o = e.getAsJsonObject();
                if ("textures".equals(o.get("name").getAsString()) && o.has("signature")) {
                    return new SkinData(o.get("value").getAsString(), o.get("signature").getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.fine("Skin of " + name + " not found: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /** Skin de reserva: PNG da pasta de skins ou o robô do mod, assinada pelo MineSkin (com cache). */
    private SkinData robotSkin() {
        CachedSkin c = cache.get(ROBOT_KEY);
        if (c != null) {
            return c.skin();
        }
        if (!mineskinAllowed.getAsBoolean() || System.currentTimeMillis() - lastRobotAttempt < 10_000) {
            return null;
        }
        lastRobotAttempt = System.currentTimeMillis();
        byte[] png = localPng();
        if (png == null) {
            return null;
        }
        SkinData s = uploadToMineSkin(png);
        if (s != null) {
            cache.put(ROBOT_KEY, new CachedSkin(s, System.currentTimeMillis()));
            saveCache();
        }
        return s;
    }

    private byte[] localPng() {
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        try {
            if (files != null && files.length > 0) {
                return Files.readAllBytes(files[0].toPath());
            }
            try (InputStream in = SkinService.class.getResourceAsStream("/skins/robot.png")) {
                if (in == null) {
                    return null;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private SkinData uploadToMineSkin(byte[] png) {
        String boundary = "----ManhuntStarSkin" + System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"robot.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(png);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.mineskin.org/generate/upload"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", "ManhuntStar/1.0")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
            JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
            JsonObject texture = null;
            if (root.has("data") && root.get("data").isJsonObject() && root.getAsJsonObject("data").has("texture")) {
                texture = root.getAsJsonObject("data").getAsJsonObject("texture");
            } else if (root.has("skin") && root.get("skin").isJsonObject() && root.getAsJsonObject("skin").has("texture")) {
                texture = root.getAsJsonObject("skin").getAsJsonObject("texture");
            }
            if (texture == null) {
                texture = findTexture(root);
            }
            if (texture != null && texture.has("value") && texture.has("signature")) {
                return new SkinData(texture.get("value").getAsString(), texture.get("signature").getAsString());
            }
            logger.warning("MineSkin did not return the fallback skin (HTTP " + r.statusCode() + ").");
        } catch (IOException | RuntimeException e) {
            logger.warning("Could not generate the fallback skin on MineSkin: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /** API nova do MineSkin: {"skin":{"texture":{"data":{"value","signature"}}}}. */
    private static JsonObject findTexture(JsonObject root) {
        if (root.has("skin") && root.get("skin").isJsonObject()) {
            JsonObject skin = root.getAsJsonObject("skin");
            if (skin.has("texture") && skin.get("texture").isJsonObject()) {
                JsonObject t = skin.getAsJsonObject("texture");
                if (t.has("data") && t.get("data").isJsonObject()) {
                    return t.getAsJsonObject("data");
                }
            }
        }
        return null;
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(cacheFile);
        for (String k : y.getKeys(false)) {
            String v = y.getString(k + ".value");
            String s = y.getString(k + ".signature");
            long t = y.getLong(k + ".time");
            if (v != null && s != null) {
                cache.put(y.getString(k + ".name", k), new CachedSkin(new SkinData(v, s), t));
            }
        }
    }

    private synchronized void saveCache() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<String, CachedSkin> e : cache.entrySet()) {
            String k = "s" + (i++);
            y.set(k + ".name", e.getKey());
            y.set(k + ".value", e.getValue().skin().value());
            y.set(k + ".signature", e.getValue().skin().signature());
            y.set(k + ".time", e.getValue().time());
        }
        try {
            y.save(cacheFile);
        } catch (IOException e) {
            logger.warning("Could not save the skin cache: " + e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
