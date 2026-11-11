package wlamir.servernewslist.server;

import wlamir.servernewslist.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

public class ServerNewsListServer {
    public static void register() {
        // Проверка новостей при входе
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String latestId = NewsFileReader.getLatestNewsId(server);
            sender.sendPacket(new UpdateCheckPayload(latestId), null);
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
}