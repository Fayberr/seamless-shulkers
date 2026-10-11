package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.client.PreviewOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import dev.fayber.seamless.TradeRefillRequest;
import dev.fayber.seamless.client.ClientOptions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class, priority = 1500)
public abstract class ContainerScreenMixin {
    @Shadow @Nullable protected Slot hoveredSlot;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void seamless$updatePreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PreviewOverlay.update(hoveredSlot, mouseX, mouseY);
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void seamless$hideUnderlyingTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        // Called before vanilla queues a deferred tooltip (and before SBT wraps it).
        // Update the hovered shulker now, not at extractRenderState TAIL.
        if (PreviewOverlay.interceptTooltip(hoveredSlot, mouseX, mouseY)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void seamless$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (PreviewOverlay.click(event.x(), event.y(), event.button())) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void seamless$tradingSpace(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.shortcutKey() == 32 && ClientOptions.INSTANCE.tradingRefill
            && ((Object) this) instanceof MerchantScreen merchant
            && ClientPlayNetworking.canSend(TradeRefillRequest.TYPE)) {
            ClientPlayNetworking.send(new TradeRefillRequest(((MerchantScreenMixin) (Object) merchant).seamless$getSelectedOffer()));
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void seamless$removed(CallbackInfo ci) {
        PreviewOverlay.clear();
    }
}
