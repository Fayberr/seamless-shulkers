package dev.fayber.seamless.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fayber.seamless.debug.PreviewDebug;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debug builds only: every raw mouse button and whether the open screen consumed it. */
@Mixin(MouseHandler.class)
public abstract class ClientInputDebugMixin {
    @Inject(method = "onButton", at = @At("HEAD"), require = 0)
    private void seamless$debugRaw(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        PreviewDebug.log("client: raw mouse button={} action={} modifiers={}", info.button(), action == 1 ? "press" : "release", info.modifiers());
    }

    @WrapOperation(method = "onButton", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean seamless$debugClicked(Screen screen, MouseButtonEvent event, boolean doubleClick, Operation<Boolean> original) {
        boolean result = original.call(screen, event, doubleClick);
        PreviewDebug.log("client: {}.mouseClicked(button={} x={} y={} mods={} double={}) -> {}", screen.getClass().getName(),
            event.button(), String.format("%.1f", event.x()), String.format("%.1f", event.y()), event.modifiers(), doubleClick, result);
        return result;
    }

    @WrapOperation(method = "onButton", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
    private boolean seamless$debugReleased(Screen screen, MouseButtonEvent event, Operation<Boolean> original) {
        boolean result = original.call(screen, event);
        PreviewDebug.log("client: {}.mouseReleased(button={} x={} y={} mods={}) -> {}", screen.getClass().getName(),
            event.button(), String.format("%.1f", event.x()), String.format("%.1f", event.y()), event.modifiers(), result);
        return result;
    }
}
