package dev.fayber.seamless.client;

import dev.fayber.seamless.RefillRequest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class SeamlessShulkersClient implements ClientModInitializer {
    private int tick;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("seamlessshulkers").executes(ctx -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.gui.setScreen(new SettingsScreen(mc.gui.screen())));
                return 1;
            })));
    }

    private void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.screen() != null) return;
        if (!ClientOptions.INSTANCE.hotbarRefill || !ClientPlayNetworking.canSend(RefillRequest.TYPE)) return;
        if (++tick % 8 != 0) return;
        int slot = client.player.getInventory().getSelectedSlot();
        ItemStack stack = client.player.getInventory().getItem(slot);
        if (stack.isEmpty() || stack.getCount() > ClientOptions.INSTANCE.refillAtOrBelow) return;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ClientPlayNetworking.send(new RefillRequest(itemId, slot,
            Math.min(64, Math.max(1, ClientOptions.INSTANCE.refillUpTo))));
    }
}
