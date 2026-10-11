package dev.fayber.seamless.client.mixin;

import dev.fayber.seamless.RefillRequest;
import dev.fayber.seamless.ShulkerStorage;
import dev.fayber.seamless.client.ClientOptions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional integration with the historic Litematica pick-block path.
 * When a needed schematic material is missing from main inventory, ask the server
 * to move it into a free hotbar slot. The next Easy Place action can use it normally.
 * No client-side inventory edits or assumptions about server acceptance.
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.InventoryUtils", remap = false)
public abstract class LitematicaPickMixin {
    private static long lastRequest;

    @Inject(method = "schematicWorldPickBlock", at = @At("HEAD"), require = 0, remap = false)
    private static void seamless$prepareMissingItem(ItemStack required, BlockPos pos, Level schematicWorld,
                                                     Minecraft client, CallbackInfo ci) {
        if (!FabricLoader.getInstance().isModLoaded("litematica")
                || !ClientOptions.INSTANCE.hotbarRefill || required.isEmpty()
                || client.player == null || !ClientPlayNetworking.canSend(RefillRequest.TYPE)) return;

        // Let normal Litematica item picking operate if the required block is already carried.
        for (int i = 0; i < 36; i++) {
            ItemStack slot = client.player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(required, slot)) return;
        }
        int empty = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) {
                empty = i;
                break;
            }
        }
        if (empty < 0) return;
        boolean available = false;
        for (int i = 0; i < 36 && !available; i++) {
            ItemStack box = client.player.getInventory().getItem(i);
            if (ShulkerStorage.isShulker(box)) {
                for (ItemStack item : ShulkerStorage.read(box)) {
                    if (ItemStack.isSameItemSameComponents(required, item)) {
                        available = true;
                        break;
                    }
                }
            }
        }
        if (!available) return;
        long now = System.nanoTime();
        if (now - lastRequest < 150_000_000L) return;
        lastRequest = now;
        ClientPlayNetworking.send(new RefillRequest(
            BuiltInRegistries.ITEM.getKey(required.getItem()).toString(),
            empty, Math.min(64, Math.max(1, ClientOptions.INSTANCE.refillUpTo))));
    }
}
