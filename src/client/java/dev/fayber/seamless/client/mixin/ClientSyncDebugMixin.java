package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.debug.PreviewDebug;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debug builds only: every inventory correction the server sends back (logged once, on the main thread). */
@Mixin(ClientPacketListener.class)
public abstract class ClientSyncDebugMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"), require = 0)
    private void seamless$debugSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        PreviewDebug.log("client: RECV set slot containerId={} slot={} item={} stateId={}", packet.getContainerId(), packet.getSlot(),
            PreviewDebug.stack(packet.getItem()), packet.getStateId());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"), require = 0)
    private void seamless$debugContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        PreviewDebug.log("client: RECV full resync containerId={} slots={} carried={} stateId={}", packet.containerId(), packet.items().size(),
            PreviewDebug.stack(packet.carriedItem()), packet.stateId());
    }

    @Inject(method = "handleSetCursorItem", at = @At("TAIL"), require = 0)
    private void seamless$debugCursor(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        PreviewDebug.log("client: RECV cursor={}", PreviewDebug.stack(packet.contents()));
    }
}
