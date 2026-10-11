package dev.fayber.seamless.client;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Calls Fayber's Modern Config 1.x builder API reflectively. This avoids a hard
 * dependency or bundling GUI code and works with a separately installed 26.3 library.
 * API stability across 1.x is documented by Modern Config.
 */
public final class ModernScreenBridge {
    private ModernScreenBridge() {}

    @SuppressWarnings("unchecked")
    public static Screen create(Screen parent) throws ReflectiveOperationException {
        ClientOptions c = ClientOptions.INSTANCE;
        Class<?> api = Class.forName("net.fayber.modernconfig.api.ModernConfigScreen");
        Object builder = api.getMethod("builder", Component.class, Screen.class, Runnable.class)
                .invoke(null, Component.literal("Seamless Shulkers"), parent, (Runnable) c::save);
        Class<?> b = builder.getClass();
        Method category = b.getMethod("category", String.class);
        Method bool = b.getMethod("bool", String.class, BooleanSupplier.class, Consumer.class);
        Method slider = b.getMethod("intSlider", String.class, IntSupplier.class, IntConsumer.class, int.class, int.class, int.class);

        category.invoke(builder, "Shulker Preview");
        bool.invoke(builder, "Show preview", (BooleanSupplier) () -> c.preview, (Consumer<Boolean>) v -> c.preview = v);
        bool.invoke(builder, "Interactive preview (Alt)", (BooleanSupplier) () -> c.interactivePreview, (Consumer<Boolean>) v -> c.interactivePreview = v);
        bool.invoke(builder, "Prefer Shulker Box Tooltip", (BooleanSupplier) () -> c.preferExternalTooltip, (Consumer<Boolean>) v -> c.preferExternalTooltip = v);

        category.invoke(builder, "Building");
        Method cycle = b.getMethod("cycle", String.class, Supplier.class, Consumer.class, Object[].class, Function.class);
        cycle.invoke(builder, "Easy Place supply mode",
                (Supplier<ClientOptions.EasyPlaceMode>) () -> c.easyPlaceMode,
                (Consumer<ClientOptions.EasyPlaceMode>) v -> c.easyPlaceMode = v,
                ClientOptions.EasyPlaceMode.values(), (Function<ClientOptions.EasyPlaceMode, String>) Enum::name);
        bool.invoke(builder, "Selected-slot auto-refill", (BooleanSupplier) () -> c.hotbarRefill, (Consumer<Boolean>) v -> c.hotbarRefill = v);
        slider.invoke(builder, "Refill threshold", (IntSupplier) () -> c.refillAtOrBelow, (IntConsumer) v -> c.refillAtOrBelow = v, 0, 63, 1);
        slider.invoke(builder, "Refill up to", (IntSupplier) () -> c.refillUpTo, (IntConsumer) v -> c.refillUpTo = v, 1, 64, 1);

        category.invoke(builder, "Villager Trading");
        bool.invoke(builder, "Space trading refill", (BooleanSupplier) () -> c.tradingRefill, (Consumer<Boolean>) v -> c.tradingRefill = v);
        return (Screen) b.getMethod("build").invoke(builder);
    }
}
