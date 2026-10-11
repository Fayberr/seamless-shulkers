package dev.fayber.seamless.client;

import java.util.List;
import dev.fayber.seamless.ShulkerStorage;
import dev.fayber.seamless.StorageRequest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class PreviewOverlay {
    private static final int WIDTH = 176, HEIGHT = 78;
    private static final int FIRST_X = 8, FIRST_Y = 18, CELL = 18;
    private static int x, y, shulkerSlot = -1;
    private static boolean pinned;
    public static boolean isPinned() { return pinned; }
    private static List<ItemStack> stacks = List.of();

    private PreviewOverlay() {}

    /**
     * Run BEFORE vanilla extractTooltip queues its deferred tooltip.
     *
     * AbstractContainerScreen.extractRenderState does:
     * extractContents -> extractCarriedItem -> extractTooltip -> return.
     * Setting pinned only at extractRenderState TAIL is too late to suppress
     * the tooltip from Shulker Box Tooltip: it will already be queued on top.
     */
    public static void update(Slot hovered, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientOptions.INSTANCE.preview || mc.player == null) {
            clear();
            return;
        }

        boolean alt = mc.hasAltDown();
        if (!alt) pinned = false;
        if (hovered != null && hovered.container instanceof Inventory inventory) {
            int slot = hovered.getContainerSlot();
            if (slot >= 0 && slot < 36 && ShulkerStorage.isShulker(inventory.getItem(slot))) {
                if (!pinned) {
                    shulkerSlot = slot;
                    x = Math.max(4, Math.min(mouseX + 12, mc.getWindow().getGuiScaledWidth() - WIDTH - 4));
                    y = Math.max(4, Math.min(mouseY + 12, mc.getWindow().getGuiScaledHeight() - HEIGHT - 4));
                }
                if (alt && ClientOptions.INSTANCE.interactivePreview) pinned = true;
            } else if (!pinned) shulkerSlot = -1;
        } else if (!pinned) shulkerSlot = -1;

        if (shulkerSlot >= 0 && !ShulkerStorage.isShulker(mc.player.getInventory().getItem(shulkerSlot))) {
            clear();
        }
    }

    /**
     * Suppress the underlying item tooltip when our own 27-slot overlay is visible.
     * Normal hovering still belongs to Shulker Box Tooltip if the user prefers it.
     */
    public static boolean interceptTooltip(Slot hovered, int mouseX, int mouseY) {
        update(hovered, mouseX, mouseY);
        if (shulkerSlot < 0) return false;
        if (pinned) return true;
        return !ClientOptions.INSTANCE.preferExternalTooltip
                || !FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");
    }

    public static void render(GuiGraphicsExtractor graphics, Slot hovered, int mouseX, int mouseY) {
        // extractTooltip is usually invoked earlier in the same render pass.
        // Update defensively for container screens with custom render sequences.
        update(hovered, mouseX, mouseY);
        if (shulkerSlot < 0) return;
        Minecraft mc = Minecraft.getInstance();
        ItemStack box = mc.player.getInventory().getItem(shulkerSlot);
        if (!pinned && ClientOptions.INSTANCE.preferExternalTooltip &&
                FabricLoader.getInstance().isModLoaded("shulkerboxtooltip")) return;
        stacks = ShulkerStorage.read(box);
        // Original vanilla shulker box screen texture; implementation is entirely ours.
        graphics.blit(RenderPipelines.GUI_TEXTURED,
            Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png"),
            x, y, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);
        graphics.text(mc.font, box.getHoverName(), x + 8, y + 6, 0xff404040, false);
        for (int i = 0; i < 27; i++) {
            int sx = x + FIRST_X + (i % 9) * CELL, sy = y + FIRST_Y + (i / 9) * CELL;
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                graphics.item(stack, sx + 1, sy + 1);
                graphics.itemDecorations(mc.font, stack, sx + 1, sy + 1);
            }
            if (pinned && mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x80ffffff);
                if (!stack.isEmpty()) graphics.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
            }
        }
    }

    public static boolean click(double mouseX, double mouseY, int button) {
        if (!pinned || !Minecraft.getInstance().hasAltDown() || !ClientOptions.INSTANCE.interactivePreview) return false;
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
