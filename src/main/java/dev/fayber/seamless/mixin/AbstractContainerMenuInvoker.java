package dev.fayber.seamless.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {
    @Invoker("addSlot")
    Slot seamless$addSlot(Slot slot);

    @Invoker("moveItemStackTo")
    boolean seamless$moveItemStackTo(ItemStack stack, int startSlot, int endSlot, boolean backwards);
}
