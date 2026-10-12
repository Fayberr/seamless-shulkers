package dev.fayber.seamless.client.preview;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

/** Physical modifier state. Uses the same keys as Shulker Box Tooltip: Shift, Alt (+Shift), Control. */
public final class PreviewKeys {
    private PreviewKeys() {}

    public static boolean shift() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    public static boolean alt() {
        return InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
    }

    public static boolean control() {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean isControlKey(int key) {
        return key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL;
    }

    public static Component shiftName() {
        return Component.translatable("key.keyboard.left.shift");
    }

    public static Component fullName() {
        return Component.empty().append(Component.translatable("key.keyboard.left.alt")).append("+")
            .append(Component.translatable("key.keyboard.left.shift"));
    }

    public static Component controlName() {
        return Component.translatable("key.keyboard.left.control");
    }

    /** "Key: action" with the key in gold and the action in white, like Shulker Box Tooltip's hints. */
    public static Component hint(Component key, String actionKey) {
        MutableComponent line = Component.empty();
        line.append(Component.empty().append(key).append(": ").withStyle(ChatFormatting.GOLD));
        line.append(Component.translatable(actionKey).withStyle(ChatFormatting.WHITE));
        return line;
    }
}
