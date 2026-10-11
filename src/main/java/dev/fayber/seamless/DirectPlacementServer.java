package dev.fayber.seamless;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative one-shot virtual use of an item from a carried shulker.
 * Never permanently transfers the item into a player inventory slot.
 */
public final class DirectPlacementServer {
    private DirectPlacementServer() {}

    private record Pending(int source, int inside, int hotbar, long createdAt) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final long TTL_NS = 1_500_000_000L;

    public static void arm(ServerPlayer player, DirectArmRequest packet) {
        int source = packet.shulkerSlot(), inside = packet.contentSlot(), selected = packet.hotbarSlot();
        if (source < 0 || source >= 36 || inside < 0 || inside >= 27 || selected < 0 || selected >= 9
                || player.getInventory().getSelectedSlot() != selected || player.containerMenu != player.inventoryMenu) {
            PENDING.remove(player.getUUID());
            return;
        }
        ItemStack shulker = player.getInventory().getItem(source);
        if (!ShulkerStorage.isShulker(shulker)
                || !(ShulkerStorage.read(shulker).get(inside).getItem() instanceof BlockItem)) {
            PENDING.remove(player.getUUID());
            return;
        }
        PENDING.put(player.getUUID(), new Pending(source, inside, selected, System.nanoTime()));
    }

    public static InteractionResult useOn(ServerPlayer player, ItemStack originalHand,
                                           Function<ItemStack, InteractionResult> vanilla) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || System.nanoTime() - pending.createdAt > TTL_NS
                || player.containerMenu != player.inventoryMenu
                || player.getInventory().getSelectedSlot() != pending.hotbar) {
            return vanilla.apply(originalHand);
        }

        ItemStack box = player.getInventory().getItem(pending.source);
        if (!ShulkerStorage.isShulker(box)) return vanilla.apply(originalHand);
        List<ItemStack> contents = ShulkerStorage.read(box);
        ItemStack stored = contents.get(pending.inside);
        if (stored.isEmpty() || !(stored.getItem() instanceof BlockItem)) return vanilla.apply(originalHand);

        // A one-item stack ensures vanilla can consume at most one shulker item.
        ItemStack virtual = stored.copyWithCount(1);
        ItemStack previous = player.getInventory().getItem(pending.hotbar);
        player.getInventory().setItem(pending.hotbar, virtual);
        try {
            return vanilla.apply(virtual);
        } finally {
            boolean used = virtual.isEmpty() || virtual.getCount() == 0;
            player.getInventory().setItem(pending.hotbar, previous);
            if (used) {
                List<ItemStack> afterContents = ShulkerStorage.read(box);
                ItemStack remaining = afterContents.get(pending.inside);
                if (!remaining.isEmpty() && ItemStack.isSameItemSameComponents(remaining, stored)) {
                    remaining.shrink(1);
                    afterContents.set(pending.inside, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
                    ShulkerStorage.write(box, afterContents);
                } else {
                    SeamlessShulkers.LOGGER.error("Shulker contents changed mid-placement for {}", player.getScoreboardName());
                }
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}
