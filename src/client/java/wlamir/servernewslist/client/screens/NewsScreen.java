package wlamir.servernewslist.client.screens;

import net.minecraft.client.Minecraft;
import wlamir.servernewslist.client.NewsCacheManager;
import wlamir.servernewslist.network.NewsRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class NewsScreen extends Screen {
    private final Screen parent;
    private NewsListWidget newsList;
    private double savedScrollAmount = 0;


    public NewsScreen(Screen parent) {
        super(Component.literal("Новости сервера"));
        this.parent = parent;
    }

    private Button refreshButton;
    private static long lastRefreshTime = 0;
    private static final long REFRESH_COOLDOWN_MS = 10000; // 3 секунды


    @Override
    protected void init() {
        super.init();

        this.newsList = new NewsListWidget(this.minecraft, this.width, this.height-66, 33, this.height - 50);
        this.addWidget(this.newsList);

        this.refreshButton = Button.builder(Component.literal("Обновить"), button -> {
            lastRefreshTime = System.currentTimeMillis(); // Запоминаем время
            NewsCacheManager.clearCache();
            ClientPlayNetworking.send(new NewsRequestPayload());
        }).bounds(this.width - 125, 6, 120, 20).build();

        this.addRenderableWidget(this.refreshButton);

        // ВАЖНО: Сразу проверяем состояние при открытии!
        // Если с момента нажатия прошло меньше 3 секунд, кнопка сразу будет заблокирована.
        updateButtonState();

        this.addRenderableWidget(Button.builder(Component.literal("Готово"),
                b -> this.minecraft.gui.setScreen(this.parent)).bounds(6,  6, 120, 20).build());
    }

    private void updateButtonState() {
        if (this.refreshButton != null) {
            long timePassed = System.currentTimeMillis() - lastRefreshTime;
            long timeLeft = REFRESH_COOLDOWN_MS - timePassed;

            if (timeLeft > 0) {
                this.refreshButton.active = false;
                long secondsLeft = (timeLeft / 1000) + 1;
                this.refreshButton.setMessage(Component.literal("Подождите " + secondsLeft + " сек."));
            } else {
                this.refreshButton.active = true;
                this.refreshButton.setMessage(Component.literal("Обновить"));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonState(); // Обновляем состояние 20 раз в секунду
    }


    // ИСПРАВЛЕНИЕ: метод render заменен на extractRenderState
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // ИСПРАВЛЕНИЕ: drawCenteredString заменен на centeredText
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.text(this.font, "UTC: "+LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), 134, 12, 0x40FFFFFF, true);
        this.newsList.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    public void refreshList() {
        // Сохраняем текущую позицию перед очисткой
        if (this.newsList != null) {
            // Пробуем получить значение напрямую или через геттер, если он есть
            this.savedScrollAmount = this.newsList.scrollAmount();
        }

        this.clearWidgets();
        this.init(); // Пересоздаем экран

        // Восстанавливаем позицию после инициализации
        if (this.newsList != null) {
            this.newsList.setScrollAmount(this.savedScrollAmount);
        }
    }

}