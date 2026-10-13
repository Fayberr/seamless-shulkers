package dev.fayber.seamless.preview;

import dev.fayber.seamless.SeamlessShulkers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * The 27 slots of one shulker box item lying in a slot of the open menu (the player's inventory or
 * the opened container), exposed as a real container so
 * that vanilla menu logic (pickup, place, swap, drag, double-click, quick-move) runs unchanged on
 * both the predicting client and the authoritative server.
 *
 * <p>Vanilla mutates the stack returned by {@link #getItem} in place and then calls
 * {@link #setChanged}, so the contents are cached as live stacks and written back to the shulker's
 * {@code CONTAINER} component on every change. The cache is reloaded whenever the component object
 * changes underneath (server resync, refill, another mod).
 *
 * <p>On the server the binding is anchored to the identity of the bound ItemStack: if the box is
 * moved, swapped or replaced, the binding dies instead of following the slot to another box.
 */
public final class ShulkerPreviewContainer implements Container {
    private final boolean server;
    private final NonNullList<ItemStack> items = NonNullList.withSize(ShulkerRules.SIZE, ItemStack.EMPTY);
    private @Nullable Slot source;
    private @Nullable ItemStack boundStack;
    private @Nullable Item boundItem;
    private @Nullable ItemContainerContents loadedFrom;
    private boolean invalidated;
    private int token;
    private boolean suspended;

    public ShulkerPreviewContainer(boolean server) {
        this.server = server;
    }

    /**
     * Binds to the shulker currently in menu slot {@code source}. Returns false and stays unbound if
     * it is not editable. Which slots may be sources is decided by the caller.
     */
    public boolean bind(Slot source) {
        unbind();
        if (source instanceof ShulkerPreviewSlot) return false;
        ItemStack stack = source.getItem();
        if (!ShulkerRules.isEditable(stack)) return false;
        this.source = source;
        boundStack = stack;
        boundItem = stack.getItem();
        reload(stack);
        return true;
    }

    public void unbind() {
        source = null;
        boundStack = null;
        boundItem = null;
        loadedFrom = null;
        invalidated = false;
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
    }

    /** Network token of the current binding, echoed in release notifications. */
    public int token() {
        return token;
    }

    public void setToken(int token) {
        this.token = token;
    }

    /** Menu index of the slot holding the bound box, or -1. */
    public int boundSlot() {
        Slot slot = source;
        return target() == null || slot == null ? -1 : slot.index;
    }

    /** The menu slot holding the bound box, or null when unbound. */
    public @Nullable Slot source() {
        return target() == null ? null : source;
    }

    /**
     * While suspended the box looks empty and accepts nothing, so a menu's own quick-move, which
     * often targets every slot up to {@code slots.size()}, cannot move items into it.
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public boolean isBound() {
        return target() != null;
    }

    /** True once if a binding died because its box disappeared. */
    public boolean consumeInvalidation() {
        target();
        boolean result = invalidated;
        invalidated = false;
        return result;
    }

    private @Nullable ItemStack target() {
        Slot slot = source;
        if (slot == null) return null;
        ItemStack stack = slot.getItem();
        // The client's stacks are replaced by every server resync, so identity only works
        // on the server. The client follows the slot and is corrected by the server if it diverges.
        boolean sameBox = server ? stack == boundStack : stack.getItem() == boundItem;
        if (!sameBox || !ShulkerRules.isEditable(stack)) {
            dev.fayber.seamless.debug.PreviewDebug.log("{}: binding of slot {} died (sameBox={}, now {})", server ? "server" : "client",
                slot.index, sameBox, dev.fayber.seamless.debug.PreviewDebug.stack(stack));
            unbind();
            invalidated = true;
            return null;
        }
        return stack;
    }

    /** Returns the bound box with the cache refreshed, or null when unbound. */
    private @Nullable ItemStack sync() {
        ItemStack stack = target();
        if (stack != null && stack.get(DataComponents.CONTAINER) != loadedFrom) reload(stack);
        return stack;
    }

    private void reload(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        loadedFrom = contents;
        if (contents == null) {
            for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        } else {
            contents.copyInto(items);
        }
    }

    private void write() {
        ItemStack stack = target();
        if (stack == null) return;
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        loadedFrom = stack.get(DataComponents.CONTAINER);
        Slot slot = source;
        // Marks the holder dirty: the player inventory, or a chest's block entity so it is saved.
        if (slot != null) slot.setChanged();
    }

    /** Snapshot copies of the bound contents (27 entries), for rendering. */
    public NonNullList<ItemStack> copyContents() {
        NonNullList<ItemStack> copy = NonNullList.withSize(ShulkerRules.SIZE, ItemStack.EMPTY);
        if (sync() != null) for (int i = 0; i < items.size(); i++) copy.set(i, items.get(i).copy());
        return copy;
    }

    @Override
    public int getContainerSize() {
        return ShulkerRules.SIZE;
    }

    @Override
    public boolean isEmpty() {
        if (suspended || sync() == null) return true;
        for (ItemStack item : items) if (!item.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (suspended || slot < 0 || slot >= items.size() || sync() == null) return ItemStack.EMPTY;
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (suspended || slot < 0 || slot >= items.size() || sync() == null) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) write();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (suspended || slot < 0 || slot >= items.size() || sync() == null) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) write();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        if (suspended || sync() == null) {
            // Slots refuse placement while unbound, so this means a caller bypassed mayPlace.
            if (!stack.isEmpty()) SeamlessShulkers.LOGGER.warn("Ignored write of {} into an unbound shulker preview slot", stack);
            return;
        }
        items.set(slot, stack);
        write();
    }

    @Override
    public void setChanged() {
        if (!suspended && sync() != null) write();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !suspended && isBound() && ShulkerRules.mayStore(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        // Never wipe a real shulker box through the generic Clearable path.
    }
}
