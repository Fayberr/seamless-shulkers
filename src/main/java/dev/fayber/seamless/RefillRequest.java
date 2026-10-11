package dev.fayber.seamless;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RefillRequest(String itemId, int targetSlot, int requestedCount) implements CustomPacketPayload {
    public static final Type<RefillRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(SeamlessShulkers.MOD_ID, "refill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefillRequest> CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RefillRequest::itemId,
            ByteBufCodecs.VAR_INT, RefillRequest::targetSlot,
            ByteBufCodecs.VAR_INT, RefillRequest::requestedCount, RefillRequest::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
