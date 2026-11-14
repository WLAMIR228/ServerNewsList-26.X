package wlamir.servernewslist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RatingUpdatePayload(String newsId, int likes, int dislikes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RatingUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("servernewslist", "rating_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RatingUpdatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RatingUpdatePayload::newsId,
                    ByteBufCodecs.VAR_INT, RatingUpdatePayload::likes,
                    ByteBufCodecs.VAR_INT, RatingUpdatePayload::dislikes,
                    RatingUpdatePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}