package wlamir.servernewslist.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import wlamir.servernewslist.ModGameRules;
import wlamir.servernewslist.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

public class ServerNewsListServer {
    private static int tickCounter = 0;
    private static final int TICKS_PER_HOUR = 20 * 60 * 60;


    public static void register() {
        // Проверка новостей при входе
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String latestId = NewsFileReader.getLatestNewsId(server);
            sender.sendPacket(new UpdateCheckPayload(latestId), null);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= TICKS_PER_HOUR) {
                tickCounter = 0;
                cleanupOldNews(server);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(RateNewsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                String serverName = context.server().getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9.-]", "_");
                Path baseDir = context.server().getServerDirectory().resolve("config/servernewslist").resolve(serverName);

                Path folder = baseDir.resolve("news").resolve(payload.newsId());
                if (!Files.exists(folder)) folder = baseDir.resolve("postponed").resolve(payload.newsId());
                if (!Files.exists(folder)) return;

                Path propFile = folder.resolve("properties.txt");
                if (Files.exists(propFile)) {
                    Properties props = new Properties();
                    try (var reader = Files.newBufferedReader(propFile, StandardCharsets.UTF_8)) {
                        props.load(reader);
                        // Если оценки запрещены, просто игнорируем пакет
                        if (!Boolean.parseBoolean(props.getProperty("allow_rating", "true").trim())) {
                            return;
                        }
                    } catch (Exception ignored) {}
                }

                Path ratingFile = folder.resolve("rating.txt");
                com.google.gson.JsonObject votes = new com.google.gson.JsonObject();

                // 1. Читаем существующие голоса
                if (Files.exists(ratingFile)) {
                    try {
                        String content = Files.readString(ratingFile, StandardCharsets.UTF_8).trim();
                        if (content.startsWith("{")) {
                            votes = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                        }
                    } catch (Exception ignored) {}
                }

                String playerUUID = context.player().getUUID().toString();
                int currentVote = votes.has(playerUUID) ? votes.get(playerUUID).getAsInt() : 0;
                int newVote = payload.isLike() ? 1 : -1;

                // 2. Логика переключения
                if (currentVote == newVote) {
                    // Игрок нажал на то же самое - отменяем голос
                    votes.remove(playerUUID);
                } else {
                    // Игрок ставит новый голос (или меняет старый)
                    votes.addProperty(playerUUID, newVote);
                }

                // 3. Сохраняем файл
                try {
                    Files.writeString(ratingFile, votes.toString(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // 4. Считаем новые общие цифры
                int newLikes = 0;
                int newDislikes = 0;
                for (String key : votes.keySet()) {
                    int v = votes.get(key).getAsInt();
                    if (v == 1) newLikes++;
                    else if (v == -1) newDislikes++;
                }

                // 5. Отправляем обновленные цифры ВСЕМ игрокам на сервере (чтобы у всех обновилось)
                RatingUpdatePayload update = new RatingUpdatePayload(payload.newsId(), newLikes, newDislikes);
                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, update);
                }
            });
        });

        // Ответ на запрос всех новостей
        ServerPlayNetworking.registerGlobalReceiver(NewsRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String json = NewsFileReader.readAllNews(context.server());

            try {
                // 1. Сжимаем JSON в GZIP
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                    gzip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                System.out.println("[DEBUG-CLIENT] Получен JSON. Размер: " + json.length());
                if (json.contains("mediaData")) {
                    System.out.println("[DEBUG-CLIENT] В JSON есть mediaData.");
                } else {
                    System.out.println("[DEBUG-CLIENT] ВНИМАНИЕ: В JSON НЕТ mediaData!");
                }

                // 2. Кодируем в Base64, чтобы передать как строку
                String compressedBase64 = Base64.getEncoder().encodeToString(bos.toByteArray());

                // 3. Разбиваем на куски по 30000 символов
                int chunkSize = 30000;
                int totalChunks = (int) Math.ceil((double) compressedBase64.length() / chunkSize);

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(start + chunkSize, compressedBase64.length());
                    String chunk = compressedBase64.substring(start, end);
                    ServerPlayNetworking.send(player, new NewsResponsePayload(chunk, i, totalChunks));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void cleanupOldNews(MinecraftServer server) {
        // Получаем значения геймрулов
        int hours = server.overworld().getGameRules().get(ModGameRules.NEWS_LIFETIME_HOURS);
        boolean safeDelete = server.overworld().getGameRules().get(ModGameRules.SAFE_DELETE);

        if (hours <= 0) return;

        long cutoffTime = System.currentTimeMillis() - (hours * 60L * 60L * 1000L);

        String serverName = server.getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9.-]", "_");
        Path baseDir = server.getServerDirectory().resolve("config/servernewslist").resolve(serverName);

        // Создаем папку deleted, если её еще нет
        Path deletedRoot = baseDir.resolve("deleted");
        if (safeDelete && !Files.exists(deletedRoot)) {
            try {
                Files.createDirectories(deletedRoot);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Передаем safeDelete и deletedRoot в метод удаления
        deleteOldFolders(baseDir.resolve("news"), deletedRoot, safeDelete, cutoffTime);
        deleteOldFolders(baseDir.resolve("postponed"), deletedRoot, safeDelete, cutoffTime);
    }

    private static void deleteOldFolders(Path root, Path deletedRoot, boolean safeDelete, long cutoffTime) {
        if (!Files.exists(root)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path folder : stream) {
                if (!Files.isDirectory(folder)) continue;

                Path propFile = folder.resolve("properties.txt");
                long releaseTime = 0;

                // ... (логика чтения времени из properties.txt остается без изменений) ...
                if (Files.exists(propFile)) {
                    Properties props = new Properties();
                    try (var reader = Files.newBufferedReader(propFile, StandardCharsets.UTF_8)) {
                        props.load(reader);
                        String timeStr = props.getProperty("time");
                        if (timeStr != null && !timeStr.isBlank()) {
                            LocalDateTime publishTime = LocalDateTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                            releaseTime = publishTime.toInstant(ZoneOffset.UTC).toEpochMilli();
                        }
                    } catch (Exception ignored) {}
                }

                if (releaseTime == 0) {
                    try {
                        releaseTime = Files.getLastModifiedTime(folder.resolve("news.txt")).toMillis();
                    } catch (IOException e) {
                        releaseTime = System.currentTimeMillis();
                    }
                }

                // Если новость устарела:
                if (releaseTime < cutoffTime) {
                    if (safeDelete) {
                        // БЕЗОПАСНОЕ УДАЛЕНИЕ: Переносим в папку deleted
                        try {
                            Path targetFolder = deletedRoot.resolve(folder.getFileName().toString());
                            Files.move(folder, targetFolder, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("[ServerNews] Новость '" + folder.getFileName() + "' перемещена в папку deleted.");
                        } catch (IOException e) {
                            System.out.println("[ServerNews] Не удалось переместить папку " + folder.getFileName() + ": " + e.getMessage());
                        }
                    } else {
                        // ПОЛНОЕ УДАЛЕНИЕ: Стираем папку
                        try {
                            Files.walk(folder)
                                    .sorted(Comparator.reverseOrder())
                                    .forEach(p -> {
                                        try { Files.delete(p); } catch (IOException ignored) {}
                                    });
                            System.out.println("[ServerNews] Удалена устаревшая новость: " + folder.getFileName());
                        } catch (IOException e) {
                            System.out.println("[ServerNews] Не удалось удалить папку: " + folder.getFileName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}