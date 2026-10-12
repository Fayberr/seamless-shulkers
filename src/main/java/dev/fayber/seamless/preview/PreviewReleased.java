package dev.fayber.seamless.preview;

import dev.fayber.seamless.SeamlessShulkers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server tells the client that the binding with {@code token} was refused or ended (box moved,
 * replaced or emptied out of its slot). Releases for older tokens are ignored by the client.
 */
public record PreviewReleased(int token) implements CustomPacketPayload {
    public static final Type<PreviewReleased> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(SeamlessShulkers.MOD_ID, "preview_released"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewReleased> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, PreviewReleased::token, PreviewReleased::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
