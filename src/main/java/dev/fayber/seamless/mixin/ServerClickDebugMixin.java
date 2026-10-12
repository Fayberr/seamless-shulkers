package dev.fayber.seamless.mixin;

import dev.fayber.seamless.debug.PreviewDebug;
import dev.fayber.seamless.preview.PreviewMenu;
import dev.fayber.seamless.preview.ShulkerPreviewContainer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debug builds only: traces every container click the server receives and what it did to the preview. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerClickDebugMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClick", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void seamless$debugBefore(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!PreviewDebug.ENABLED) return;
        AbstractContainerMenu menu = player.containerMenu;
        int slot = packet.slotNum();
        PreviewDebug.log("server: click recv containerId={} (open {} id={}) slot={} button={} input={} stateId={} (server {}) slotNow={} carried={} {}",
            packet.containerId(), menu.getClass().getSimpleName(), menu.containerId, slot, packet.buttonNum(), packet.containerInput(),
            packet.stateId(), menu.getStateId(), slot >= 0 && slot < menu.slots.size() ? PreviewDebug.slot(menu.slots.get(slot)) : "-",
            PreviewDebug.stack(menu.getCarried()), previewState());
    }

    @Inject(method = "handleContainerClick", require = 0, at = @At("RETURN"))
    private void seamless$debugAfter(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!PreviewDebug.ENABLED) return;
        AbstractContainerMenu menu = player.containerMenu;
        int slot = packet.slotNum();
        PreviewDebug.log("server: click done slot={} slotNow={} carried={} {}", slot,
            slot >= 0 && slot < menu.slots.size() ? PreviewDebug.slot(menu.slots.get(slot)) : "-",
            PreviewDebug.stack(menu.getCarried()), previewState());
    }

    private String previewState() {
        ShulkerPreviewContainer preview = ((PreviewMenu) player.inventoryMenu).seamless$preview();
        return "preview[bound=" + preview.isBound() + " slot=" + preview.boundSlot() + " token=" + preview.token()
            + " firstSlot=" + ((PreviewMenu) player.inventoryMenu).seamless$firstPreviewSlot()
            + " menuSlots=" + player.inventoryMenu.slots.size() + "]";
    }
}
