package dev.fayber.seamless.preview;

import dev.fayber.seamless.mixin.AbstractContainerMenuAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;

/** Server side of the preview binding. Runs on the server thread, in order with vanilla click packets. */
public final class PreviewServer {
    private PreviewServer() {}

    public static void handleBind(ServerPlayer player, PreviewBindRequest request) {
        ShulkerPreviewContainer preview = ((PreviewMenu) player.inventoryMenu).seamless$preview();
        if (request.inventorySlot() < 0) {
            preview.unbind();
            return;
        }
        boolean accepted = !player.isSpectator()
            && player.containerMenu == player.inventoryMenu
            && request.containerId() == player.inventoryMenu.containerId
            && ShulkerRules.isBindableInventorySlot(request.inventorySlot())
            && clientSeesSameBox(player, request)
            && preview.bind(request.inventorySlot());
        if (accepted) {
            preview.setToken(request.token());
        } else {
            preview.unbind();
            notifyReleased(player, request.token());
        }
    }

    /** The box hash the client sent must match the server's box, so a desynced client cannot pick the wrong one. */
    private static boolean clientSeesSameBox(ServerPlayer player, PreviewBindRequest request) {
        ContainerSynchronizer synchronizer = ((AbstractContainerMenuAccessor) player.inventoryMenu).seamless$synchronizer();
        if (synchronizer == null) return false;
        RemoteSlot probe = synchronizer.createSlot();
        probe.receive(request.expected());
        return probe.matches(player.getInventory().getItem(request.inventorySlot()));
    }

    public static void notifyReleased(ServerPlayer player, int token) {
        if (ServerPlayNetworking.canSend(player, PreviewReleased.TYPE)) {
            ServerPlayNetworking.send(player, new PreviewReleased(token));
        }
    }
}
