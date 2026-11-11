package wlamir.servernewslist.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.List;

public class GifRenderer {
    private final Identifier[] frameTextures;
    private final int frameDelayMs;
    private final long startTime;

    public GifRenderer(List<NativeImage> frames, int frameDelayMs) {
        this.frameDelayMs = frameDelayMs;
        this.frameTextures = new Identifier[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            Identifier id = Identifier.fromNamespaceAndPath("servernewslist", "gif/frame_" + i);
            // ИСПРАВЛЕНИЕ: добавлен Supplier<String> в конструктор
            Minecraft.getInstance().getTextureManager()
                    .register(id, new DynamicTexture(() -> "gif_frame_" + id.getPath(), frames.get(i)));
            frameTextures[i] = id;
        }
        this.startTime = System.currentTimeMillis();
    }

    public Identifier getCurrentFrame() {
        if (frameTextures.length == 0) return null;
        int index = (int) ((System.currentTimeMillis() - startTime) / frameDelayMs) % frameTextures.length;
        return frameTextures[index];
    }
}