package dev.fayber.seamless.client.mixin;

import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    @Nullable Slot seamless$hoveredSlot();

    @Accessor("isQuickCrafting")
    boolean seamless$isQuickCrafting();

    @Accessor("quickCraftSlots")
    Set<Slot> seamless$quickCraftSlots();

    @Accessor("quickCraftingType")
    int seamless$quickCraftingType();

    @Accessor("leftPos")
    int seamless$leftPos();

    @Accessor("topPos")
    int seamless$topPos();

    @Invoker("getHoveredSlot")
    @Nullable Slot seamless$getHoveredSlot(double x, double y);

    @Invoker("isHovering")
    boolean seamless$isHovering(int left, int top, int w, int h, double x, double y);
}
