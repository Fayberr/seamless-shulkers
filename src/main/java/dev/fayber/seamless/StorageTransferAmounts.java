package dev.fayber.seamless;

/** Standard Minecraft pickup semantics, independent of rendering and networking. */
public final class StorageTransferAmounts {
    private StorageTransferAmounts() {}

    public static int withdrawal(int available, int mouseButton) {
        if (available <= 0) return 0;
        return mouseButton == 1 ? (available + 1) / 2 : mouseButton == 0 ? available : 0;
    }

    /** Vanilla right-click deposits exactly one item; left-click deposits as many as fit. */
    public static int deposit(int carried, int stored, int maxStack, int mouseButton) {
        if (carried <= 0 || stored >= maxStack || mouseButton < 0 || mouseButton > 1) return 0;
        return Math.min(mouseButton == 1 ? 1 : carried, Math.max(0, maxStack - stored));
    }
}
