package wlamir.servernewslist.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NewsFileReader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static String readAllNews(MinecraftServer server) {
        String serverName = server.getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9.-]", "_");

        Path baseDir = server.getServerDirectory().resolve("config/servernewslist").resolve(serverName);
        Path newsRoot = baseDir.resolve("news");
        Path postponedRoot = baseDir.resolve("postponed");

        try {
            if (!Files.exists(newsRoot)) {
                Files.createDirectories(newsRoot);
                createExampleNews(newsRoot, "Пример Новости", 1);
            }
            if (!Files.exists(postponedRoot)) {
                Files.createDirectories(postponedRoot);
                createExampleNews(postponedRoot, "Отложенная Новость", 2);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "[]";
        }

        List<Map<String, Object>> newsList = new ArrayList<>();

        // Передаем newsRoot вторым аргументом, чтобы знать, куда переносить
        processDirectory(newsRoot, newsRoot, newsList, false);
        processDirectory(postponedRoot, newsRoot, newsList, true);

        newsList.sort((a, b) -> Long.compare((Long) b.get("timestamp"), (Long) a.get("timestamp")));

        return GSON.toJson(newsList);
    }

    private static void processDirectory(Path root, Path newsRoot, List<Map<String, Object>> newsList, boolean isPostponed) {
        // Сначала собираем все папки в список, чтобы избежать ошибок при их перемещении
        List<Path> folders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path folder : stream) {
                if (Files.isDirectory(folder)) folders.add(folder);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (Path folder : folders) {
            String folderName = folder.getFileName().toString();
            Path txtFile = folder.resolve("news.txt");
            if (!Files.exists(txtFile)) continue;

            int newsType = 1;
            long releaseTimestamp = 0;

            Path propFile = folder.resolve("properties.txt");
            if (Files.exists(propFile)) {
                Properties props = new Properties();
                try (var reader = Files.newBufferedReader(propFile, StandardCharsets.UTF_8)) {
                    props.load(reader);
                    newsType = Integer.parseInt(props.getProperty("type", "1").trim());
                    String timeStr = props.getProperty("time");
                    if (timeStr != null && !timeStr.isBlank()) {
                        LocalDateTime publishTime = LocalDateTime.parse(timeStr.trim(), TIME_FORMATTER);
                        releaseTimestamp = publishTime.toInstant(ZoneOffset.UTC).toEpochMilli();
                    }
                } catch (Exception e) {
                    System.out.println("[ServerNews] Ошибка чтения properties.txt в " + folderName);
                }
            }

            if (releaseTimestamp == 0) {
                try {
                    releaseTimestamp = Files.getLastModifiedTime(txtFile).toMillis();
                } catch (IOException e) {
                    releaseTimestamp = System.currentTimeMillis();
                }
            }

            // ЛОГИКА ОТЛОЖЕННЫХ НОВОСТЕЙ
            if (isPostponed) {
                if (System.currentTimeMillis() < releaseTimestamp) {
                    continue; // Время еще не пришло, пропускаем
                } else {
                    // ВРЕМЯ ПРИШЛО! ПЕРЕНОСИМ ПАПКУ
                    Path targetFolder = newsRoot.resolve(folderName);
                    try {
                        Files.move(folder, targetFolder, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("[ServerNews] Новость '" + folderName + "' перенесена в папку news.");

                        // Обновляем пути, так как папка теперь лежит в другом месте
                        folder = targetFolder;
                        txtFile = folder.resolve("news.txt");
                    } catch (IOException e) {
                        System.out.println("[ServerNews] Не удалось перенести папку " + folderName + ": " + e.getMessage());
                        // Даже если перенос не удался, мы продолжим чтение из старой папки, чтобы игроки увидели новость
                    }
                }
            }

            // Чтение контента (после возможного переноса)
            try {
                String text = Files.readString(txtFile, StandardCharsets.UTF_8);
                List<String> mediaData = new ArrayList<>();

                try (DirectoryStream<Path> mediaStream = Files.newDirectoryStream(folder)) {
                    for (Path file : mediaStream) {
                        String name = file.getFileName().toString().toLowerCase();
                        if (name.endsWith(".webp")) {
                            byte[] bytes = Files.readAllBytes(file);
                            mediaData.add(Base64.getEncoder().encodeToString(bytes));
                        }
                    }
                }

                Map<String, Object> newsEntry = new LinkedHashMap<>();
                newsEntry.put("id", folderName);
                newsEntry.put("title", folderName);
                newsEntry.put("text", text);
                newsEntry.put("mediaData", mediaData);
                newsEntry.put("timestamp", releaseTimestamp);
                newsEntry.put("type", newsType);

                newsList.add(newsEntry);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void createExampleNews(Path root, String name, int type) throws IOException {
        Path exampleFolder = root.resolve(name);
        Files.createDirectories(exampleFolder);

        Files.writeString(exampleFolder.resolve("news.txt"),
                "Это пример новости.\nТип новости: " + type + "\n\\1\nКартинка появится здесь!",
                StandardCharsets.UTF_8);

        String timeStr = name.equals("Отложенная Новость") ? "31.12.2026 23:59" : "01.01.2025 12:00";
        Files.writeString(exampleFolder.resolve("properties.txt"),
                "# Время публикации по GMT/UTC (День.Месяц.Год Час:Минута)\n" +
                        "time=" + timeStr + "\n" +
                        "# Тип новости: 1(темный), 2(зеленый), 3(голубой), 4(красный)\n" +
                        "type=" + type,
                StandardCharsets.UTF_8);
    }

    public static String getLatestNewsId(MinecraftServer server) {
        String json = readAllNews(server);
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            if (!arr.isEmpty()) {
                return arr.get(0).getAsJsonObject().get("id").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "none";
    }
}