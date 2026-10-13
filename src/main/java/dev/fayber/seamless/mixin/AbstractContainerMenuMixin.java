package dev.fayber.seamless.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fayber.seamless.preview.PreviewMenu;
import dev.fayber.seamless.debug.PreviewDebug;
import dev.fayber.seamless.preview.PreviewServer;
import dev.fayber.seamless.preview.ShulkerPreviewContainer;
import dev.fayber.seamless.preview.ShulkerPreviewSlot;
import dev.fayber.seamless.preview.ShulkerRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hosts the shulker preview slots of any menu (see {@link PreviewMenu}) and routes Shift-clicks
 * around them in menus other than the player's inventory.
 *
 * <p>Preview slots never travel over the network. Their contents are the bound shulker's CONTAINER
 * component, which is synced through the ordinary inventory slot holding the box. This keeps
 * vanilla clients (46 inventory slots) working on a server with the mod, and stops a server
 * resync from writing into a client-side shulker through these slots.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements PreviewMenu {
    @Shadow @Final public NonNullList<Slot> slots;

    @Shadow protected abstract Slot addSlot(Slot slot);

    @Unique private @Nullable ShulkerPreviewContainer seamless$preview;
    @Unique private int seamless$firstSlot = -1;
    @Unique private @Nullable ServerPlayer seamless$owner;

    @Override
    public @Nullable ShulkerPreviewContainer seamless$preview() {
        return seamless$preview;
    }

    @Override
    public int seamless$firstPreviewSlot() {
        return seamless$firstSlot;
    }

    @Override
    public int seamless$nextFirstPreviewSlot() {
        return seamless$preview != null ? seamless$firstSlot : slots.size();
    }

    @Override
    public ShulkerPreviewContainer seamless$ensurePreview(boolean server) {
        ShulkerPreviewContainer preview = seamless$preview;
        if (preview != null) return preview;
        preview = new ShulkerPreviewContainer(server);
        seamless$preview = preview;
        seamless$firstSlot = slots.size();
        for (int i = 0; i < ShulkerRules.SIZE; i++) addSlot(new ShulkerPreviewSlot(preview, i));
        return preview;
    }

    @Override
    public void seamless$setPreviewOwner(@Nullable ServerPlayer owner) {
        seamless$owner = owner;
    }

    @Inject(method = "synchronizeSlotToRemote", at = @At("HEAD"), cancellable = true)
    private void seamless$keepPreviewLocal(int slot, ItemStack current, Supplier<ItemStack> copy, CallbackInfo ci) {
        if (slots.get(slot) instanceof ShulkerPreviewSlot) ci.cancel();
    }

    @ModifyArg(method = "sendAllDataToRemote", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/ContainerSynchronizer;sendInitialData(Lnet/minecraft/world/inventory/AbstractContainerMenu;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;[I)V"),
        index = 1)
    private List<ItemStack> seamless$stripPreviewSlots(List<ItemStack> items) {
        int first = -1;
        boolean tail = true;
        for (int i = 0; i < slots.size() && i < items.size(); i++) {
            boolean preview = slots.get(i) instanceof ShulkerPreviewSlot;
            if (preview && first < 0) first = i;
            if (!preview && first >= 0) tail = false;
        }
        if (first < 0) return items;
        if (tail) return new ArrayList<>(items.subList(0, first));
        // Another mod appended slots after ours: keep indices aligned, send nothing real.
        List<ItemStack> blanked = new ArrayList<>(items);
        for (int i = first; i < blanked.size(); i++) {
            if (slots.get(i) instanceof ShulkerPreviewSlot) blanked.set(i, ItemStack.EMPTY);
        }
        return blanked;
    }

    @WrapOperation(method = {"setItem", "initializeContents"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/Slot;set(Lnet/minecraft/world/item/ItemStack;)V"))
    private void seamless$ignoreRemotePreviewWrites(Slot slot, ItemStack stack, Operation<Void> original) {
        if (!(slot instanceof ShulkerPreviewSlot)) original.call(slot, stack);
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void seamless$reportLostBinding(CallbackInfo ci) {
        ShulkerPreviewContainer preview = seamless$preview;
        ServerPlayer owner = seamless$owner;
        if (preview != null && owner != null && preview.consumeInvalidation()) {
            PreviewServer.notifyReleased(owner, preview.token());
        }
    }

    /** Closing a menu ends its binding (InventoryMenu and the vanilla container menus call super). */
    @Inject(method = "removed", at = @At("HEAD"))
    private void seamless$releaseOnClose(Player player, CallbackInfo ci) {
        if (seamless$preview != null) seamless$preview.unbind();
    }

    /**
     * Shift-click in a container menu while a box is bound. The player's inventory menu has its own
     * routing (InventoryMenuMixin), everything else is handled here:
     * <ul>
     * <li>A preview slot of a box in the player's inventory moves to the container, using the
     *     destination slots' placement rules.</li>
     * <li>A preview slot of a box lying in the container moves to the player's inventory.</li>
     * <li>Any other slot keeps the menu's own behavior; the box is suspended meanwhile so menus
     *     that target "every slot after the container" do not put things into it.</li>
     * </ul>
     */
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack seamless$routeQuickMove(AbstractContainerMenu menu, Player player, int slotIndex, Operation<ItemStack> original) {
        ShulkerPreviewContainer preview = seamless$preview;
        if (preview == null || (Object) this instanceof InventoryMenu) return original.call(menu, player, slotIndex);
        int first = seamless$firstSlot;
        if (slotIndex >= first && slotIndex < first + ShulkerRules.SIZE) {
            Slot source = preview.source();
            if (source == null) return ItemStack.EMPTY;
            return seamless$moveToTargets(player, slotIndex, source.container instanceof Inventory);
        }
        preview.setSuspended(true);
        try {
            return original.call(menu, player, slotIndex);
        } finally {
            preview.setSuspended(false);
        }
    }

    /**
     * Moves preview contents into the opposite inventory using the real slots' placement rules.
     * Container targets run forwards; player targets run backwards, like a chest's Shift-click.
     * Result slots reject placement, and the source box is excluded. Matching stacks fill first.
     */
    @Unique
    private ItemStack seamless$moveToTargets(Player player, int slotIndex, boolean toContainer) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        List<Slot> targets = new ArrayList<>();
        for (Slot target : slots) {
            if (target instanceof ShulkerPreviewSlot || target == seamless$preview.source()) continue;
            boolean playerSlot = target.container == player.getInventory();
            if (toContainer ? !playerSlot : playerSlot && target.getContainerSlot() >= 0
                && target.getContainerSlot() < Inventory.INVENTORY_SIZE) targets.add(target);
        }
        boolean moved = false;
        for (int step = 0; step < targets.size() && !stack.isEmpty(); step++) {
            Slot target = targets.get(toContainer ? step : targets.size() - 1 - step);
            ItemStack there = target.getItem();
            if (there.isEmpty() || !ItemStack.isSameItemSameComponents(stack, there) || !target.mayPlace(stack)) continue;
            int room = target.getMaxStackSize(there) - there.getCount();
            if (room <= 0) continue;
            int count = Math.min(room, stack.getCount());
            stack.shrink(count);
            there.grow(count);
            target.setChanged();
            moved = true;
        }
        for (int step = 0; step < targets.size() && !stack.isEmpty(); step++) {
            Slot target = targets.get(toContainer ? step : targets.size() - 1 - step);
            if (target.hasItem() || !target.mayPlace(stack)) continue;
            target.setByPlayer(stack.split(Math.min(stack.getCount(), target.getMaxStackSize(stack))));
            target.setChanged();
            moved = true;
            break;
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }
        PreviewDebug.log("{}: quick-move out of box in container, slot {} moved {} of {}", player.level().isClientSide() ? "client" : "server",
            slotIndex, original.getCount() - stack.getCount(), PreviewDebug.stack(original));
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
