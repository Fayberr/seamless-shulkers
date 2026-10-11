package dev.fayber.seamless;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TradeRefillRequest(int offerIndex) implements CustomPacketPayload {
    public static final Type<TradeRefillRequest> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(SeamlessShulkers.MOD_ID, "trade_refill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeRefillRequest> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, TradeRefillRequest::offerIndex,
            TradeRefillRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
