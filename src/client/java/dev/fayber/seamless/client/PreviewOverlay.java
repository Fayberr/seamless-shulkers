package dev.fayber.seamless.client;

import java.util.List;
import dev.fayber.seamless.ShulkerStorage;
import dev.fayber.seamless.StorageRequest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory tooltip rendering and a temporary Alt-held modal mouse region.
 * No Shulker Box Tooltip code is copied or modified.
 */
public final class PreviewOverlay {
    private static final int WIDTH = 176;
    private static final int GRID_X = 7;
    private static final int GRID_Y = 20;
    private static final int CELL = 18;
    private static final int FULL_ROWS = 3;
    private static final int FOOTER_HEIGHT = 45;

    private static final int FRAME = 0xff43155e;
    private static final int BACKGROUND = 0xf410091b;
    private static final int SLOT_DARK = 0xff302238;
    private static final int SLOT_LIGHT = 0xff4a3a50;
    private static final int MUTED = 0xff999099;
    private static final int GOLD = 0xffffaa00;

    private static final PreviewInputGuard INPUT = new PreviewInputGuard();
    private static int x, y, shulkerSlot = -1;
    private static boolean pinned;
    private static List<ItemStack> stacks = List.of();

    private PreviewOverlay() {}

    /** Must run before vanilla queues the next tooltip. */
    public static void update(Slot hovered, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientOptions.INSTANCE.preview || mc.player == null) {
            clear();
            return;
        }
        boolean alt = mc.hasAltDown() && ClientOptions.INSTANCE.interactivePreview;
        if (!alt) pinned = false;

        int slot = resolveCarriedShulkerSlot(hovered, mc);
        if (slot >= 0) {
            if (!pinned) {
                shulkerSlot = slot;
                if (alt) pinned = true;
                List<ItemStack> preview = ShulkerStorage.read(mc.player.getInventory().getItem(slot));
                position(mouseX, mouseY, panelHeight(preview));
            }
        } else if (!pinned) {
            shulkerSlot = -1;
        }
        if (shulkerSlot >= 0 && !ShulkerStorage.isShulker(mc.player.getInventory().getItem(shulkerSlot))) {
            clear();
        }
    }

    private static int resolveCarriedShulkerSlot(Slot hovered, Minecraft mc) {
        if (hovered == null || mc.player == null || !ShulkerStorage.isShulker(hovered.getItem())) return -1;
        Inventory inv = mc.player.getInventory();
        int index = hovered.getContainerSlot();
        if (hovered.container == inv && index >= 0 && index < 36) return index;
        for (int i = 0; i < 36; i++) {
            // Identity comparison avoids attaching the preview to another
            // shulker with identical contents in a wrapped inventory screen.
            if (inv.getItem(i) == hovered.getItem()) return i;
        }
        return -1;
    }

    private static int rows(List<ItemStack> contents) {
        if (pinned) return FULL_ROWS;
        int nonEmpty = 0;
        for (ItemStack item : contents) if (!item.isEmpty()) nonEmpty++;
        return Math.max(1, Math.min(FULL_ROWS, (nonEmpty + 8) / 9));
    }

    private static int panelHeight(List<ItemStack> contents) {
        return GRID_Y + rows(contents) * CELL + FOOTER_HEIGHT;
    }

    private static void position(int mouseX, int mouseY, int height) {
        Minecraft mc = Minecraft.getInstance();
        int windowWidth = mc.getWindow().getGuiScaledWidth();
        int windowHeight = mc.getWindow().getGuiScaledHeight();

        int preferredX = mouseX + 12;
        if (preferredX + WIDTH + 4 > windowWidth) preferredX = mouseX - WIDTH - 12;
        x = Math.max(4, Math.min(preferredX, Math.max(4, windowWidth - WIDTH - 4)));

        // Tooltip-like positioning near the hovered item. Flip above the mouse
        // when there is insufficient space below, instead of hugging the bottom.
        int preferredY = mouseY - 12;
        if (preferredY + height + 4 > windowHeight) preferredY = mouseY - height - 8;
        y = Math.max(4, Math.min(preferredY, Math.max(4, windowHeight - height - 4)));
    }

    public static boolean shouldRender() {
        return shulkerSlot >= 0 && ClientOptions.INSTANCE.preview
            && (pinned || !ClientOptions.INSTANCE.preferExternalTooltip
                || !FabricLoader.getInstance().isModLoaded("shulkerboxtooltip"));
    }

    public static boolean interceptTooltip(Slot hovered, int mouseX, int mouseY) {
        update(hovered, mouseX, mouseY);
        // Never suppress the other mod's tooltip unless our own valid
        // preview will replace it at the end of the render extraction.
        return shouldRender();
    }

    /** Called on the final GUI stratum, AFTER deferred vanilla/SBT tooltips. */
    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!shouldRender()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack box = mc.player.getInventory().getItem(shulkerSlot);
        if (!ShulkerStorage.isShulker(box)) { clear(); return; }
        stacks = ShulkerStorage.read(box);

        int visibleRows = rows(stacks);
        int height = panelHeight(stacks);
        // A vanilla-style tooltip outline, rather than the full 176x78
        // shulker container screen. Borders and slots use pixel-sharp fills.
        graphics.fill(x - 2, y - 2, x + WIDTH + 2, y + height + 2, 0xf0000000);
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, FRAME);
        graphics.fill(x, y, x + WIDTH, y + height, BACKGROUND);
        graphics.text(mc.font, box.getHoverName(), x + 7, y + 6, 0xffffffff, false);

        int rendered = 0;
        for (int slot = 0; slot < 27; slot++) {
            ItemStack item = stacks.get(slot);
            int visibleIndex;
            if (pinned) {
                visibleIndex = slot;
            } else {
                if (item.isEmpty()) continue;
                visibleIndex = rendered++;
                if (visibleIndex >= visibleRows * 9) break;
            }
            int sx = x + GRID_X + (visibleIndex % 9) * CELL;
            int sy = y + GRID_Y + (visibleIndex / 9) * CELL;
            drawSlot(graphics, sx, sy);
            if (!item.isEmpty()) {
                graphics.item(item, sx + 1, sy + 1);
                graphics.itemDecorations(mc.font, item, sx + 1, sy + 1);
            }
            if (pinned && contains(mouseX, mouseY, sx, sy, CELL, CELL)) {
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x55ffffff);
                if (!item.isEmpty()) graphics.setTooltipForNextFrame(mc.font, item, mouseX, mouseY);
            }
        }

        // Empty slots in the full interactive grid still need visible cells.
        if (pinned) {
            // The loop above already draws all 27 slots, including empty ones.
        } else if (rendered == 0) {
            drawSlot(graphics, x + GRID_X, y + GRID_Y);
        }

        int metaY = y + GRID_Y + visibleRows * CELL + 4;
        graphics.text(mc.font, Component.literal(BuiltInRegistries.ITEM.getKey(box.getItem()).toString()),
                x + 7, metaY, MUTED, false);
        graphics.text(mc.font, Component.literal(box.getComponents().size() + " component(s)"),
                x + 7, metaY + 10, MUTED, false);
        int populated = 0;
        for (ItemStack item : stacks) if (!item.isEmpty()) populated++;
        graphics.text(mc.font, Component.literal("Contains " + populated + " stack(s)"),
                x + 7, metaY + 20, 0xffd1c6d1, false);
        graphics.text(mc.font, Component.literal(pinned ? "Release Alt: close preview" : "Hold Left Alt: interact"),
                x + 7, metaY + 30, GOLD, false);

        // Render the server-synced carried stack above our own final stratum,
        // otherwise the overlay can obscure Minecraft's earlier cursor layer.
        if (pinned) {
            ItemStack carried = mc.player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                graphics.item(carried, mouseX - 8, mouseY - 8);
                graphics.itemDecorations(mc.font, carried, mouseX - 8, mouseY - 8);
            }
        }
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int sx, int sy) {
        graphics.fill(sx, sy, sx + CELL, sy + CELL, SLOT_DARK);
        graphics.fill(sx, sy, sx + CELL - 1, sy + 1, 0xff100b14);
        graphics.fill(sx, sy, sx + 1, sy + CELL - 1, 0xff100b14);
        graphics.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, SLOT_LIGHT);
    }

    private static boolean contains(double mx, double my, int bx, int by, int w, int h) {
        return mx >= bx && mx < bx + w && my >= by && my < by + h;
    }

    /**
     * Alt-pinned preview is modal: NEVER let any press fall through to the
     * underlying container. The packet is sent only for left/right clicks
     * inside the real, uncompressed 9x3 grid.
     */
    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean modal = pinned && Minecraft.getInstance().hasAltDown()
                && ClientOptions.INSTANCE.interactivePreview;
        if (!INPUT.mousePressed(modal)) return false;

        int dx = (int)Math.floor(mouseX) - x - GRID_X;
        int dy = (int)Math.floor(mouseY) - y - GRID_Y;
        if (button == 0 || button == 1) {
            if (dx >= 0 && dy >= 0 && dx < 9 * CELL && dy < 3 * CELL) {
                int inner = (dy / CELL) * 9 + dx / CELL;
                if (ClientPlayNetworking.canSend(StorageRequest.TYPE)) {
                    ClientPlayNetworking.send(new StorageRequest(shulkerSlot, inner, button));
                }
            }
        }
        return true;
    }

    /** Block vanilla's drag-to-distribute while a preview press is captured. */
    public static boolean mouseDragged() { return INPUT.mouseDragged(pinned); }

    /**
     * Also consume the mouse release even if Alt was released mid-drag.
     * Vanilla releases with a carried stack can move items to a slot behind
     * the preview, which caused the reported unexpected half stacks.
     */
    public static boolean mouseReleased() { return INPUT.mouseReleased(pinned); }

    public static void clear() {
        pinned = false;
        shulkerSlot = -1;
        stacks = List.of();
        INPUT.reset();
    }
}
