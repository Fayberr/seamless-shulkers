package dev.fayber.seamless.preview;

import dev.fayber.seamless.SeamlessShulkers;
import net.minecraft.network.HashedStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client asks to edit the shulker box in {@code inventorySlot} (player Inventory index) through the
 * preview slots of its inventory menu. {@code expected} is the hash of the box as the client sees
 * it, so the server never binds a different box than the one the player is looking at.
 * {@code token} identifies this binding in a later {@link PreviewReleased}. A negative slot releases.
 */
public record PreviewBindRequest(int containerId, int inventorySlot, int token, HashedStack expected)
        implements CustomPacketPayload {
    public static final Type<PreviewBindRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(SeamlessShulkers.MOD_ID, "preview_bind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewBindRequest> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, PreviewBindRequest::containerId,
            ByteBufCodecs.VAR_INT, PreviewBindRequest::inventorySlot,
            ByteBufCodecs.VAR_INT, PreviewBindRequest::token,
            HashedStack.STREAM_CODEC, PreviewBindRequest::expected, PreviewBindRequest::new);

    public static PreviewBindRequest release(int containerId, int token) {
        return new PreviewBindRequest(containerId, -1, token, HashedStack.EMPTY);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
