package dev.fayber.seamless;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class ShulkerStorage {
    private ShulkerStorage() {}

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem block
            && block.getBlock() instanceof ShulkerBoxBlock;
    }

    public static List<ItemStack> read(ItemStack shulker) {
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
        if (contents != null) contents.copyInto(items);
        List<ItemStack> copies = new ArrayList<>(27);
        for (ItemStack item : items) copies.add(item.copy());
        return copies;
    }

    private static void write(ItemStack shulker, List<ItemStack> items) {
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    // Only manipulates the server-owned carried stack and menu cursor; no client-supplied items.
    public static void handleStorage(ServerPlayer player, StorageRequest packet) {
        int slot = packet.inventorySlot(), inner = packet.innerSlot(), button = packet.button();
        if (slot < 0 || slot >= 36 || inner < 0 || inner >= 27 || (button != 0 && button != 1)) return;
        if (player.containerMenu != player.inventoryMenu) return;
        ItemStack shulker = player.getInventory().getItem(slot);
        if (!isShulker(shulker)) return;

        List<ItemStack> contents = read(shulker);
        ItemStack inside = contents.get(inner);
        ItemStack carried = player.containerMenu.getCarried();
        int takeAmount = button == 1 ? (inside.getCount() + 1) / 2 : inside.getCount();

        if (carried.isEmpty() && !inside.isEmpty()) {
            ItemStack taken = inside.copyWithCount(takeAmount);
            inside.shrink(takeAmount);
            contents.set(inner, inside.isEmpty() ? ItemStack.EMPTY : inside);
            player.containerMenu.setCarried(taken);
        } else if (!carried.isEmpty() && !isShulker(carried)) {
            int moved;
            if (inside.isEmpty()) {
                moved = button == 1 ? 1 : Math.min(carried.getCount(), carried.getMaxStackSize());
                contents.set(inner, carried.copyWithCount(moved));
            } else {
                if (!ItemStack.isSameItemSameComponents(inside, carried)) return;
                moved = Math.min(button == 1 ? 1 : carried.getCount(), inside.getMaxStackSize() - inside.getCount());
                if (moved <= 0) return;
                inside.grow(moved);
                contents.set(inner, inside);
            }
            carried.shrink(moved);
            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        } else {
            return;
        }

        write(shulker, contents);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    // Server-authoritative Space trading refills. The client supplies only the selected offer index.
    public static void handleTradeRefill(ServerPlayer player, TradeRefillRequest packet) {
        if (!(player.containerMenu instanceof MerchantMenu menu)) return;
        if (packet.offerIndex() < 0 || packet.offerIndex() >= menu.getOffers().size()) return;
        if (!menu.stillValid(player)) return;
        MerchantOffer offer = menu.getOffers().get(packet.offerIndex());
        if (offer.isOutOfStock()) return;
        menu.setSelectionHint(packet.offerIndex());
        fillTradePayment(player, menu, offer.getItemCostA(), 0);
        offer.getItemCostB().ifPresent(cost -> fillTradePayment(player, menu, cost, 1));
        menu.updateSellItem();
        player.getInventory().setChanged();
        menu.broadcastChanges();
    }

    private static void fillTradePayment(ServerPlayer player, MerchantMenu menu, ItemCost cost, int paymentSlot) {
        ItemStack payment = menu.getSlot(paymentSlot).getItem();
        if (!payment.isEmpty() && !cost.test(payment)) return;
        boolean changed = false;
        for (int source = 0; source < 36; source++) {
            ItemStack shulker = player.getInventory().getItem(source);
            if (!isShulker(shulker)) continue;
            List<ItemStack> items = read(shulker);
            boolean sourceChanged = false;
            for (int inner = 0; inner < 27; inner++) {
                ItemStack stored = items.get(inner);
                if (stored.isEmpty() || !cost.test(stored)) continue;
                if (!payment.isEmpty() && !ItemStack.isSameItemSameComponents(payment, stored)) continue;
                int room = (payment.isEmpty() ? stored.getMaxStackSize() : payment.getMaxStackSize()) - payment.getCount();
                if (room <= 0) break;
                int amount = Math.min(room, stored.getCount());
                if (amount <= 0) continue;
                if (payment.isEmpty()) payment = stored.copyWithCount(amount);
                else payment.grow(amount);
                stored.shrink(amount);
                items.set(inner, stored.isEmpty() ? ItemStack.EMPTY : stored);
                sourceChanged = true;
                changed = true;
            }
            if (sourceChanged) write(shulker, items);
            if (!payment.isEmpty() && payment.getCount() >= payment.getMaxStackSize()) break;
        }
        if (changed) menu.getSlot(paymentSlot).set(payment);
    }

    // Refills only an empty or matching main-inventory stack. Never overwrites user items.
    public static void handleRefill(ServerPlayer player, RefillRequest request) {
        if (request.targetSlot() < 0 || request.targetSlot() >= 36) return;
        if (request.requestedCount() < 1 || request.requestedCount() > 64 || request.itemId().length() > 128) return;
        Item item;
        try {
            item = BuiltInRegistries.ITEM.getValue(Identifier.parse(request.itemId()));
        } catch (RuntimeException ex) { return; }
        if (item == null) return;

        int destination = request.targetSlot();
        ItemStack target = player.getInventory().getItem(destination);
        if (!target.isEmpty() && !target.is(item)) return;
        int missing = Math.min(request.requestedCount() - target.getCount(),
            (target.isEmpty() ? item.getDefaultMaxStackSize() : target.getMaxStackSize()) - target.getCount());
        if (missing <= 0) return;

        for (int inventorySlot = 0; inventorySlot < 36 && missing > 0; inventorySlot++) {
            if (inventorySlot == destination) continue;
            ItemStack shulker = player.getInventory().getItem(inventorySlot);
            if (!isShulker(shulker)) continue;
            List<ItemStack> contents = read(shulker);
            boolean changed = false;
            for (int inner = 0; inner < 27 && missing > 0; inner++) {
                ItemStack stored = contents.get(inner);
                if (stored.isEmpty() || !stored.is(item)) continue;
                if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(target, stored)) continue;
                int moved = Math.min(missing, stored.getCount());
                if (target.isEmpty()) target = stored.copyWithCount(moved);
                else target.grow(moved);
                stored.shrink(moved);
                contents.set(inner, stored.isEmpty() ? ItemStack.EMPTY : stored);
                missing -= moved;
                changed = true;
            }
            if (changed) write(shulker, contents);
        }
        player.getInventory().setItem(destination, target);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
