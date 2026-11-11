package wlamir.servernewslist.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class NewsCacheManager {
    // Базовая папка для кэшей
    private static final File BASE_DIR = new File(Minecraft.getInstance().gameDirectory, "config/servernewslist");
    private static final File LAST_SERVER_FILE = new File(BASE_DIR, "last_server.txt");

    private static final long CACHE_LIFETIME_MS = 2L * 24 * 60 * 60 * 1000; // 2 дня
    private static final Gson GSON = new GsonBuilder().create();

    public static class CacheData {
        String json;
        long lastUsed;
        String latestNewsId;
    }

    /**
     * Динамически определяет файл кэша в зависимости от текущего сервера.
     */
    private static File getCacheFile() {
        if (!BASE_DIR.exists()) {
            BASE_DIR.mkdirs();
        }

        String serverId = "unknown";

        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            // Мы в мультиплеере. Берем IP сервера и чистим его от недопустимых символов
            ServerData server = mc.getCurrentServer();
            serverId = server.ip.replaceAll("[^a-zA-Z0-9.-]", "_");

            // Запоминаем этот сервер как последний, чтобы показать его кэш в главном меню
            try {
                Files.writeString(LAST_SERVER_FILE.toPath(), serverId, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}

        } else if (mc.getSingleplayerServer() != null) {
            // Мы в одиночной игре
            serverId = "singleplayer";
        } else {
            // Мы в главном меню. Читаем, на каком сервере играли в последний раз
            if (LAST_SERVER_FILE.exists()) {
                try {
                    String last = Files.readString(LAST_SERVER_FILE.toPath(), StandardCharsets.UTF_8).trim();
                    if (!last.isEmpty()) serverId = last;
                } catch (IOException ignored) {}
            }
        }

        return new File(BASE_DIR, "news_cache_" + serverId + ".json");
    }

    public static void saveCache(String json) {
        try {
            CacheData data = new CacheData();
            data.json = json;
            data.lastUsed = System.currentTimeMillis();

            // Вычисляем ID последней новости
            try {
                com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
                if (!arr.isEmpty()) {
                    data.latestNewsId = arr.get(0).getAsJsonObject().get("id").getAsString();
                }
            } catch (Exception ignored) {}

            Files.writeString(getCacheFile().toPath(), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String loadCache() {
        File cacheFile = getCacheFile();
        if (!cacheFile.exists()) return null;

        try {
            String content = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
            CacheData data = GSON.fromJson(content, CacheData.class);
            if (data == null || data.json == null) return null;

            long age = System.currentTimeMillis() - data.lastUsed;
            if (age > CACHE_LIFETIME_MS) {
                clearCache();
                return null;
            }

            // Обновляем время последнего использования
            data.lastUsed = System.currentTimeMillis();
            Files.writeString(cacheFile.toPath(), GSON.toJson(data), StandardCharsets.UTF_8);

            return data.json;
        } catch (IOException e) {
            clearCache();
            return null;
        }
    }

    public static String getLatestCachedNewsId() {
        File cacheFile = getCacheFile();
        if (!cacheFile.exists()) return null;
        try {
            String content = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
            CacheData data = GSON.fromJson(content, CacheData.class);
            return data != null ? data.latestNewsId : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void clearCache() {
        File cacheFile = getCacheFile();
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }
}