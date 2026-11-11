package wlamir.servernewslist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NewsResponsePayload(String data, int chunkIndex, int totalChunks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NewsResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("servernewslist", "news_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NewsResponsePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, NewsResponsePayload::data,
                    ByteBufCodecs.VAR_INT, NewsResponsePayload::chunkIndex,
                    ByteBufCodecs.VAR_INT, NewsResponsePayload::totalChunks,
                    NewsResponsePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}