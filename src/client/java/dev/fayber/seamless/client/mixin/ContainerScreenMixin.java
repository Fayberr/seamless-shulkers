package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.client.PreviewOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow @Nullable protected Slot hoveredSlot;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void seamless$render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PreviewOverlay.render(graphics, hoveredSlot, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void seamless$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (PreviewOverlay.click(event.x(), event.y(), event.button() == 1 ? 0 : event.button() == 3 ? 1 : -1)) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void seamless$removed(CallbackInfo ci) {
        PreviewOverlay.clear();
    }
}
