package dev.fayber.seamless.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewInputGuardTest {
    @Test void normalInventoryClicksPassThrough() {
        PreviewInputGuard guard = new PreviewInputGuard();
        assertFalse(guard.mousePressed(false));
        assertFalse(guard.mouseDragged(false));
        assertFalse(guard.mouseReleased(false));
    }

    @Test void modalPreviewConsumesClickDragAndRelease() {
        PreviewInputGuard guard = new PreviewInputGuard();
        assertTrue(guard.mousePressed(true));
        assertTrue(guard.mouseDragged(true));
        assertTrue(guard.mouseReleased(true));
        assertFalse(guard.mouseReleased(false));
    }

    @Test void releasingAltMidDragMustNotDropItemsIntoBackgroundSlot() {
        PreviewInputGuard guard = new PreviewInputGuard();
        assertTrue(guard.mousePressed(true));
        // Alt is no longer held and the preview is not visibly modal.
        assertTrue(guard.mouseDragged(false));
        assertTrue(guard.mouseReleased(false));
        assertFalse(guard.mousePressed(false));
    }

    @Test void modalClicksOutsideGridStillBlockBackground() {
        PreviewInputGuard guard = new PreviewInputGuard();
        assertTrue(guard.mousePressed(true));
        assertTrue(guard.mouseReleased(true));
    }

    @Test void screenCloseResetsPendingCapture() {
        PreviewInputGuard guard = new PreviewInputGuard();
        guard.mousePressed(true);
        guard.reset();
        assertFalse(guard.mouseReleased(false));
    }
}
