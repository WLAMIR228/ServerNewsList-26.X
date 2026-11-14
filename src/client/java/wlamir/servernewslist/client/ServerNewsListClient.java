package wlamir.servernewslist.client;

import wlamir.servernewslist.client.screens.NewsScreen;
import wlamir.servernewslist.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens; // Важно для доступа к виджетам
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen; // Целевой экран
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ServerNewsListClient implements ClientModInitializer {

	private static final Map<Integer, String> newsChunks = new HashMap<>();
	private static int expectedChunks = -1;

	@Override
	public void onInitializeClient() {
		// Добавление кнопки в меню паузы (ESC)
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen) {
				Button newsButton = Button.builder(
						Component.translatable("gui.servernewslist.button"),
						button -> client.gui.setScreen(new NewsScreen(screen))
				).bounds(scaledWidth - 128, scaledHeight - 28, 120, 20).build();

				// Используем Screens.getWidgets для безопасного добавления
				Screens.getWidgets(screen).add(newsButton);
			}
		});

		// Запрос данных при входе на сервер (чтобы кэш был актуален)
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientPlayNetworking.send(new NewsRequestPayload());
		});

		ClientPlayNetworking.registerGlobalReceiver(RatingUpdatePayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().gui.screen() instanceof NewsScreen screen) {
					// 1. Обновляем цифры на экране
					screen.updateRating(payload.newsId(), payload.likes(), payload.dislikes());

					// 2. АВТОМАТИЧЕСКИ НАЖИМАЕМ КНОПКУ "ОБНОВИТЬ"
					screen.triggerRefresh();
				}
			});
		});

		// Приём ответа с новостями
		ClientPlayNetworking.registerGlobalReceiver(NewsResponsePayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				newsChunks.put(payload.chunkIndex(), payload.data());
				expectedChunks = payload.totalChunks();

				if (newsChunks.size() == expectedChunks) {
					StringBuilder fullBase64 = new StringBuilder();
					for (int i = 0; i < expectedChunks; i++) {
						fullBase64.append(newsChunks.get(i));
					}
					newsChunks.clear();
					expectedChunks = -1;

					try {
						// Распаковываем GZIP обратно в JSON
						byte[] compressed = Base64.getDecoder().decode(fullBase64.toString());
						ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
						GZIPInputStream gzip = new GZIPInputStream(bis);
						String json = new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

						NewsCacheManager.saveCache(json);
						if (context.client().gui.screen() instanceof NewsScreen newsScreen) {
							newsScreen.refreshList();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		});

		// Уведомление о новой новости при входе
		ClientPlayNetworking.registerGlobalReceiver(UpdateCheckPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				String cachedId = NewsCacheManager.getLatestCachedNewsId();
				if (!payload.latestNewsId().equals(cachedId)) {
					context.player().sendSystemMessage(Component.translatable("chat.servernewslist.new_news"));
				}
			});
		});
	}
}