package dev.fayber.seamless;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StorageRequest(int inventorySlot, int innerSlot, int button) implements CustomPacketPayload {
    public static final Type<StorageRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(SeamlessShulkers.MOD_ID, "storage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageRequest> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, StorageRequest::inventorySlot,
            ByteBufCodecs.VAR_INT, StorageRequest::innerSlot,
            ByteBufCodecs.VAR_INT, StorageRequest::button, StorageRequest::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
