package dev.fayber.seamless.preview;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

/**
 * A slot of the bound shulker box. Inactive so vanilla neither draws nor hit-tests it; the client
 * preview panel positions and renders these slots itself and routes the cursor to them.
 */
public final class ShulkerPreviewSlot extends Slot {
    private final ShulkerPreviewContainer preview;

    public ShulkerPreviewSlot(ShulkerPreviewContainer preview, int slot) {
        super(preview, slot, -2000, -2000);
        this.preview = preview;
    }

    public ShulkerPreviewContainer preview() {
        return preview;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return preview.isBound() && ShulkerRules.mayStore(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return preview.isBound();
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean isHighlightable() {
        return false;
    }
}
