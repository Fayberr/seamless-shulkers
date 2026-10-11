package dev.fayber.seamless.client;

import java.util.List;
import dev.fayber.seamless.ShulkerStorage;
import dev.fayber.seamless.StorageRequest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class PreviewOverlay {
    private static final int WIDTH = 176, HEIGHT = 78;
    private static final int FIRST_X = 8, FIRST_Y = 18, CELL = 18;
    private static int x, y, shulkerSlot = -1;
    private static boolean pinned;
    private static List<ItemStack> stacks = List.of();

    private PreviewOverlay() {}

    public static void render(GuiGraphics graphics, Slot hovered, int mouseX, int mouseY) {
        if (!ClientOptions.INSTANCE.preview) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean alt = Screen.hasAltDown();
        if (!alt) pinned = false;
        if (hovered != null && hovered.container instanceof Inventory inventory) {
            int slot = hovered.getContainerSlot();
            if (slot >= 0 && slot < 36 && ShulkerStorage.isShulker(inventory.getItem(slot))) {
                shulkerSlot = slot;
                if (!pinned) {
                    x = Math.min(mouseX + 12, mc.getWindow().getGuiScaledWidth() - WIDTH - 4);
                    y = Math.min(mouseY + 12, mc.getWindow().getGuiScaledHeight() - HEIGHT - 4);
                }
                if (alt && ClientOptions.INSTANCE.interactivePreview) pinned = true;
            } else if (!pinned) shulkerSlot = -1;
        } else if (!pinned) shulkerSlot = -1;

        if (shulkerSlot < 0) return;
        ItemStack box = mc.player.getInventory().getItem(shulkerSlot);
        if (!ShulkerStorage.isShulker(box)) { shulkerSlot = -1; pinned = false; return; }

        // Respect external tooltip mods for ordinary hover; interactive Alt overlay stays ours.
        if (!pinned && ClientOptions.INSTANCE.preferExternalTooltip &&
            FabricLoader.getInstance().isModLoaded("shulkerboxtooltip")) return;
        stacks = ShulkerStorage.read(box);
        // Classic inventory palette with 9x3 item slots. No foreign renderer or source is copied.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xff171717);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xffc6c6c6);
        graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + 3, 0xffffffff);
        graphics.drawString(mc.font, box.getHoverName(), x + 8, y + 6, 0xff404040, false);
        for (int i = 0; i < 27; i++) {
            int sx = x + FIRST_X + (i % 9) * CELL, sy = y + FIRST_Y + (i / 9) * CELL;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xff373737);
            graphics.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xffffffff);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xff8b8b8b);
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, sx + 1, sy + 1);
                graphics.renderItemDecorations(mc.font, stack, sx + 1, sy + 1);
            }
            if (pinned && mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x80ffffff);
            }
        }
    }

    public static boolean click(double mouseX, double mouseY, int button) {
        if (!pinned || !Screen.hasAltDown() || !ClientOptions.INSTANCE.interactivePreview) return false;
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + HEIGHT) return false;
        int col = ((int) mouseX - x - FIRST_X) / CELL;
        int row = ((int) mouseY - y - FIRST_Y) / CELL;
        int dx = (int) mouseX - x - FIRST_X, dy = (int) mouseY - y - FIRST_Y;
        if (dx >= 0 && dy >= 0 && col >= 0 && col < 9 && row >= 0 && row < 3) {
            int sx = dx % CELL, sy = dy % CELL;
            if (sx < 18 && sy < 18 && (button == 0 || button == 1)) {
                if (ClientPlayNetworking.canSend(StorageRequest.TYPE))
                    ClientPlayNetworking.send(new StorageRequest(shulkerSlot, row * 9 + col, button));
            }
        }
        return true; // never allow fall-through into underlying inventory when preview is pinned
    }

    public static void clear() {
        pinned = false;
        shulkerSlot = -1;
        stacks = List.of();
    }
}
