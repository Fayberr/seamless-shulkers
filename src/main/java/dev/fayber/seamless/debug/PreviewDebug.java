package dev.fayber.seamless.debug;

import dev.fayber.seamless.SeamlessShulkers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Diagnostic trace of every preview-related input, click and sync, written to the normal game log
 * with the prefix {@code [SS-DEBUG]}. Off in release builds. Enabled by a debug build
 * ({@code gradle build -PdebugBuild}, which ships a marker resource) or by the JVM flag
 * {@code -Dseamlessshulkers.debug=true}.
 */
public final class PreviewDebug {
    public static final boolean ENABLED = Boolean.getBoolean("seamlessshulkers.debug")
        || PreviewDebug.class.getResource("/seamlessshulkers-debug.marker") != null;

    private PreviewDebug() {}

    public static void log(String format, Object... args) {
        if (ENABLED) SeamlessShulkers.LOGGER.info("[SS-DEBUG] " + format, args);
    }

    public static String stack(ItemStack stack) {
        if (stack == null) return "null";
        if (stack.isEmpty()) return "empty";
        return stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static String slot(Slot slot) {
        if (slot == null) return "none";
        return "#" + slot.index + "(" + slot.getClass().getSimpleName() + " c" + slot.getContainerSlot() + " " + stack(slot.getItem()) + ")";
    }
}
