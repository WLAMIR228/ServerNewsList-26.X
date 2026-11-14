package wlamir.servernewslist.client.mixin;

import wlamir.servernewslist.client.screens.NewsScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screens.Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addNewsButton(CallbackInfo ci) {
        // Теперь this.addRenderableWidget доступен, так как мы внутри наследника Screen
        this.addRenderableWidget(Button.builder(Component.translatable("gui.servernewslist.button"),
                        button -> Minecraft.getInstance().gui.setScreen(new NewsScreen((TitleScreen) (Object) this)))
                .bounds(this.width / 2 + 50, this.height / 4 + 48 + 24 + 24 , 100, 20)
                .build());
    }
}