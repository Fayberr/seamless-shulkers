package dev.fayber.seamless.preview;

import dev.fayber.seamless.debug.PreviewDebug;
import dev.fayber.seamless.mixin.AbstractContainerMenuAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Server side of the preview binding. Runs on the server thread, in order with vanilla click packets. */
public final class PreviewServer {
    private PreviewServer() {}

    public static void handleBind(ServerPlayer player, PreviewBindRequest request) {
        AbstractContainerMenu menu = player.containerMenu;
        PreviewMenu host = (PreviewMenu) menu;
        if (request.menuSlot() < 0) {
            ShulkerPreviewContainer preview = host.seamless$preview();
            PreviewDebug.log("server: release request token={} containerId={} (open {} id={}, bound={})", request.token(),
                request.containerId(), menu.getClass().getSimpleName(), menu.containerId, preview != null && preview.isBound());
            if (preview != null && request.containerId() == menu.containerId && request.token() == preview.token()) preview.unbind();
            return;
        }
        String refusal = refusal(player, menu, request);
        ShulkerPreviewContainer preview = null;
        if (refusal == null) {
            preview = host.seamless$ensurePreview(true);
            if (!preview.bind(menu.slots.get(request.menuSlot()))) refusal = "box not editable";
        }
        if (PreviewDebug.ENABLED) {
            Slot slot = request.menuSlot() < menu.slots.size() ? menu.slots.get(request.menuSlot()) : null;
            PreviewDebug.log("server: bind request slot={} token={} containerId={} firstSlot={} (open {} id={} firstSlot={}) -> {} [{}] holder={} serverSlot={}",
                request.menuSlot(), request.token(), request.containerId(), request.firstSlot(), menu.getClass().getSimpleName(),
                menu.containerId, host.seamless$nextFirstPreviewSlot(), refusal == null ? "ACCEPTED" : "REFUSED",
                refusal == null ? "ok" : refusal, slot == null ? "-" : slot.container.getClass().getName(), PreviewDebug.slot(slot));
        }
        if (refusal == null) {
            preview.setToken(request.token());
            host.seamless$setPreviewOwner(player);
            return;
        }
        notifyReleased(player, request.token());
        // A request for a menu that is no longer open must not touch the one that is.
        if (request.containerId() != menu.containerId) return;
        ShulkerPreviewContainer existing = host.seamless$preview();
        if (existing != null) existing.unbind();
        // Undo whatever the client predicted in its panel before it learns about the refusal. Its
        // clicks may even have targeted preview slots this menu does not have, which vanilla drops
        // without a resync.
        menu.sendAllDataToRemote();
    }

    /** Why the request must be refused, or null if it may bind. Every check is server-side truth. */
    private static @Nullable String refusal(ServerPlayer player, AbstractContainerMenu menu, PreviewBindRequest request) {
        if (player.isSpectator()) return "spectator";
        if (request.containerId() != menu.containerId) return "menu " + request.containerId() + " is not open";
        if (!menu.stillValid(player)) return "menu no longer valid";
        if (request.menuSlot() >= menu.slots.size()) return "no such slot";
        Slot slot = menu.slots.get(request.menuSlot());
        if (slot instanceof ShulkerPreviewSlot) return "slot is a preview slot";
        if (!slot.isActive()) return "slot inactive";
        String holder = holderRefusal(player, menu, slot);
        if (holder != null) return holder;
        if (!slot.mayPickup(player)) return "slot is locked";
        if (request.firstSlot() != ((PreviewMenu) menu).seamless$nextFirstPreviewSlot()) {
            return "preview slots would start at " + ((PreviewMenu) menu).seamless$nextFirstPreviewSlot() + ", client expects " + request.firstSlot();
        }
        if (!clientSeesSameBox(menu, slot, request)) return "client sees a different box";
        return null;
    }

    /**
     * A box may be edited where it is stored, never where it is the input of a recipe or of
     * another derived result: changing the contents of a box in a crafting grid, anvil, loom, smithing
     * table, grindstone or trade slot after its result was computed could duplicate items.
     * So the player's own main inventory, hotbar and offhand, and real storage: block entities
     * (chests, barrels, hoppers, dispensers, furnaces, crafters and modded storage blocks), double
     * chests, the ender chest, chest minecarts and boats, and the chests of mounts.
     */
    private static @Nullable String holderRefusal(ServerPlayer player, AbstractContainerMenu menu, Slot slot) {
        Container container = slot.container;
        if (container == player.getInventory()) {
            return ShulkerRules.isBindableInventorySlot(slot.getContainerSlot()) ? null : "armor slot";
        }
        if (menu instanceof InventoryMenu) return "crafting grid of the inventory";
        if (container instanceof BlockEntity || container instanceof CompoundContainer || container instanceof PlayerEnderChestContainer
            || container instanceof ContainerEntity || menu instanceof AbstractMountInventoryMenu) {
            return null;
        }
        return "not a storage container (" + container.getClass().getSimpleName() + ")";
    }

    /** The box hash the client sent must match the server's box, so a desynced client cannot pick the wrong one. */
    private static boolean clientSeesSameBox(AbstractContainerMenu menu, Slot slot, PreviewBindRequest request) {
        ContainerSynchronizer synchronizer = ((AbstractContainerMenuAccessor) menu).seamless$synchronizer();
        if (synchronizer == null) return false;
        RemoteSlot probe = synchronizer.createSlot();
        probe.receive(request.expected());
        return probe.matches(slot.getItem());
    }

    public static void notifyReleased(ServerPlayer player, int token) {
        if (ServerPlayNetworking.canSend(player, PreviewReleased.TYPE)) {
            ServerPlayNetworking.send(player, new PreviewReleased(token));
        }
    }
}
