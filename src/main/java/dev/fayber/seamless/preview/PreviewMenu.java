package dev.fayber.seamless.preview;

/** Implemented by InventoryMenu through a mixin. */
public interface PreviewMenu {
    ShulkerPreviewContainer seamless$preview();

    /** Menu index of preview slot 0; preview slots occupy 27 consecutive indices. */
    int seamless$firstPreviewSlot();
}
