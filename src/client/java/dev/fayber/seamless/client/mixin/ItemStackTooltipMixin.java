package dev.fayber.seamless.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fayber.seamless.client.preview.ShulkerTooltips;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Replaces vanilla's shulker content lines with the summary and the preview image (only without Shulker Box Tooltip). */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @WrapOperation(method = "addDetailsToTooltip", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V"))
    private void seamless$containerLines(ItemStack stack, DataComponentType<?> type, Item.TooltipContext context,
            TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag, Operation<Void> original) {
        if (type == DataComponents.CONTAINER && ShulkerTooltips.replacesContainerLines(stack)) return;
        original.call(stack, type, context, display, lines, flag);
    }

    @ModifyReturnValue(method = "getTooltipImage", at = @At("RETURN"))
    private Optional<TooltipComponent> seamless$previewImage(Optional<TooltipComponent> original) {
        return ShulkerTooltips.image((ItemStack) (Object) this, original);
    }
}
