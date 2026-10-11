package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.client.PreviewOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen performs deferred tooltip extraction AFTER extractRenderState.
 * The interactive shulker inventory must appear after that pass, not underneath.
 * This hook leaves Shulker Box Tooltip's ordinary hover tooltips untouched.
 */
@Mixin(Screen.class)
public abstract class ScreenTooltipLayerMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void seamless$drawAboveDeferredTooltips(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> && PreviewOverlay.shouldRender()) {
            graphics.nextStratum();
            PreviewOverlay.render(graphics, mouseX, mouseY);
        }
    }
}
