package dev.fayber.seamless.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fayber.seamless.TradeRefillRequest;
import dev.fayber.seamless.client.ClientOptions;
import dev.fayber.seamless.client.preview.PreviewController;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the pinned shulker panel into vanilla's container screen. Mouse handling is not replaced:
 * vanilla resolves the hovered slot (a preview slot inside the panel), picks the click type and
 * sends the click, so pickup, place, swap, drag and double-click behave exactly as for any slot.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 1500)
public abstract class ContainerScreenMixin {
    @Unique
    private AbstractContainerScreen<?> seamless$self() {
        return (AbstractContainerScreen<?>) (Object) this;
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void seamless$pinnedHover(double x, double y, CallbackInfoReturnable<Slot> cir) {
        if (PreviewController.isPinned(seamless$self())) cir.setReturnValue(PreviewController.hoveredSlot(seamless$self(), x, y));
    }

    /** A click on the panel is inside the GUI even where the panel extends past it (no -999 drop). */
    @WrapOperation(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
    private boolean seamless$panelIsInside(AbstractContainerScreen<?> screen, double x, double y, int left, int top,
            Operation<Boolean> original) {
        return !PreviewController.inside(screen, x, y) && original.call(screen, x, y, left, top);
    }

    /** Shift held for Shift+Alt is part of the preview gesture, not a quick-move request. */
    @WrapOperation(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/input/MouseButtonEvent;hasShiftDown()Z"))
    private boolean seamless$shiftIsGesture(MouseButtonEvent event, Operation<Boolean> original) {
        return !PreviewController.suppressShift(seamless$self()) && original.call(event);
    }

    /** Widgets under the panel (recipe book button and the like) never see its clicks. */
    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean seamless$widgetsBehindPanel(AbstractContainerScreen<?> screen, MouseButtonEvent event, boolean doubleClick,
            Operation<Boolean> original) {
        return !PreviewController.inside(screen, event.x(), event.y()) && original.call(screen, event, doubleClick);
    }

    @Inject(method = "extractCarriedItem", at = @At("HEAD"))
    private void seamless$drawPinned(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        PreviewController.frame(seamless$self(), mouseX, mouseY);
        PreviewController.render(seamless$self(), graphics, mouseX, mouseY);
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void seamless$pinnedTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (PreviewController.tooltip(seamless$self(), graphics, mouseX, mouseY)) ci.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void seamless$keys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        PreviewController.keyPressed(seamless$self(), event.key());
        if (event.shortcutKey() == 32 && ClientOptions.INSTANCE.tradingRefill
            && ((Object) this) instanceof MerchantScreen merchant
            && ClientPlayNetworking.canSend(TradeRefillRequest.TYPE)) {
            ClientPlayNetworking.send(new TradeRefillRequest(((MerchantScreenMixin) (Object) merchant).seamless$getSelectedOffer()));
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void seamless$removed(CallbackInfo ci) {
        PreviewController.screenRemoved(seamless$self());
    }
}
