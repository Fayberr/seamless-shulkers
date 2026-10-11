package dev.fayber.seamless;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DirectArmRequest(int shulkerSlot, int contentSlot, int hotbarSlot) implements CustomPacketPayload {
    public static final Type<DirectArmRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SeamlessShulkers.MOD_ID, "direct_arm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DirectArmRequest> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DirectArmRequest::shulkerSlot,
                    ByteBufCodecs.VAR_INT, DirectArmRequest::contentSlot,
                    ByteBufCodecs.VAR_INT, DirectArmRequest::hotbarSlot,
                    DirectArmRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
