package dev.fayber.seamless.mixin;

import dev.fayber.seamless.preview.PreviewMenu;
import dev.fayber.seamless.preview.ShulkerPreviewContainer;
import dev.fayber.seamless.preview.ShulkerPreviewSlot;
import dev.fayber.seamless.preview.ShulkerRules;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Appends the 27 shulker preview slots to the player's inventory menu on both logical sides, so a
 * pinned preview is just more slots of the open menu and every click goes through vanilla code.
 * High priority so the slots land after other mods' additions where possible.
 */
@Mixin(value = InventoryMenu.class, priority = 2000)
public abstract class InventoryMenuMixin implements PreviewMenu {
    @Unique private ShulkerPreviewContainer seamless$preview;
    @Unique private int seamless$firstSlot = -1;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void seamless$addPreviewSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        // InventoryMenu is constructed with active == !level.isClientSide()
        seamless$preview = new ShulkerPreviewContainer(inventory, active);
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        seamless$firstSlot = self.slots.size();
        for (int i = 0; i < ShulkerRules.SIZE; i++) {
            ((AbstractContainerMenuInvoker) self).seamless$addSlot(new ShulkerPreviewSlot(seamless$preview, i));
        }
    }

    @Override
    public ShulkerPreviewContainer seamless$preview() {
        return seamless$preview;
    }

    @Override
    public int seamless$firstPreviewSlot() {
        return seamless$firstSlot;
    }

    /**
     * Shift-click like a chest: shulker slots go to the player inventory (hotbar first, from the
     * right), inventory and offhand slots go into the bound shulker unless it cannot hold them.
     * Everything else is vanilla.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void seamless$quickMove(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        int first = seamless$firstSlot;
        int end = first + ShulkerRules.SIZE;
        if (slotIndex >= first && slotIndex < end) {
            cir.setReturnValue(seamless$move(player, slotIndex, InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END, true));
        } else if (slotIndex >= InventoryMenu.INV_SLOT_START && slotIndex <= InventoryMenu.SHIELD_SLOT && seamless$preview.isBound()
            && ShulkerRules.mayStore(((AbstractContainerMenu) (Object) this).slots.get(slotIndex).getItem())) {
            // Items a shulker cannot hold (other shulker boxes) keep the vanilla inventory/hotbar route.
            cir.setReturnValue(seamless$move(player, slotIndex, first, end, false));
        }
    }

    @Unique
    private ItemStack seamless$move(Player player, int slotIndex, int start, int end, boolean backwards) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot slot = self.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!((AbstractContainerMenuInvoker) self).seamless$moveItemStackTo(stack, start, end, backwards)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void seamless$release(Player player, CallbackInfo ci) {
        seamless$preview.unbind();
    }
}
