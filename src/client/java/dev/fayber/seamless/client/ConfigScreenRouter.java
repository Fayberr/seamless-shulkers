package dev.fayber.seamless.client;

import dev.fayber.seamless.SeamlessShulkers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/** One route shared by /seamlessshulkers and Mod Menu. No GUI dependency is mandatory. */
public final class ConfigScreenRouter {
    private ConfigScreenRouter() {}

    public static Screen create(Screen parent) {
        if (FabricLoader.getInstance().isModLoaded("modernconfig")
                && FabricLoader.getInstance().isModLoaded("moderngui")) {
            try {
                return ModernScreenBridge.create(parent);
            } catch (ReflectiveOperationException | LinkageError ex) {
                SeamlessShulkers.LOGGER.warn("Modern Config screen unavailable, using fallback", ex);
            }
        }
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            try {
                return ClothScreenBridge.create(parent);
            } catch (LinkageError ex) {
                SeamlessShulkers.LOGGER.warn("Cloth Config screen unavailable, using fallback", ex);
            }
        }
        return new SettingsScreen(parent);
    }
}
