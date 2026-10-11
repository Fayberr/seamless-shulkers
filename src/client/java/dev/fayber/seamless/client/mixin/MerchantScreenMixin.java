package dev.fayber.seamless.client.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantScreenMixin {
    @Accessor("shopItem")
    int seamless$getSelectedOffer();
}
