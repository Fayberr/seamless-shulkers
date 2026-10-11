package dev.fayber.seamless.client;

import dev.fayber.seamless.ShulkerStorage;
import dev.fayber.seamless.StorageRequest;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Standalone inventory tooltip with the same vanilla tooltip / bundle-slot
 * visual vocabulary as Shulker Box Tooltip. No external source code is used.
 *
 * Shift: compact read-only preview (only if SBT isn't providing it).
 * Left Alt: lock and interact with the complete, uncompressed 9x3 contents.
 * While locked, ordinary inventory clicks OUTSIDE the preview still work.
 */
public final class PreviewOverlay {
    private static final int SLOT_SIZE = 24;
    private static final int GRID_MARGIN = 9;
    private static final int GRID_TOP = 21;
    private static final int FULL_COLUMNS = 9;
    private static final int MAX_SLOTS = 27;
    private static final int FOOTER = 48;
    private static final int PANEL_PADDING = 12;
    private static final Identifier SLOT_BACKGROUND =
            Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final Identifier SLOT_HIGHLIGHT =
            Identifier.withDefaultNamespace("container/bundle/slot_highlight_back");

    private static final PreviewInputGuard INPUT = new PreviewInputGuard();
    private static int x;
    private static int y;
    private static int width;
    private static int height;
    private static int shulkerSlot = -1;
    private static boolean locked;
    private static List<ItemStack> contents = List.of();

    private PreviewOverlay() {}

    private static boolean externalPreviewActive() {
        return ClientOptions.INSTANCE.preferExternalTooltip
                && FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");
    }

    public static void update(Slot hovered, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientOptions.INSTANCE.preview || mc.player == null) {
            clear();
            return;
        }
        boolean alt = mc.hasAltDown() && ClientOptions.INSTANCE.interactivePreview;
        if (!alt) locked = false;

        int hoveredShulker = findShulkerSlot(hovered, mc);
        if (!locked) {
            shulkerSlot = hoveredShulker;
            if (hoveredShulker >= 0) {
                if (alt) locked = true;
                ItemStack shulker = mc.player.getInventory().getItem(hoveredShulker);
                contents = ShulkerStorage.read(shulker);
                calculateGeometry(mc, shulker, mouseX, mouseY);
            }
        }
        if (shulkerSlot >= 0
                && !ShulkerStorage.isShulker(mc.player.getInventory().getItem(shulkerSlot))) {
            clear();
        }
    }

    private static int findShulkerSlot(Slot hovered, Minecraft mc) {
        if (hovered == null || mc.player == null || !ShulkerStorage.isShulker(hovered.getItem())) {
            return -1;
        }
        Inventory inventory = mc.player.getInventory();
        int index = hovered.getContainerSlot();
        if (hovered.container == inventory && index >= 0 && index < 36) return index;
        // Modded menus may wrap the inventory; only accept an exact stack reference.
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i) == hovered.getItem()) return i;
        }
        return -1;
    }

    private static int usedSlots(List<ItemStack> stacks) {
        int count = 0;
        for (ItemStack stack : stacks) if (!stack.isEmpty()) count++;
        return count;
    }

    private static int shownRows() {
        if (locked) return 3;
        return Math.max(1, Math.min(3, (usedSlots(contents) + 8) / 9));
    }

    private static int shownColumns() {
        return locked ? FULL_COLUMNS : Math.max(1, Math.min(FULL_COLUMNS, usedSlots(contents)));
    }

    private static void calculateGeometry(Minecraft mc, ItemStack box, int mouseX, int mouseY) {
        int columns = shownColumns();
        width = Math.max(columns * SLOT_SIZE + GRID_MARGIN * 2,
                Math.max(140, Math.max(
                        mc.font.width(BuiltInRegistries.ITEM.getKey(box.getItem()).toString()) + 16,
                        mc.font.width(box.getHoverName()) + PANEL_PADDING)));
        height = GRID_TOP + shownRows() * SLOT_SIZE + FOOTER;

        // Keep the tooltip close to the original hovered item, with vanilla-like
        // flipping when close to the screen edges.
        int preferredX = mouseX + 12;
        if (preferredX + width + 4 > mc.getWindow().getGuiScaledWidth())
            preferredX = mouseX - width - 12;
        x = Math.max(4, Math.min(preferredX,
                Math.max(4, mc.getWindow().getGuiScaledWidth() - width - 4)));
        int preferredY = mouseY - 12;
        if (preferredY + height + 4 > mc.getWindow().getGuiScaledHeight())
            preferredY = mouseY - height - 8;
        y = Math.max(4, Math.min(preferredY,
                Math.max(4, mc.getWindow().getGuiScaledHeight() - height - 4)));
    }

    public static boolean isLocked() { return locked; }

    public static boolean shouldRender() {
        if (shulkerSlot < 0 || !ClientOptions.INSTANCE.preview) return false;
        if (locked) return true;
        // SBT owns normal hover and Shift preview whenever installed/preferred.
        return !externalPreviewActive() && Minecraft.getInstance().hasShiftDown();
    }

    public static boolean interceptTooltip(Slot hovered, int mouseX, int mouseY) {
        update(hovered, mouseX, mouseY);
        // Vanilla & Shulker Box Tooltip remain untouched unless we have a
        // complete replacement ready, so ordinary tooltips cannot disappear.
        return shouldRender();
    }

    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!shouldRender()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack box = mc.player.getInventory().getItem(shulkerSlot);
        if (!ShulkerStorage.isShulker(box)) { clear(); return; }
        contents = ShulkerStorage.read(box);

        // Native Minecraft tooltip background and vanilla 24px slot sprites.
        // Unlike the previous custom gray GUI and hand-painted purple boxes,
        // this follows the normal tooltip renderer.
        TooltipRenderUtil.extractTooltipBackground(graphics, x, y, width, height, null);
        graphics.text(mc.font, box.getHoverName(), x + 8, y + 7, 0xffffffff, false);

        int occupied = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            ItemStack stack = contents.get(i);
            int displayIndex;
            if (locked) {
                displayIndex = i;
            } else {
                if (stack.isEmpty()) continue;
                displayIndex = occupied++;
            }
            int sx = x + GRID_MARGIN + (displayIndex % FULL_COLUMNS) * SLOT_SIZE;
            int sy = y + GRID_TOP + (displayIndex / FULL_COLUMNS) * SLOT_SIZE;
            boolean hover = locked && inside(mouseX, mouseY, sx, sy, SLOT_SIZE, SLOT_SIZE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    hover ? SLOT_HIGHLIGHT : SLOT_BACKGROUND, sx, sy, SLOT_SIZE, SLOT_SIZE);
            if (!stack.isEmpty()) {
                graphics.item(stack, sx + 4, sy + 4);
                graphics.itemDecorations(mc.font, stack, sx + 4, sy + 4);
            }
            if (hover && !stack.isEmpty()) {
                graphics.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
            }
        }
        if (!locked && occupied == 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND,
                    x + GRID_MARGIN, y + GRID_TOP, SLOT_SIZE, SLOT_SIZE);
        }

        int footerY = y + GRID_TOP + shownRows() * SLOT_SIZE + 5;
        graphics.text(mc.font,
                Component.literal(BuiltInRegistries.ITEM.getKey(box.getItem()).toString()),
                x + 8, footerY, 0xff777777, false);
        graphics.text(mc.font, Component.literal(box.getComponents().size() + " component(s)"),
                x + 8, footerY + 10, 0xff777777, false);
        graphics.text(mc.font, Component.literal("Contains " + usedSlots(contents) + " stack(s)"),
                x + 8, footerY + 20, 0xffcccccc, false);
        graphics.text(mc.font,
                Component.literal(locked ? "Release Left Alt: unlock" : "Hold Left Alt: interact"),
                x + 8, footerY + 31, 0xffffaa00, false);

        if (locked) {
            // Vanilla's cursor stack was rendered at the previous stratum.
            // Display it again above the preview to keep the held item visible.
            ItemStack carried = mc.player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                graphics.item(carried, mouseX - 8, mouseY - 8);
                graphics.itemDecorations(mc.font, carried, mouseX - 8, mouseY - 8);
            }
        }
    }

    private static boolean inside(double mx, double my, int sx, int sy, int w, int h) {
        return mx >= sx && mx < sx + w && my >= sy && my < sy + h;
    }

    /**
     * Capture ONLY clicks that begin inside the locked tooltip. Any click
     * outside it goes directly to vanilla (even while Alt is held), allowing
     * the carried stack to be deposited in regular inventory slots.
     */
    public static boolean mouseClicked(double mx, double my, int button) {
        boolean active = locked && Minecraft.getInstance().hasAltDown()
                && ClientOptions.INSTANCE.interactivePreview;
        boolean insidePanel = active && inside(mx, my, x - 3, y - 3, width + 6, height + 6);
        if (!INPUT.mousePressed(active, insidePanel, button)) return false;

        int dx = (int)Math.floor(mx) - x - GRID_MARGIN;
        int dy = (int)Math.floor(my) - y - GRID_TOP;
        if ((button == 0 || button == 1) && dx >= 0 && dy >= 0
                && dx < FULL_COLUMNS * SLOT_SIZE && dy < 3 * SLOT_SIZE) {
            int inner = (dy / SLOT_SIZE) * FULL_COLUMNS + dx / SLOT_SIZE;
            if (ClientPlayNetworking.canSend(StorageRequest.TYPE)) {
                ClientPlayNetworking.send(new StorageRequest(shulkerSlot, inner, button));
            }
        }
        return true;
    }

    public static boolean mouseDragged(int button) {
        return INPUT.mouseDragged(button);
    }

    public static boolean mouseReleased(int button) {
        return INPUT.mouseReleased(button);
    }

    public static void clear() {
        locked = false;
        shulkerSlot = -1;
        contents = List.of();
        INPUT.reset();
    }
}
