package dev.fayber.seamless.client;

import dev.fayber.seamless.RefillRequest;
import dev.fayber.seamless.client.preview.ClientShulkerTooltip;
import dev.fayber.seamless.client.preview.PreviewController;
import dev.fayber.seamless.client.preview.ShulkerTooltip;
import dev.fayber.seamless.client.preview.ShulkerTooltips;
import dev.fayber.seamless.preview.PreviewReleased;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
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
    private final String[] lastKnownHotbarItems = new String[9];

    @Override
    public void onInitializeClient() {
        if (dev.fayber.seamless.debug.PreviewDebug.ENABLED) {
            dev.fayber.seamless.debug.PreviewDebug.log("DEBUG BUILD active. Loaded mods: {}", net.fabricmc.loader.api.FabricLoader.getInstance()
                .getAllMods().stream().map(m -> m.getMetadata().getId() + "@" + m.getMetadata().getVersion().getFriendlyString())
                .filter(m -> !m.startsWith("fabric-") && !m.startsWith("java@") && !m.startsWith("mixinextras")).sorted().toList());
            dev.fayber.seamless.debug.PreviewDebug.log("Options at startup: {}", ClientOptions.INSTANCE.describe());
            if (!ClientOptions.INSTANCE.preview) dev.fayber.seamless.debug.PreviewDebug.log("NOTE: 'Show preview' is OFF, so there is no preview at all");
            if (!ClientOptions.INSTANCE.interactivePreview) dev.fayber.seamless.debug.PreviewDebug.log("NOTE: 'Editable full preview (Shift+Alt)' is OFF, so the Shift+Alt panel is display-only");
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientTooltipComponentCallback.EVENT.register(data ->
            data instanceof ShulkerTooltip tooltip ? new ClientShulkerTooltip(tooltip) : null);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> ShulkerTooltips.lines(stack, lines));
        ClientPlayNetworking.registerGlobalReceiver(PreviewReleased.TYPE, (payload, context) ->
            PreviewController.onReleased(payload.token()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PreviewController.reset());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("seamlessshulkers").executes(ctx -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.gui.setScreen(ConfigScreenRouter.create(mc.gui.screen())));
                return 1;
            })));
    }

    private void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.screen() != null) return;
        if (!ClientOptions.INSTANCE.hotbarRefill || !ClientPlayNetworking.canSend(RefillRequest.TYPE)) return;
        int slot = client.player.getInventory().getSelectedSlot();
        ItemStack stack = client.player.getInventory().getItem(slot);
        String itemId = null;
        if (!stack.isEmpty()) {
            itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            lastKnownHotbarItems[slot] = itemId;
        } else {
            // An exhausted stack is refillable only while the player actively uses items.
            if (!client.options.keyUse.isDown()) return;
            itemId = lastKnownHotbarItems[slot];
        }
        if (itemId == null || ++tick % 8 != 0) return;
        if (!stack.isEmpty() && stack.getCount() > ClientOptions.INSTANCE.refillAtOrBelow) return;
        ClientPlayNetworking.send(new RefillRequest(itemId, slot,
            Math.min(64, Math.max(1, ClientOptions.INSTANCE.refillUpTo))));
    }
}
