package dev.fayber.seamless.preview;

import dev.fayber.seamless.SeamlessShulkers;
import net.minecraft.network.HashedStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client asks to edit the shulker box in menu slot {@code menuSlot} of the open menu
 * {@code containerId} through that menu's preview slots, which start at {@code firstSlot} (so both
 * sides agree on their indices). {@code expected} is the hash of the box as the client sees it, so
 * the server never binds a different box than the one the player is looking at. {@code token}
 * identifies this binding in a later {@link PreviewReleased}. A negative menu slot releases.
 *
 * <p>The id carries a version: a client and server whose requests differ simply do not see each
 * other's channel, and the client falls back to a display-only preview.
 */
public record PreviewBindRequest(int containerId, int menuSlot, int firstSlot, int token, HashedStack expected)
        implements CustomPacketPayload {
    public static final Type<PreviewBindRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(SeamlessShulkers.MOD_ID, "preview_bind_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewBindRequest> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, PreviewBindRequest::containerId,
            ByteBufCodecs.VAR_INT, PreviewBindRequest::menuSlot,
            ByteBufCodecs.VAR_INT, PreviewBindRequest::firstSlot,
            ByteBufCodecs.VAR_INT, PreviewBindRequest::token,
            HashedStack.STREAM_CODEC, PreviewBindRequest::expected, PreviewBindRequest::new);

    public static PreviewBindRequest release(int containerId, int token) {
        return new PreviewBindRequest(containerId, -1, -1, token, HashedStack.EMPTY);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
