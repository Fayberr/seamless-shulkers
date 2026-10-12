package dev.fayber.seamless.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fayber.seamless.preview.PreviewMenu;
import dev.fayber.seamless.preview.PreviewServer;
import dev.fayber.seamless.preview.ShulkerPreviewSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preview slots never travel over the network. Their contents are the bound shulker's CONTAINER
 * component, which is synced through the ordinary inventory slot holding the box. This keeps
 * vanilla clients (46 inventory slots) working on a server with the mod, and stops a server
 * resync from writing into a client-side shulker through these slots.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;

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
        if ((Object) this instanceof InventoryMenu menu && (Object) this instanceof PreviewMenu preview
            && ((InventoryMenuAccessor) menu).seamless$owner() instanceof ServerPlayer owner
            && preview.seamless$preview().consumeInvalidation()) {
            PreviewServer.notifyReleased(owner, preview.seamless$preview().token());
        }
    }
}
