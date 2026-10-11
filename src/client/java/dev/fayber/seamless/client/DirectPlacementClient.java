package dev.fayber.seamless.client;

import dev.fayber.seamless.DirectArmRequest;
import dev.fayber.seamless.ShulkerStorage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import java.util.function.Supplier;

/** Arms one ordinary vanilla use-on-block from a carried shulker without persistent refilling. */
public final class DirectPlacementClient {
    private DirectPlacementClient() {}
    private record Selected(int source, int inside, ItemStack expected) {}
    private static Selected armed;

    public static boolean prepare(ItemStack required, Minecraft mc) {
        armed = null;
        if (ClientOptions.INSTANCE.easyPlaceMode != ClientOptions.EasyPlaceMode.DIRECT
                || mc.player == null || required.isEmpty() || !(required.getItem() instanceof BlockItem)
                || !ClientPlayNetworking.canSend(DirectArmRequest.TYPE)) return false;

        for (int slot = 0; slot < 36; slot++) {
            if (ItemStack.isSameItemSameComponents(mc.player.getInventory().getItem(slot), required)) return false;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack box = mc.player.getInventory().getItem(slot);
            if (!ShulkerStorage.isShulker(box)) continue;
            var contents = ShulkerStorage.read(box);
            for (int inner = 0; inner < contents.size(); inner++) {
                if (ItemStack.isSameItemSameComponents(contents.get(inner), required)) {
                    armed = new Selected(slot, inner, contents.get(inner).copyWithCount(1));
                    return true;
                }
            }
        }
        return false;
    }

    public static InteractionHand resolveHand(ItemStack required, InteractionHand normal) {
        if (normal != null) return normal;
        return armed != null && ItemStack.isSameItemSameComponents(armed.expected, required)
                ? InteractionHand.MAIN_HAND : null;
    }

    public static InteractionResult useOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                           Supplier<InteractionResult> vanilla) {
        Selected current = armed;
        armed = null;
        if (current == null || hand != InteractionHand.MAIN_HAND || player == null
                || ClientOptions.INSTANCE.easyPlaceMode != ClientOptions.EasyPlaceMode.DIRECT
                || !ClientPlayNetworking.canSend(DirectArmRequest.TYPE)) return vanilla.get();

        int selected = player.getInventory().getSelectedSlot();
        ItemStack box = player.getInventory().getItem(current.source);
        if (!ShulkerStorage.isShulker(box)) return vanilla.get();
        ItemStack stored = ShulkerStorage.read(box).get(current.inside);
        if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, current.expected)) return vanilla.get();

        // Custom arm packet precedes vanilla use-on packet on the same ordered connection.
        ClientPlayNetworking.send(new DirectArmRequest(current.source, current.inside, selected));
        ItemStack original = player.getInventory().getItem(selected);
        player.getInventory().setItem(selected, current.expected.copyWithCount(1));
        try {
            return vanilla.get();
        } finally {
            player.getInventory().setItem(selected, original);
        }
    }
}
