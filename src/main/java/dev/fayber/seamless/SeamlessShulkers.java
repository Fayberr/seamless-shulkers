package dev.fayber.seamless;

import dev.fayber.seamless.preview.PreviewBindRequest;
import dev.fayber.seamless.preview.PreviewReleased;
import dev.fayber.seamless.preview.PreviewServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeamlessShulkers implements ModInitializer {
    public static final String MOD_ID = "seamlessshulkers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(PreviewBindRequest.TYPE, PreviewBindRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PreviewReleased.TYPE, PreviewReleased.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RefillRequest.TYPE, RefillRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TradeRefillRequest.TYPE, TradeRefillRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DirectArmRequest.TYPE, DirectArmRequest.CODEC);
        // Fabric runs play payload handlers on the server thread, in order with vanilla packets, so a
        // bind is always applied before the container clicks the client sent after it.
        ServerPlayNetworking.registerGlobalReceiver(PreviewBindRequest.TYPE, (payload, context) ->
            PreviewServer.handleBind(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RefillRequest.TYPE, (payload, context) ->
            context.server().execute(() -> ShulkerStorage.handleRefill(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TradeRefillRequest.TYPE, (payload, context) ->
            context.server().execute(() -> ShulkerStorage.handleTradeRefill(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(DirectArmRequest.TYPE, (payload, context) ->
            context.server().execute(() -> DirectPlacementServer.arm(context.player(), payload)));
        LOGGER.info("Seamless Shulkers server storage handlers ready");
    }
}
