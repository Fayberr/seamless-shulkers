package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.preview.ShulkerPreviewContainer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The creative inventory tab copies every inventory-menu slot into its own menu and lays them out
 * by index, which would put the 27 preview slots on top of the hotbar and shift the hotbar range
 * its quick-move relies on. They are not part of the creative inventory.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin {
    @Inject(method = "selectTab", at = @At("TAIL"))
    private void seamless$dropPreviewSlots(CreativeModeTab tab, CallbackInfo ci) {
        ((AbstractContainerScreen<?>) (Object) this).getMenu().slots
            .removeIf(slot -> slot.container instanceof ShulkerPreviewContainer);
    }
}
