package dev.fayber.seamless.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final ClientOptions options = ClientOptions.INSTANCE;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Seamless Shulkers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 115;
        int y = Math.max(35, this.height / 2 - 110);
        addBoolean(x, y, "Shulker preview", () -> options.preview, v -> options.preview = v);
        addBoolean(x, y + 23, "Interactive Alt preview", () -> options.interactivePreview, v -> options.interactivePreview = v);
        addBoolean(x, y + 46, "Prefer Shulker Box Tooltip", () -> options.preferExternalTooltip, v -> options.preferExternalTooltip = v);
        addBoolean(x, y + 69, "Hotbar auto-refill", () -> options.hotbarRefill, v -> options.hotbarRefill = v);
        addBoolean(x, y + 92, "Space trading refill", () -> options.tradingRefill, v -> options.tradingRefill = v);

        this.addRenderableWidget(Button.builder(Component.literal("Refill threshold: " + options.refillAtOrBelow),
            button -> {
                options.refillAtOrBelow = (options.refillAtOrBelow + 4) % 36;
                button.setMessage(Component.literal("Refill threshold: " + options.refillAtOrBelow));
            }).bounds(x, y + 115, 230, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Refill target: " + options.refillUpTo),
            button -> {
                options.refillUpTo = options.refillUpTo >= 64 ? 1 : Math.min(64, options.refillUpTo + 8);
                button.setMessage(Component.literal("Refill target: " + options.refillUpTo));
            }).bounds(x, y + 138, 230, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save and close"),
            button -> {
                options.save();
                this.minecraft.gui.setScreen(parent);
            }).bounds(x, y + 165, 230, 20).build());
    }

    private void addBoolean(int x, int y, String title,
                            java.util.function.BooleanSupplier getter,
                            java.util.function.Consumer<Boolean> setter) {
        this.addRenderableWidget(Button.builder(Component.literal(title + ": " + (getter.getAsBoolean() ? "ON" : "OFF")),
            button -> {
                setter.accept(!getter.getAsBoolean());
                button.setMessage(Component.literal(title + ": " + (getter.getAsBoolean() ? "ON" : "OFF")));
            }).bounds(x, y, 230, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, Math.max(15, this.height / 2 - 135), -1);
    }

    @Override
    public void onClose() {
        options.save();
        this.minecraft.gui.setScreen(parent);
    }
}
