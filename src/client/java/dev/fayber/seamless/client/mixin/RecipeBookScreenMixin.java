package dev.fayber.seamless.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fayber.seamless.client.preview.PreviewController;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The recipe book sits next to the inventory; it must not take clicks or drags aimed at the panel. */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookScreenMixin {
    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean seamless$bookClick(RecipeBookComponent<?> book, MouseButtonEvent event, boolean doubleClick,
            Operation<Boolean> original) {
        return !PreviewController.inside((AbstractContainerScreen<?>) (Object) this, event.x(), event.y())
            && original.call(book, event, doubleClick);
    }

    @WrapOperation(method = "mouseDragged", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
    private boolean seamless$bookDrag(RecipeBookComponent<?> book, MouseButtonEvent event, double dx, double dy,
            Operation<Boolean> original) {
        return !PreviewController.inside((AbstractContainerScreen<?>) (Object) this, event.x(), event.y())
            && original.call(book, event, dx, dy);
    }
}
