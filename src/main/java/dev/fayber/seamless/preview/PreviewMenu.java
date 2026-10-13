package dev.fayber.seamless.preview;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Implemented by every AbstractContainerMenu through a mixin. The player's inventory menu gets its
 * 27 preview slots when it is built; any other menu gets them appended the first time a box is
 * bound in it, so menus nobody pins a box in are never touched.
 */
public interface PreviewMenu {
    /** The preview of this menu, or null if it has no preview slots yet. */
    @Nullable ShulkerPreviewContainer seamless$preview();

    /** Menu index of preview slot 0, or -1 if there are none; preview slots occupy 27 consecutive indices. */
    int seamless$firstPreviewSlot();

    /** Menu index preview slot 0 has, or will get once {@link #seamless$ensurePreview} runs. */
    int seamless$nextFirstPreviewSlot();

    /** Appends the preview slots if missing and returns the preview. */
    ShulkerPreviewContainer seamless$ensurePreview(boolean server);

    /** Server only: the player whose binding this menu holds, for release notifications. */
    void seamless$setPreviewOwner(@Nullable ServerPlayer owner);
}
