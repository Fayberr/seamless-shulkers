package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.debug.PreviewDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debug builds only: every container click the client predicts and sends, with the result of its prediction. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientClickDebugMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), require = 0)
    private void seamless$debugSend(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (!PreviewDebug.ENABLED) return;
        AbstractContainerMenu menu = player.containerMenu;
        PreviewDebug.log("client: SEND click containerId={} (menu {} id={}) slot={} button={} input={} slotBefore={} carriedBefore={} screen={}",
            containerId, menu.getClass().getSimpleName(), menu.containerId, slot, button, input,
            slot >= 0 && slot < menu.slots.size() ? PreviewDebug.slot(menu.slots.get(slot)) : "-", PreviewDebug.stack(menu.getCarried()),
            Minecraft.getInstance().gui.screen() == null ? "none" : Minecraft.getInstance().gui.screen().getClass().getName());
    }

    @Inject(method = "handleContainerInput", at = @At("RETURN"), require = 0)
    private void seamless$debugPredicted(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (!PreviewDebug.ENABLED) return;
        AbstractContainerMenu menu = player.containerMenu;
        PreviewDebug.log("client: predicted slotAfter={} carriedAfter={}",
            slot >= 0 && slot < menu.slots.size() ? PreviewDebug.slot(menu.slots.get(slot)) : "-", PreviewDebug.stack(menu.getCarried()));
    }
}
