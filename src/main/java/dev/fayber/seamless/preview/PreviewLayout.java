package dev.fayber.seamless.preview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/** Geometry, merging and labels of the preview panel. Free of game classes so it is unit tested. */
public final class PreviewLayout {
    public static final int SLOT = 18;
    /** Panel border; slot boxes start here. Items sit one pixel further in. */
    public static final int BORDER = 7;
    public static final int FULL_COLUMNS = 9;
    public static final int FULL_ROWS = 3;
    public static final int MAX_COMPACT_COLUMNS = 9;

    private PreviewLayout() {}

    public record Merged<T>(T first, int total, int firstIndex) {}

    /**
     * Merges equal stacks (compact preview). Empty entries must already be excluded by
     * {@code count <= 0}. Sorted by merged count, largest first; ties keep the earliest slot first.
     */
    public static <T> List<Merged<T>> merge(List<T> stacks, BiPredicate<T, T> same, ToIntFunction<T> count) {
        List<Merged<T>> merged = new ArrayList<>();
        List<int[]> totals = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            T stack = stacks.get(i);
            int n = count.applyAsInt(stack);
            if (n <= 0) continue;
            boolean added = false;
            for (int m = 0; m < merged.size(); m++) {
                if (same.test(merged.get(m).first(), stack)) {
                    totals.get(m)[0] += n;
                    added = true;
                    break;
                }
            }
            if (!added) {
                merged.add(new Merged<>(stack, 0, i));
                totals.add(new int[] {n});
            }
        }
        List<Merged<T>> result = new ArrayList<>(merged.size());
        for (int m = 0; m < merged.size(); m++) {
            result.add(new Merged<>(merged.get(m).first(), totals.get(m)[0], merged.get(m).firstIndex()));
        }
        result.sort(Comparator.comparingInt((Merged<T> e) -> -e.total()).thenComparingInt(Merged::firstIndex));
        return result;
    }

    public static int compactColumns(int entries) {
        return Math.max(1, Math.min(MAX_COMPACT_COLUMNS, entries));
    }

    public static int compactRows(int entries) {
        return Math.max(1, (entries + MAX_COMPACT_COLUMNS - 1) / MAX_COMPACT_COLUMNS);
    }

    public static int panelWidth(int columns) {
        return BORDER * 2 + columns * SLOT;
    }

    public static int panelHeight(int rows) {
        return BORDER * 2 + rows * SLOT;
    }

    /** Index of the slot box under a point relative to the panel origin, or -1 (border, outside). */
    public static int slotAt(int localX, int localY, int columns, int rows) {
        int dx = localX - BORDER;
        int dy = localY - BORDER;
        if (dx < 0 || dy < 0) return -1;
        int column = dx / SLOT;
        int row = dy / SLOT;
        if (column >= columns || row >= rows) return -1;
        return row * columns + column;
    }

    /** Count label for compact entries: up to three digits as-is, then thousands ("1.7k", "12k"). */
    public static String countLabel(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 10_000) {
            int tenths = count / 100;
            return tenths % 10 == 0 ? (tenths / 10) + "k" : (tenths / 10) + "." + (tenths % 10) + "k";
        }
        return (count / 1000) + "k";
    }

    /**
     * Top-left of a tooltip-like box of the given content size next to the cursor, mirroring
     * vanilla's default tooltip placement (right of the cursor, flipped left at the screen edge)
     * and kept fully on screen including its 3 px frame.
     */
    public static int[] place(int mouseX, int mouseY, int width, int height, int screenWidth, int screenHeight) {
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width + 3 > screenWidth) x = Math.max(mouseX - 24 - width, 4);
        if (y + height + 3 > screenHeight) y = screenHeight - height - 3;
        x = Math.max(4, Math.min(x, screenWidth - width - 4));
        y = Math.max(4, y);
        return new int[] {x, y};
    }

    /** Multiplies an ARGB color by an RGB tint, the way a tinted texture would render. */
    public static int tint(int argb, int rgbTint) {
        int a = argb >>> 24;
        int r = ((argb >> 16) & 0xFF) * ((rgbTint >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((rgbTint >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (rgbTint & 0xFF) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Brightens each channel to at least 15 % so very dark dyes keep the slot grid readable. */
    public static int clampTint(int rgb) {
        int min = 38;
        int r = Math.max(min, (rgb >> 16) & 0xFF);
        int g = Math.max(min, (rgb >> 8) & 0xFF);
        int b = Math.max(min, rgb & 0xFF);
        return r << 16 | g << 8 | b;
    }
}
