package dev.fayber.seamless.client.preview;

import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Tooltip image of a shulker box: the merged compact view (Shift) or the full 9x3 grid (Shift+Alt). */
public record ShulkerTooltip(List<ItemStack> contents, boolean full, int tint) implements TooltipComponent {}
