package dev.fayber.seamless.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Only loaded if Cloth Config is installed. No Cloth classes are referenced from the router signature. */
public final class ClothScreenBridge {
    private ClothScreenBridge() {}

    public static Screen create(Screen parent) {
        ClientOptions c = ClientOptions.INSTANCE;
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Seamless Shulkers"))
                .setSavingRunnable(c::save);
        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory display = builder.getOrCreateCategory(Component.literal("Shulker Preview"));
        display.addEntry(eb.startBooleanToggle(Component.literal("Show preview"), c.preview)
                .setDefaultValue(true).setSaveConsumer(value -> c.preview = value).build());
        display.addEntry(eb.startBooleanToggle(Component.literal("Editable full preview (Shift+Alt)"), c.interactivePreview)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Take and place items in the pinned full preview of a shulker box in your inventory."))
                .setSaveConsumer(value -> c.interactivePreview = value).build());

        ConfigCategory building = builder.getOrCreateCategory(Component.literal("Building"));
        building.addEntry(eb.startEnumSelector(Component.literal("Easy Place supply mode"),
                        ClientOptions.EasyPlaceMode.class, c.easyPlaceMode)
                .setDefaultValue(ClientOptions.EasyPlaceMode.DIRECT)
                .setTooltip(Component.literal("DIRECT uses one item from the shulker for each placement. INVENTORY_REFILL moves stacks into free hotbar slots."))
                .setSaveConsumer(value -> c.easyPlaceMode = value).build());
        building.addEntry(eb.startBooleanToggle(Component.literal("Automatic selected-slot refill"), c.hotbarRefill)
                .setDefaultValue(false).setSaveConsumer(value -> c.hotbarRefill = value).build());
        building.addEntry(eb.startIntSlider(Component.literal("Refill when count is at most"), c.refillAtOrBelow, 0, 63)
                .setDefaultValue(8).setSaveConsumer(value -> c.refillAtOrBelow = value).build());
        building.addEntry(eb.startIntSlider(Component.literal("Refill up to"), c.refillUpTo, 1, 64)
                .setDefaultValue(64).setSaveConsumer(value -> c.refillUpTo = value).build());

        ConfigCategory trades = builder.getOrCreateCategory(Component.literal("Villager Trading"));
        trades.addEntry(eb.startBooleanToggle(Component.literal("Space trading refill"), c.tradingRefill)
                .setDefaultValue(true).setSaveConsumer(value -> c.tradingRefill = value).build());
        return builder.build();
    }
}
