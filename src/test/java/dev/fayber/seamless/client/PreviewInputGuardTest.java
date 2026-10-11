package dev.fayber.seamless.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewInputGuardTest {
    @Test void outsideClickGoesToVanillaEvenWhenAltLocksPreview() {
        PreviewInputGuard g = new PreviewInputGuard();
        assertFalse(g.mousePressed(true, false, 0));
        assertFalse(g.mouseDragged(0));
        assertFalse(g.mouseReleased(0));
    }
    @Test void insideClickCapturesOnlyMatchingGesture() {
        PreviewInputGuard g = new PreviewInputGuard();
        assertTrue(g.mousePressed(true, true, 0));
        assertFalse(g.mouseDragged(1));
        assertTrue(g.mouseDragged(0));
        assertFalse(g.mouseReleased(1));
        assertTrue(g.mouseReleased(0));
        assertFalse(g.mouseReleased(0));
    }
    @Test void releasedAltDoesNotLetCapturedReleaseTouchBackground() {
        PreviewInputGuard g = new PreviewInputGuard();
        assertTrue(g.mousePressed(true, true, 0));
        assertTrue(g.mouseDragged(0));
        assertTrue(g.mouseReleased(0));
        assertFalse(g.mousePressed(false, false, 0));
    }
    @Test void clickOnPreviewFrameIsCapturedWithoutMovingItems() {
        PreviewInputGuard g = new PreviewInputGuard();
        assertTrue(g.mousePressed(true, true, 1));
        assertTrue(g.mouseReleased(1));
    }
    @Test void closeResetsGestureOwnership() {
        PreviewInputGuard g = new PreviewInputGuard();
        g.mousePressed(true, true, 0);
        g.reset();
        assertFalse(g.mouseReleased(0));
    }
}
