package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.client.PreviewOverlay;
import net.minecraft.client.gui.GuiGraphics;
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

    @Inject(method = "render", at = @At("TAIL"))
    private void seamless$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PreviewOverlay.render(graphics, hoveredSlot, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void seamless$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (PreviewOverlay.click(mouseX, mouseY, button)) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void seamless$removed(CallbackInfo ci) {
        PreviewOverlay.clear();
    }
}
