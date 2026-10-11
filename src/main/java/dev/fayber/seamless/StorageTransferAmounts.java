package dev.fayber.seamless;

/** Standard Minecraft pickup semantics, independent of rendering and networking. */
public final class StorageTransferAmounts {
    private StorageTransferAmounts() {}

    public static int withdrawal(int available, int mouseButton) {
        if (available <= 0) return 0;
        return mouseButton == 1 ? (available + 1) / 2 : mouseButton == 0 ? available : 0;
    }
}
