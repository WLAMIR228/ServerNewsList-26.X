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

    private Button refreshButton;
    public static long lastRefreshTime = 0;
    public static long currentCooldownMs = 10000;

    public static final long MANUAL_COOLDOWN_MS = 9750;
    public static final long RATING_COOLDOWN_MS = 19750;

    public boolean showType1 = true;
    public boolean showType2 = true;
    public boolean showType3 = true;
    public boolean showType4 = true;


    public NewsScreen(Screen parent) {
        super(Component.translatable("gui.servernewslist.button"));
        this.parent = parent;
    }

    public static boolean isRefreshing() {
        return System.currentTimeMillis() - lastRefreshTime < currentCooldownMs;
    }

    public static long getDynamicCooldown(long baseCooldownMs) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return baseCooldownMs; // Если мы не в игре, возвращаем базовое время
        }

        int playerCount = mc.getConnection().getOnlinePlayers().size();

        return baseCooldownMs + (playerCount * 250L);
    }

    public void triggerRefresh(long baseCooldownMs) {
        long now = System.currentTimeMillis();
        long actualCooldown = getDynamicCooldown(baseCooldownMs); // <--- Высчитываем время с игроками

        // Разрешаем обновление, если время истекло или если новый кулдаун больше текущего
        if (now - lastRefreshTime >= currentCooldownMs || actualCooldown > currentCooldownMs) {
            lastRefreshTime = now;
            currentCooldownMs = actualCooldown; // <--- Сохраняем динамическое время

            NewsCacheManager.clearCache();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new wlamir.servernewslist.network.NewsRequestPayload()
            );

            if (this.refreshButton != null) {
                this.refreshButton.active = false;
            }
        }
    }

    public void updateRating(String newsId, int likes, int dislikes) {
        if (this.newsList != null) {
            for (var entry : this.newsList.children()) {
                if (entry.getTitle().equals(newsId)) {
                    entry.likes = likes;
                    entry.dislikes = dislikes;
                    break;
                }
            }
        }
    }



    @Override
    protected void init() {
        super.init();

        this.newsList = new NewsListWidget(
                this.minecraft, this.width, this.height - 68, 33, this.height - 50,
                this.showType1, this.showType2, this.showType3, this.showType4
        );
        this.addWidget(this.newsList);

        this.refreshButton = Button.builder(Component.translatable("gui.servernewslist.refresh"), button -> {
            triggerRefresh(MANUAL_COOLDOWN_MS);
        }).bounds(this.width - 125, 6, 120, 20).build();

        this.addRenderableWidget(this.refreshButton);

        // ВАЖНО: Сразу проверяем состояние при открытии!
        // Если с момента нажатия прошло меньше 3 секунд, кнопка сразу будет заблокирована.
        updateButtonState();

        this.addRenderableWidget(Button.builder(Component.translatable("gui.servernewslist.done"),
                b -> this.minecraft.gui.setScreen(this.parent)).bounds(6,  6, 120, 20).build());

        int checkY = this.height - 25;

        this.addRenderableWidget(net.minecraft.client.gui.components.Checkbox.builder(Component.translatable("gui.servernewslist.type.type1.short"), this.font)
                .pos(8, checkY)
                .selected(this.showType1)
                .onValueChange((checkbox, value) -> {
                    this.showType1 = value;
                    Minecraft.getInstance().execute(this::refreshList);
                }).build());

        this.addRenderableWidget(net.minecraft.client.gui.components.Checkbox.builder(Component.translatable("gui.servernewslist.type.type2.short"), this.font)
                .pos(8 + 40, checkY) // Сдвинул координаты, так как текст стал короче
                .selected(this.showType2)
                .onValueChange((checkbox, value) -> {
                    this.showType2 = value;
                    Minecraft.getInstance().execute(this::refreshList);
                }).build());

        this.addRenderableWidget(net.minecraft.client.gui.components.Checkbox.builder(Component.translatable("gui.servernewslist.type.type3.short"), this.font)
                .pos(8 + 80, checkY)
                .selected(this.showType3)
                .onValueChange((checkbox, value) -> {
                    this.showType3 = value;
                    Minecraft.getInstance().execute(this::refreshList);
                }).build());

        this.addRenderableWidget(net.minecraft.client.gui.components.Checkbox.builder(Component.translatable("gui.servernewslist.type.type4.short"), this.font)
                .pos(8 + 120, checkY)
                .selected(this.showType4)
                .onValueChange((checkbox, value) -> {
                    this.showType4 = value;
                    Minecraft.getInstance().execute(this::refreshList);
                }).build());
    }

    private void updateButtonState() {
        if (this.refreshButton != null) {
            long timePassed = System.currentTimeMillis() - lastRefreshTime;
            long timeLeft = currentCooldownMs - timePassed; // <--- Используем текущий кулдаун

            if (timeLeft > 0) {
                this.refreshButton.active = false;
                long secondsLeft = (timeLeft / 1000) + 1;
                this.refreshButton.setMessage(Component.translatable("gui.servernewslist.wait", secondsLeft));
            } else {
                if (!this.refreshButton.active) {
                    this.refreshButton.active = true;
                    this.refreshButton.setMessage(Component.translatable("gui.servernewslist.refresh"));
                }
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

        if (this.newsList != null) {
            this.newsList.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.text(this.font, "UTC: "+LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), this.width - 112, this.height - 12, 0x40FFFFFF, true);
        this.newsList.extractRenderState(graphics, mouseX, mouseY, partialTick);

        this.newsList.hoveredTooltip = null;


        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.newsList.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Если список записал подсказку (мышь наведена на иконку) — отрисовываем её
        if (this.newsList.hoveredTooltip != null) {
            graphics.setTooltipForNextFrame(
                    this.font,
                    Component.translatable(this.newsList.hoveredTooltip),
                    mouseX,
                    mouseY
            );
        }
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