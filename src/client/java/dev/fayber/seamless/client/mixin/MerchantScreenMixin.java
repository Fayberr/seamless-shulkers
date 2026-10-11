package dev.fayber.seamless.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;

// Reserved integration point. Trading refills must match the exact selected offer,
// including both costs, before any automatic transfer occurs.
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {}
