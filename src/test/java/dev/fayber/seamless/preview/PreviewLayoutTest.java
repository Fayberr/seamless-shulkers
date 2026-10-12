package dev.fayber.seamless.preview;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewLayoutTest {
    record Stack(String id, int count) {}

    static List<PreviewLayout.Merged<Stack>> merge(Stack... stacks) {
        return PreviewLayout.merge(List.of(stacks), (a, b) -> a.id().equals(b.id()), Stack::count);
    }

    static final Stack EMPTY = new Stack("air", 0);

    @Test
    void mergesEqualStacksAndHidesEmptySlots() {
        var merged = merge(new Stack("arrow", 12), EMPTY, new Stack("stone", 5), new Stack("arrow", 17), EMPTY, new Stack("arrow", 46));
        assertEquals(2, merged.size());
        assertEquals("arrow", merged.get(0).first().id());
        assertEquals(75, merged.get(0).total());
        assertEquals(0, merged.get(0).firstIndex());
        assertEquals("stone", merged.get(1).first().id());
        assertEquals(5, merged.get(1).total());
    }

    @Test
    void sortsByTotalDescendingThenByFirstAppearance() {
        var merged = merge(new Stack("a", 3), new Stack("b", 10), new Stack("c", 3), new Stack("d", 64), new Stack("a", 4));
        assertEquals(List.of("d", "b", "a", "c"), merged.stream().map(m -> m.first().id()).toList());
        assertEquals(7, merged.get(2).total());
    }

    @Test
    void emptyBoxMergesToNothing() {
        List<Stack> empty = new ArrayList<>();
        for (int i = 0; i < 27; i++) empty.add(EMPTY);
        assertTrue(PreviewLayout.merge(empty, (a, b) -> true, Stack::count).isEmpty());
    }

    @Test
    void compactGridIsOnlyAsLargeAsNeeded() {
        assertEquals(1, PreviewLayout.compactColumns(0));
        assertEquals(1, PreviewLayout.compactRows(0));
        assertEquals(2, PreviewLayout.compactColumns(2));
        assertEquals(1, PreviewLayout.compactRows(2));
        assertEquals(9, PreviewLayout.compactColumns(9));
        assertEquals(1, PreviewLayout.compactRows(9));
        assertEquals(9, PreviewLayout.compactColumns(10));
        assertEquals(2, PreviewLayout.compactRows(10));
        assertEquals(3, PreviewLayout.compactRows(27));
        assertEquals(14 + 2 * 18, PreviewLayout.panelWidth(2));
        assertEquals(14 + 3 * 18, PreviewLayout.panelHeight(3));
    }

    @Test
    void slotHitTestExcludesTheBorder() {
        assertEquals(-1, PreviewLayout.slotAt(6, 10, 9, 3));
        assertEquals(-1, PreviewLayout.slotAt(10, 6, 9, 3));
        assertEquals(0, PreviewLayout.slotAt(7, 7, 9, 3));
        assertEquals(0, PreviewLayout.slotAt(24, 24, 9, 3));
        assertEquals(1, PreviewLayout.slotAt(25, 7, 9, 3));
        assertEquals(9, PreviewLayout.slotAt(7, 25, 9, 3));
        assertEquals(26, PreviewLayout.slotAt(7 + 8 * 18 + 17, 7 + 2 * 18 + 17, 9, 3));
        assertEquals(-1, PreviewLayout.slotAt(7 + 9 * 18, 10, 9, 3));
        assertEquals(-1, PreviewLayout.slotAt(10, 7 + 3 * 18, 9, 3));
        assertEquals(-1, PreviewLayout.slotAt(-3, -3, 9, 3));
    }

    @Test
    void countLabels() {
        assertEquals("1", PreviewLayout.countLabel(1));
        assertEquals("999", PreviewLayout.countLabel(999));
        assertEquals("1k", PreviewLayout.countLabel(1000));
        assertEquals("1.7k", PreviewLayout.countLabel(1728));
        assertEquals("9.9k", PreviewLayout.countLabel(9999));
        assertEquals("12k", PreviewLayout.countLabel(12_345));
        assertTrue(PreviewLayout.countLabel(27 * 64 * 64).length() <= 4, "fits a slot: " + PreviewLayout.countLabel(27 * 64 * 64));
    }

    @Test
    void placementFollowsTheCursorAndStaysOnScreen() {
        assertArrayEquals(new int[] {112, 88}, PreviewLayout.place(100, 100, 50, 40, 400, 300));
        // flips left at the right edge
        int[] flipped = PreviewLayout.place(380, 100, 50, 40, 400, 300);
        assertEquals(380 - 24 - 50, flipped[0]);
        // pushed up at the bottom edge, never off the top
        assertEquals(300 - 40 - 3, PreviewLayout.place(100, 290, 50, 40, 400, 300)[1]);
        assertEquals(4, PreviewLayout.place(100, 2, 50, 40, 400, 300)[1]);
        // a box wider than the space left of the cursor is clamped inside the screen
        int[] wide = PreviewLayout.place(30, 100, 200, 40, 220, 300);
        assertEquals(4, wide[0]);
    }

    @Test
    void tintMultipliesAndKeepsAlpha() {
        assertEquals(0xFF8B8B8B, PreviewLayout.tint(0xFF8B8B8B, 0xFFFFFF));
        assertEquals(0x80000000, PreviewLayout.tint(0x80FFFFFF, 0x000000));
        assertEquals(0xFF7F0000, PreviewLayout.tint(0xFFFFFFFF, 0x7F0000));
        assertEquals(0x262626, PreviewLayout.clampTint(0x000000));
        assertEquals(0xFF2626, PreviewLayout.clampTint(0xFF0000));
    }
}
