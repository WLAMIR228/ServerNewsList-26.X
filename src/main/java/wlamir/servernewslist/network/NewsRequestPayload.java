package wlamir.servernewslist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NewsRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NewsRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("servernewslist", "news_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NewsRequestPayload> CODEC =
            StreamCodec.unit(new NewsRequestPayload());

    // ДОБАВЛЕНО: срабатывает при создании пакета (отправке клиентом)
    public NewsRequestPayload {
        System.out.println("NewsRequestPayload");
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}