package wlamir.servernewslist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateCheckPayload(String latestNewsId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateCheckPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("servernewslist", "update_check"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCheckPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, UpdateCheckPayload::latestNewsId,
                    UpdateCheckPayload::new
            );

    // ДОБАВЛЕНО: срабатывает при создании пакета (отправке сервером)
    public UpdateCheckPayload {
        System.out.println("UpdateCheckPayload");
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}