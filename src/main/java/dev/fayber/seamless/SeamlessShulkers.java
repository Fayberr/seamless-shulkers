package dev.fayber.seamless;

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
        PayloadTypeRegistry.serverboundPlay().register(StorageRequest.TYPE, StorageRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RefillRequest.TYPE, RefillRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TradeRefillRequest.TYPE, TradeRefillRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StorageRequest.TYPE, (payload, context) ->
            context.server().execute(() -> ShulkerStorage.handleStorage(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RefillRequest.TYPE, (payload, context) ->
            context.server().execute(() -> ShulkerStorage.handleRefill(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TradeRefillRequest.TYPE, (payload, context) ->
            context.server().execute(() -> ShulkerStorage.handleTradeRefill(context.player(), payload)));
        LOGGER.info("Seamless Shulkers server storage handlers ready");
    }
}
