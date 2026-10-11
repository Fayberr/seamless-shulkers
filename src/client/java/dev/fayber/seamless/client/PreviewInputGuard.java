package dev.fayber.seamless.client;

/**
 * Minimal mouse press ownership. Only gestures starting in the preview
 * are captured; outside clicks remain ordinary vanilla inventory clicks.
 * Ownership persists if Alt is released or the pointer leaves the panel
 * before the matching mouse button is released.
 */
public final class PreviewInputGuard {
    private int capturedButton = -1;

    public boolean mousePressed(boolean locked, boolean insidePreview, int button) {
        if (!locked || !insidePreview) return false;
        capturedButton = button;
        return true;
    }

    public boolean mouseDragged(int button) {
        return capturedButton == button;
    }

    public boolean mouseReleased(int button) {
        if (capturedButton != button) return false;
        capturedButton = -1;
        return true;
    }

    public void reset() { capturedButton = -1; }
}
