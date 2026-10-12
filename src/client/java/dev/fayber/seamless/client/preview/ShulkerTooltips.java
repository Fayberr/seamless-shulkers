package dev.fayber.seamless.client.preview;

import dev.fayber.seamless.client.ClientOptions;
import dev.fayber.seamless.preview.ShulkerRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Hover tooltip of shulker boxes (States A and B, and the unpinned full grid outside container
 * screens). When Shulker Box Tooltip is installed it owns these states and nothing here runs, so
 * there is never a second preview; only the pinned State C panel is ours.
 */
public final class ShulkerTooltips {
    private static final boolean SBT = FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");

    private ShulkerTooltips() {}

    public static boolean externalTooltipOwnsCompact() {
        return SBT;
    }

    private static boolean ours(ItemStack stack) {
        return !SBT && ClientOptions.INSTANCE.preview && ShulkerRules.isShulker(stack)
            && !stack.has(DataComponents.CONTAINER_LOOT) && Minecraft.getInstance().isSameThread();
    }

    /** Vanilla's "item x N" container lines are replaced by the summary and the preview image. */
    public static boolean replacesContainerLines(ItemStack stack) {
        return ours(stack);
    }

    public static Optional<TooltipComponent> image(ItemStack stack, Optional<TooltipComponent> original) {
        if (!ours(stack) || !PreviewKeys.shift() || PreviewController.hasPin()) return original;
        NonNullList<ItemStack> items = PanelRenderer.contents(stack);
        if (PanelRenderer.nonEmptyCount(items) == 0) return original;
        return Optional.of(new ShulkerTooltip(items, PreviewKeys.alt(), PanelRenderer.tintOf(stack)));
    }

    public static void lines(ItemStack stack, List<Component> lines) {
        if (!ours(stack) || lines.isEmpty()) return;
        int stacks = PanelRenderer.nonEmptyCount(PanelRenderer.contents(stack));
        List<Component> added = new ArrayList<>();
        added.add(summary(stacks).withStyle(ChatFormatting.GRAY));
        if (stacks > 0) {
            boolean shift = PreviewKeys.shift();
            if (!shift) {
                added.add(PreviewKeys.hint(PreviewKeys.shiftName(), "seamlessshulkers.hint.view"));
            } else {
                if (!PreviewKeys.alt()) added.add(PreviewKeys.hint(PreviewKeys.fullName(), "seamlessshulkers.hint.full"));
                if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> && !PreviewController.hasPin()) {
                    added.add(PreviewKeys.hint(PreviewKeys.controlName(), "seamlessshulkers.hint.lock"));
                }
            }
        }
        lines.addAll(1, added);
    }

    static net.minecraft.network.chat.MutableComponent summary(int stacks) {
        if (stacks == 0) return Component.translatable("seamlessshulkers.tooltip.empty");
        if (stacks == 1) return Component.translatable("seamlessshulkers.tooltip.contains_one");
        return Component.translatable("seamlessshulkers.tooltip.contains", stacks);
    }
}
