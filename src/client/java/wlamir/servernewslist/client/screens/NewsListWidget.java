package wlamir.servernewslist.client.screens;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import wlamir.servernewslist.client.NewsCacheManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

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

    public NewsListWidget(Minecraft client, int width, int height, int top, int bottom) {
        super(client, width, height, top, bottom);
        loadFromCache();
    }

    private void loadFromCache() {
        String json = NewsCacheManager.loadCache();
        if (json == null) return;
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String title = obj.get("title").getAsString();
                String text = obj.get("text").getAsString();
                long timestamp = obj.get("timestamp").getAsLong();

                int newsType = obj.has("type") ? obj.get("type").getAsInt() : 1;

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

                addEntry(new NewsEntry(title, text, textures, timestamp, newsType));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private NewsImage loadTextureFromBase64(String name, String base64) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));

            // Защита от пустых или битых файлов
            if (bufferedImage == null || bufferedImage.getWidth() <= 0 || bufferedImage.getHeight() <= 0) {
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
            System.out.println("Не удалось загрузить картинку: " + name);
            e.printStackTrace(); // Выведет ошибку в консоль, если что-то не так
            return null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            if (scrollY != 0) {
                this.setScrollAmount(this.scrollAmount() - (scrollY * 20));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public record NewsImage(Identifier id, int width, int height) {}

    public static class NewsEntry extends ObjectSelectionList.Entry<NewsEntry> {
        private final String title, text;
        private final long timestamp;
        private final List<NewsImage> images;
        private final int type;

        // Единый отступ для всего интерфейса (слева, справа, сверху, снизу)
        private static final int PADDING = 10;
        // Высота одной строки текста
        private static final int LINE_HEIGHT = 10;

        public NewsEntry(String title, String text, List<NewsImage> images, long timestamp, int type) {
            this.title = title;
            this.text = text;
            this.images = images;
            this.timestamp = timestamp;
            this.type = type;
        }

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

            return currentHeight + PADDING; // Нижний отступ
        }


        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            var font = Minecraft.getInstance().font;
            int startX = this.getX() + PADDING;
            int currentY = this.getY() + PADDING;

            int backgroundColor;
            switch (this.type) {
                case 2:  backgroundColor = 0x55008000; break; // Зеленый
                case 3:  backgroundColor = 0x550088FF; break; // Голубой
                case 4:  backgroundColor = 0x55CC0000; break; // Красный
                case 1:
                default: backgroundColor = 0x55000000; break; // Темный (как было)
            }

            // Фон карточки
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight() - 4, backgroundColor);

            // Заголовок и дата
            graphics.text(font, title, startX, currentY, 0xFFFFAAFF, false);
            currentY += 12;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT")); // Принудительно ставим GMT
            String local = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(timestamp));
            String date = sdf.format(new java.util.Date(timestamp)) + " (" + local + ")";
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