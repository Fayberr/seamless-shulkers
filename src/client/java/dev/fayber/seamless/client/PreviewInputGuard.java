package dev.fayber.seamless.client;

/**
 * Pure input-capture state shared by preview mouse handlers.
 * A press consumed by the preview must consume its drag and release, even
 * if the user releases Alt between the press and mouse-button release.
 */
public final class PreviewInputGuard {
    private boolean captured;

    public boolean mousePressed(boolean modal) {
        if (!modal) return false;
        captured = true;
        return true;
    }

    public boolean mouseDragged(boolean modal) {
        return captured || modal;
    }

    public boolean mouseReleased(boolean modal) {
        boolean consume = captured || modal;
        captured = false;
        return consume;
    }

    public void reset() { captured = false; }
}
