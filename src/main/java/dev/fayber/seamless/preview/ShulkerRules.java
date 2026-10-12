package dev.fayber.seamless.preview;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Which stacks may be previewed, bound for editing, or stored inside a previewed shulker. */
public final class ShulkerRules {
    public static final int SIZE = 27;
    /** Player Inventory container index of the offhand. */
    public static final int OFFHAND_SLOT = 40;

    private ShulkerRules() {}

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem block
            && block.getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * A shulker may be edited in place only when it is a single item (writing contents into a stack
     * of several boxes would duplicate them) and its stored contents fit the 27 slots we expose
     * (anything beyond would be lost on write-back).
     */
    public static boolean isEditable(ItemStack stack) {
        if (!isShulker(stack) || stack.getCount() != 1) return false;
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        return contents.size() <= SIZE;
    }

    /** Main inventory, hotbar and offhand. Armor slots are not offered. */
    public static boolean isBindableInventorySlot(int inventorySlot) {
        return inventorySlot >= 0 && inventorySlot < 36 || inventorySlot == OFFHAND_SLOT;
    }

    /** Vanilla nesting rule, identical to ShulkerBoxSlot. */
    public static boolean mayStore(ItemStack stack) {
        return stack.isEmpty() || stack.getItem().canFitInsideContainerItems();
    }
}
