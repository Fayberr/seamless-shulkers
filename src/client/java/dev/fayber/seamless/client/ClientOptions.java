package dev.fayber.seamless.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientOptions {
    public boolean preview = true;
    public boolean interactivePreview = true;
    public boolean hotbarRefill = false;
    public int refillAtOrBelow = 8;
    public int refillUpTo = 64;
    public boolean preferExternalTooltip = true;
    public boolean tradingRefill = true;
    public enum EasyPlaceMode { DIRECT, INVENTORY_REFILL, OFF }
    public EasyPlaceMode easyPlaceMode = EasyPlaceMode.DIRECT;
    public boolean safeDirectPlacement = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("seamless-shulkers.json");
    public static ClientOptions INSTANCE = load();

    public static ClientOptions load() {
        try {
            if (Files.exists(PATH)) {
                ClientOptions options = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), ClientOptions.class);
                if (options != null) return options;
            }
        } catch (Exception ignored) {}
        return new ClientOptions();
    }
    public void save() {
        refillAtOrBelow = Math.max(0, Math.min(63, refillAtOrBelow));
        refillUpTo = Math.max(1, Math.min(64, refillUpTo));
        if (easyPlaceMode == null) easyPlaceMode = EasyPlaceMode.DIRECT;
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Seamless Shulkers: Unable to save config: " + e);
        }
    }
}
