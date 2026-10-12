package dev.fayber.seamless.client.preview;

import dev.fayber.seamless.preview.PreviewLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jspecify.annotations.Nullable;

/** Draws the container-style slot panel used by the tooltip image and the pinned preview. */
public final class PanelRenderer {
    public static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    public static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
    /** Default (undyed) shulker purple. */
    private static final int UNDYED = 0x976797;
    /** Translucent white vanilla puts over a slot that a drag will fill. */
    public static final int DRAG_GHOST = 0x80FFFFFF;

    private PanelRenderer() {}

    public static int tintOf(ItemStack box) {
        if (box.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock block) {
            DyeColor color = block.getColor();
            if (color != null) return PreviewLayout.clampTint(color.getTextureDiffuseColor() & 0xFFFFFF);
        }
        return UNDYED;
    }

    /** The box's 27 slots as copies, in original order. */
    public static NonNullList<ItemStack> contents(ItemStack box) {
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        ItemContainerContents contents = box.get(DataComponents.CONTAINER);
        if (contents != null) {
            if (contents.size() <= items.size()) {
                contents.copyInto(items);
            } else {
                List<ItemStack> all = contents.itemCopies().toList();
                for (int i = 0; i < items.size(); i++) items.set(i, all.get(i));
            }
        }
        return items;
    }

    public static int nonEmptyCount(List<ItemStack> items) {
        int n = 0;
        for (ItemStack item : items) if (!item.isEmpty()) n++;
        return n;
    }

    public static List<PreviewLayout.Merged<ItemStack>> merge(List<ItemStack> items) {
        return PreviewLayout.merge(new ArrayList<>(items), ItemStack::isSameItemSameComponents,
            stack -> stack.isEmpty() ? 0 : stack.getCount());
    }

    /** Panel background and slot boxes; the panel is {@link PreviewLayout#panelWidth} by {@link PreviewLayout#panelHeight}. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int columns, int rows, int tint) {
        int w = PreviewLayout.panelWidth(columns);
        int h = PreviewLayout.panelHeight(rows);
        int black = 0xFF000000;
        int light = PreviewLayout.tint(0xFFFFFFFF, tint);
        int body = PreviewLayout.tint(0xFFC6C6C6, tint);
        int shade = PreviewLayout.tint(0xFF555555, tint);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        g.fill(x + 1, y, x + w - 1, y + 1, black);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, black);
        g.fill(x, y + 1, x + 1, y + h - 1, black);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, black);
        g.fill(x + 1, y + 1, x + w - 3, y + 3, light);
        g.fill(x + 1, y + 3, x + 3, y + h - 3, light);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, shade);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 3, shade);
        int slotDark = PreviewLayout.tint(0xFF373737, tint);
        int slotFill = PreviewLayout.tint(0xFF8B8B8B, tint);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int sx = x + PreviewLayout.BORDER + column * PreviewLayout.SLOT;
                int sy = y + PreviewLayout.BORDER + row * PreviewLayout.SLOT;
                g.fill(sx, sy, sx + 18, sy + 18, slotFill);
                g.fill(sx, sy, sx + 17, sy + 1, slotDark);
                g.fill(sx, sy + 1, sx + 1, sy + 17, slotDark);
                g.fill(sx + 1, sy + 17, sx + 18, sy + 18, light);
                g.fill(sx + 17, sy + 1, sx + 18, sy + 17, light);
            }
        }
    }

    /** Item position (top-left of the 16x16 icon) of slot {@code index} in a panel at x/y. */
    public static int itemX(int panelX, int index, int columns) {
        return panelX + PreviewLayout.BORDER + 1 + (index % columns) * PreviewLayout.SLOT;
    }

    public static int itemY(int panelY, int index, int columns) {
        return panelY + PreviewLayout.BORDER + 1 + (index / columns) * PreviewLayout.SLOT;
    }

    public static void highlightBack(GuiGraphicsExtractor g, int itemX, int itemY) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, itemX - 4, itemY - 4, 24, 24);
    }

    public static void highlightFront(GuiGraphicsExtractor g, int itemX, int itemY) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, itemX - 4, itemY - 4, 24, 24);
    }

    public static void item(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, @Nullable String count) {
        if (stack.isEmpty()) return;
        g.item(stack, x, y);
        g.itemDecorations(font, stack, x, y, count);
    }

    /** Full grid, original order, empty slots kept. */
    public static void fullGrid(GuiGraphicsExtractor g, Font font, List<ItemStack> items, int x, int y, int tint, int hovered) {
        int columns = PreviewLayout.FULL_COLUMNS;
        panel(g, x, y, columns, PreviewLayout.FULL_ROWS, tint);
        for (int i = 0; i < Math.min(items.size(), columns * PreviewLayout.FULL_ROWS); i++) {
            int ix = itemX(x, i, columns);
            int iy = itemY(y, i, columns);
            if (i == hovered) highlightBack(g, ix, iy);
            item(g, font, items.get(i), ix, iy, null);
            if (i == hovered) highlightFront(g, ix, iy);
        }
    }

    /** Compact grid: merged entries, largest first, empty slots hidden. */
    public static void compactGrid(GuiGraphicsExtractor g, Font font, List<PreviewLayout.Merged<ItemStack>> entries,
            int x, int y, int tint, int hovered) {
        int columns = PreviewLayout.compactColumns(entries.size());
        panel(g, x, y, columns, PreviewLayout.compactRows(entries.size()), tint);
        for (int i = 0; i < entries.size(); i++) {
            PreviewLayout.Merged<ItemStack> entry = entries.get(i);
            int ix = itemX(x, i, columns);
            int iy = itemY(y, i, columns);
            if (i == hovered) highlightBack(g, ix, iy);
            String label = entry.total() == 1 ? null : PreviewLayout.countLabel(entry.total());
            item(g, font, entry.first(), ix, iy, label);
            if (i == hovered) highlightFront(g, ix, iy);
        }
    }
}
