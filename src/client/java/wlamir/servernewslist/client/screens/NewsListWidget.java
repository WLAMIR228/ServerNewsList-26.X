package wlamir.servernewslist.client.screens;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import wlamir.servernewslist.ServerNewsList;
import wlamir.servernewslist.client.NewsCacheManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import wlamir.servernewslist.client.ServerNewsListConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewsListWidget extends ObjectSelectionList<NewsListWidget.NewsEntry> {

    // Глобальный кэш, чтобы не забивать видеопамять
    private static final Map<String, NewsImage> TEXTURE_CACHE = new HashMap<>();

    private final boolean showType1;
    private final boolean showType2;
    private final boolean showType3;
    private final boolean showType4;

    public String hoveredTooltip = null;

    @Override
    public int getRowWidth() { return this.width - 36; }

    @Override
    public int getRowLeft() { return 10; }

    @Override
    protected int scrollBarX() { return this.width - 6; }

    @Override
    protected boolean isOverScrollbar(double mouseX, double mouseY) { return false; }

    @Override
    public void setSelected(@Nullable NewsListWidget.NewsEntry entry) {}

    public NewsListWidget(Minecraft client, int width, int height, int top, int bottom,
                          boolean showType1, boolean showType2, boolean showType3, boolean showType4) {
        super(client, width, height, top, bottom);
        this.showType1 = showType1;
        this.showType2 = showType2;
        this.showType3 = showType3;
        this.showType4 = showType4;
        loadFromCache();
    }

    private void loadFromCache() {
        String json = NewsCacheManager.loadCache();

        if (json == null) {
            return;
        }

        if (json == null) return;
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String title = obj.get("title").getAsString();
                String text = obj.get("text").getAsString();
                long timestamp = obj.get("timestamp").getAsLong();

                int newsType = obj.has("type") ? obj.get("type").getAsInt() : 1;

                if (newsType == 1 && !showType1) continue;
                if (newsType == 2 && !showType2) continue;
                if (newsType == 3 && !showType3) continue;
                if (newsType == 4 && !showType4) continue;

                int likes = obj.has("likes") ? obj.get("likes").getAsInt() : 0;
                int dislikes = obj.has("dislikes") ? obj.get("dislikes").getAsInt() : 0;

                boolean allowRating = obj.has("allowRating") ? obj.get("allowRating").getAsBoolean() : true;

                List<NewsImage> textures = new ArrayList<>();

                if (obj.has("mediaData")) {
                    JsonArray dataArr = obj.getAsJsonArray("mediaData");

                    for (int i = 0; i < dataArr.size(); i++) {
                        String base64 = dataArr.get(i).getAsString();
                        String cacheKey = title + "_" + i;

                        if (TEXTURE_CACHE.containsKey(cacheKey)) {
                            textures.add(TEXTURE_CACHE.get(cacheKey));
                        } else {
                            NewsImage img = loadTextureFromBase64(cacheKey, base64);
                            if (img != null) {
                                TEXTURE_CACHE.put(cacheKey, img);
                                textures.add(img);
                            }
                        }
                    }
                }

                addEntry(new NewsEntry(title, text, textures, timestamp, newsType, likes, dislikes, allowRating, this));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private NewsImage loadTextureFromBase64(String name, String base64) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));


            if (bufferedImage == null || bufferedImage.getWidth() <= 0 || bufferedImage.getHeight() <= 0) {
                ServerNewsList.LOGGER.error("Не удалось прочитать формат картинки: {}", name);
                return null;
            }

            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

            // ИСПРАВЛЕНИЕ: Правильная конвертация цветов и использование setPixelABGR
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = bufferedImage.getRGB(x, y);

                    // Извлекаем компоненты цвета
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;

                    // Собираем обратно в формате ABGR, который ожидает метод
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;

                    // Используем существующий метод
                    nativeImage.setPixelABGR(x, y, abgr);
                }
            }

            // Убираем минусы из ID (Minecraft не принимает отрицательные ID)
            String safeName = "img_" + Math.abs(name.hashCode());
            Identifier id = Identifier.fromNamespaceAndPath("servernewslist", safeName);

            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> safeName, nativeImage));

            return new NewsImage(id, width, height);

        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            if (scrollY != 0) {
                this.setScrollAmount(this.scrollAmount() - (scrollY * 10));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (NewsScreen.isRefreshing()) {
            return super.mouseClicked(event, doubleClick);
        }

        var font = Minecraft.getInstance().font;

        for (NewsEntry entry : this.children()) {
            // Сдвинули на 8 пикселей выше

            if (!entry.allowRating) continue;
            int btnY = entry.getY() + entry.getHeight() - 28;

            String likeStr = String.valueOf(entry.likes);
            String dislikeStr = String.valueOf(entry.dislikes);
            int likeW = font.width(likeStr);
            int dislikeW = font.width(dislikeStr);

            // Сдвинули на 4 пикселя правее
            int rightEdge = entry.getX() + entry.getWidth() - 10 - 1;

            int dislikeTextX = rightEdge - dislikeW;
            int dislikeIconX = dislikeTextX - 16 - 2;
            int likeTextX = dislikeIconX - 20 - likeW;
            int likeIconX = likeTextX - 16 - 2;

            // Клик по Лайку
            if (event.x() >= likeIconX && event.x() <= likeTextX + likeW && event.y() >= btnY && event.y() <= btnY + 16) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new wlamir.servernewslist.network.RateNewsPayload(entry.getTitle(), true)
                );
                return true;
            }

            // Клик по Дизлайку
            if (event.x() >= dislikeIconX && event.x() <= dislikeTextX + dislikeW && event.y() >= btnY && event.y() <= btnY + 16) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new wlamir.servernewslist.network.RateNewsPayload(entry.getTitle(), false)
                );
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public record NewsImage(Identifier id, int width, int height) {}

    public static class NewsEntry extends ObjectSelectionList.Entry<NewsEntry> {
        private final String title, text;
        private final long timestamp;
        private final List<NewsImage> images;
        private final int type;

        public int likes;
        public int dislikes;

        private final boolean allowRating;

        // Единый отступ для всего интерфейса (слева, справа, сверху, снизу)
        private static final int PADDING = 10;
        // Высота одной строки текста
        private static final int LINE_HEIGHT = 10;

        private final NewsListWidget parentList;

        public NewsEntry(String title, String text, List<NewsImage> images, long timestamp, int type, int likes, int dislikes, boolean allowRating, NewsListWidget parentWidget) {
            this.title = title;
            this.text = text;
            this.images = images;
            this.timestamp = timestamp;
            this.type = type;
            this.likes = likes;
            this.dislikes = dislikes;
            this.parentList = parentWidget;
            this.allowRating = allowRating;
        }

        public String getTitle() { return title; }

        // Вспомогательный метод для расчета размера картинки (используется в двух местах)
        private int[] calculateImageSize(NewsImage img) {
            // Берем РЕАЛЬНУЮ ширину окна и вычитаем отступы под края и скроллбар
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int maxWidth = screenWidth - 49; // 49 пикселей запаса по бокам

            // Если карточка вдруг меньше экрана, берем ширину карточки
            if (this.getWidth() > 0) {
                maxWidth = Math.min(maxWidth, this.getWidth() - (PADDING * 2));
            }

            // Защита от слишком узкого экрана
            if (maxWidth < 50) maxWidth = 50;

            int drawWidth = Math.min(maxWidth, img.width());
            int drawHeight = (int) ((float) img.height() / img.width() * drawWidth);

            return new int[]{drawWidth, drawHeight};
        }

        @Override
        public int getHeight() {
            int currentHeight = PADDING + 12 + 14; // Отступ + Заголовок + Дата

            // Разбиваем текст по маркерам
            String[] parts = text.split("\\\\\\d+");
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\\(\\d+)").matcher(text);
            int mediaIndex = 0;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (!part.isEmpty()) {
                    // Считаем количество строк с учетом переноса
                    List<String> lines = wrapText(part, this.getWidth() - (PADDING * 2));
                    currentHeight += lines.size() * LINE_HEIGHT;
                }

                if (matcher.find()) {
                    if (mediaIndex < images.size()) {
                        int[] size = calculateImageSize(images.get(mediaIndex));
                        currentHeight += size[1] + PADDING; // Высота картинки + отступ
                    }
                    mediaIndex++;
                }
            }
            int extraHeight = this.allowRating ? 25 : 0;

            return currentHeight + PADDING + extraHeight; // Нижний отступ
        }


        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {

            var font = Minecraft.getInstance().font;
            int startX = this.getX() + PADDING;
            int currentY = this.getY() + PADDING;

            int iconX = this.getX() + this.getWidth() - 16 - PADDING;
            int iconY = this.getY() + PADDING;

            int backgroundColor;
            switch (this.type) {
                case 2:  backgroundColor = 0x55304030; break; // Зеленый
                case 3:  backgroundColor = 0x55303040; break; // Голубой
                case 4:  backgroundColor = 0x55403030; break; // Красный
                case 1:
                default: backgroundColor = 0x55404040; break; // Темный (как было)
            }

            int lineColor;
            switch (this.type) {
                case 2:  lineColor = 0x558fbf8f; break; // Зеленый
                case 3:  lineColor = 0x558f8fbf; break; // Голубой
                case 4:  lineColor = 0x55bf8f8f; break; // Красный
                case 1:
                default: lineColor = 0x55bfbfbf; break; // Темный (как было)
            }

            // Фон карточки
            graphics.fill(this.getX(), this.getY()+1, this.getX() + this.getWidth(), this.getY() + this.getHeight() - 4 - 1, backgroundColor);

            int lineX = this.getX() + this.getWidth() + 4;
            graphics.fill(lineX, this.getY(), lineX + 6, this.getY() + this.getHeight() - 4, lineColor);

            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY()+1, lineColor);
            graphics.fill(this.getX(), this.getY() + this.getHeight() - 4, this.getX() + this.getWidth(), this.getY() + this.getHeight() - 4 + 1, lineColor);


            // Заголовок и дата
            graphics.text(font, title, startX, currentY, 0xFFFFAAFF, false);
            currentY += 12;
            boolean showLocal = ServerNewsListConfig.getInstance().show_my_timezone;
            String date;

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT")); // Принудительно ставим GMT
            if (showLocal) {
                // Показываем время в часовом поясе игрока (по умолчанию Java использует локальное время)
                String local = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(timestamp));
                date = sdf.format(new java.util.Date(timestamp)) + " (" + local + ")";
            } else {
                // Показываем время по Гринвичу (UTC)
                date = sdf.format(new java.util.Date(timestamp));
            }
            graphics.text(font, date, startX, currentY, 0xAAAAAAFF, false);
            currentY += 14;

            String[] parts = text.split("\\\\\\d+");
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\\(\\d+)").matcher(text);
            int mediaIndex = 0;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (!part.isEmpty()) {
                    List<String> lines = wrapText(part, this.getWidth() - (PADDING * 2));
                    for (String line : lines) {
                        graphics.text(font, line, startX, currentY, 0xFFFFFFFF, false);
                        currentY += LINE_HEIGHT;
                    }
                }

                if (matcher.find()) {
                    if (mediaIndex < images.size()) {
                        NewsImage img = images.get(mediaIndex);

                        // Используем обновленный метод
                        int[] size = calculateImageSize(img);
                        int drawWidth = size[0];
                        int drawHeight = size[1];

                        graphics.blit(
                                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                                img.id(),
                                startX, currentY,
                                0.0F, 0.0F,
                                drawWidth, drawHeight,
                                img.width(), img.height()
                        );

                        currentY += drawHeight + PADDING;
                    }
                    mediaIndex++;
                }
            }

            int btnY = this.getY() + this.getHeight() - 28;
            int btnX = this.getX() + this.getWidth() - 70;

            String likeStr = String.valueOf(likes);
            String dislikeStr = String.valueOf(dislikes);
            int likeW = font.width(likeStr);
            int dislikeW = font.width(dislikeStr);

            int rightEdge = this.getX() + this.getWidth() - PADDING - 1;

            int dislikeTextX = rightEdge - dislikeW;
            int dislikeIconX = dislikeTextX - 16 - 2;
            int likeTextX = dislikeIconX - 20 - likeW;
            int likeIconX = likeTextX - 16 - 2;

            Identifier likeIcon = Identifier.fromNamespaceAndPath("servernewslist", "textures/gui/like.png");
            Identifier dislikeIcon = Identifier.fromNamespaceAndPath("servernewslist", "textures/gui/dislike.png");

            boolean isRefreshing = NewsScreen.isRefreshing();

            int iconColor = isRefreshing ? 0x80FFFFFF : 0xFFFFFFFF; // Белый (иконки)
            int likeColor = isRefreshing ? 0x8055FF55 : 0xFF55FF55; // Зеленый
            int dislikeColor = isRefreshing ? 0x80FF5555 : 0xFFFF5555;

            if (this.allowRating) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, likeIcon, likeIconX, btnY, 0, 0, 16, 16, 16, 16, iconColor);
            graphics.text(font, likeStr, likeTextX, btnY + 4, likeColor, false);

            // Отрисовка Дизлайка
            graphics.blit(RenderPipelines.GUI_TEXTURED, dislikeIcon, dislikeIconX, btnY, 0, 0, 16, 16, 16, 16, iconColor);
            graphics.text(font, dislikeStr, dislikeTextX, btnY + 4, dislikeColor, false);

            if (mouseX >= likeIconX && mouseX <= likeTextX + likeW && mouseY >= btnY && mouseY <= btnY + 16) {
                this.parentList.hoveredTooltip = "gui.servernewslist.like";
            }

            // Проверка наведения на Дизлайк (иконка + текст)
            if (mouseX >= dislikeIconX && mouseX <= dislikeTextX + dislikeW && mouseY >= btnY && mouseY <= btnY + 16) {
                this.parentList.hoveredTooltip = "gui.servernewslist.dislike";
            }
            }



            String iconName;
            String tooltipKey; // <--- Теперь это ключ, а не сам текст
            switch (this.type) {
                case 2:
                    iconName = "type2";
                    tooltipKey = "gui.servernewslist.type.type2";
                    break;
                case 3:
                    iconName = "type3";
                    tooltipKey = "gui.servernewslist.type.type3";
                    break;
                case 4:
                    iconName = "type4";
                    tooltipKey = "gui.servernewslist.type.type4";
                    break;
                case 1:
                default:
                    iconName = "type1";
                    tooltipKey = "gui.servernewslist.type.type1";
                    break;
            }

            // Отрисовка иконки
            Identifier typeIcon = Identifier.fromNamespaceAndPath("servernewslist", "textures/gui/icons/" + iconName + ".png");
            graphics.blit(RenderPipelines.GUI_TEXTURED, typeIcon, iconX, iconY, 0, 0, 16, 16, 16, 16);

            // Проверка наведения мыши на иконку
            if (mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16) {
                // Сохраняем текст подсказки в родительский виджет
                // (получаем доступ к родителю через this.parentList или через статическую переменную,
                // но проще всего сохранить это в статическое поле самого NewsListWidget)
                this.parentList.hoveredTooltip = tooltipKey;
            }

        }

        // Метод для переноса длинного текста на новые строки
        private List<String> wrapText(String text, int maxWidth) {
            List<String> wrappedLines = new ArrayList<>();
            var font = Minecraft.getInstance().font;

            // Сначала разбиваем по жестким переносам (Enter)
            String[] paragraphs = text.split("\\R");
            for (String paragraph : paragraphs) {
                if (paragraph.isEmpty()) {
                    wrappedLines.add("");
                    continue;
                }

                // Разбиваем абзац на слова
                String[] words = paragraph.split(" ");
                StringBuilder currentLine = new StringBuilder();

                for (String word : words) {
                    String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
                    // Если добавление слова превышает ширину - переносим
                    if (font.width(testLine) > maxWidth) {
                        if (!currentLine.isEmpty()) {
                            wrappedLines.add(currentLine.toString());
                            currentLine = new StringBuilder(word);
                        } else {
                            // Если одно слово длиннее строки (например, длинная ссылка)
                            wrappedLines.add(word);
                        }
                    } else {
                        currentLine = new StringBuilder(testLine);
                    }
                }
                if (!currentLine.isEmpty()) {
                    wrappedLines.add(currentLine.toString());
                }
            }
            return wrappedLines;
        }

        @Override
        public Component getNarration() { return Component.literal(title); }
    }
}