package wlamir.servernewslist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RateNewsPayload(String newsId, boolean isLike) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RateNewsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("servernewslist", "rate_news"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RateNewsPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RateNewsPayload::newsId,
                    ByteBufCodecs.BOOL, RateNewsPayload::isLike,
                    RateNewsPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}