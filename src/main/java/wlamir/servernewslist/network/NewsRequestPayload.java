package wlamir.servernewslist.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NewsRequestPayload() implements CustomPayload {
    public static final CustomPayload.Type<NewsRequestPayload> TYPE =
            new CustomPayload.Type<>(Identifier.of("servernews", "news_request"));

    public static final PacketCodec<RegistryByteBuf, NewsRequestPayload> CODEC =
            PacketCodec.unit(new NewsRequestPayload());

    @Override
    public CustomPayload.Type<? extends CustomPayload> type() { return TYPE; }
}