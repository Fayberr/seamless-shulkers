package dev.fayber.seamless.client.preview;

import dev.fayber.seamless.preview.PreviewLayout;
import dev.fayber.seamless.client.ClientOptions;
import dev.fayber.seamless.client.mixin.AbstractContainerScreenAccessor;
import dev.fayber.seamless.preview.PreviewBindRequest;
import dev.fayber.seamless.preview.PreviewMenu;
import dev.fayber.seamless.preview.ShulkerPreviewContainer;
import dev.fayber.seamless.preview.ShulkerPreviewSlot;
import dev.fayber.seamless.preview.ShulkerRules;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The pinned preview (State C, and a locked State B). A pin belongs to one container screen and
 * one source slot. It stays where it was opened while the cursor moves, and is closed when its
 * keys are released (unless locked), when the box leaves the slot, or when the screen closes.
 *
 * <p>An interactive pin binds the 27 preview slots of the player's inventory menu to the box, so
 * clicks inside the panel are ordinary vanilla container clicks on those slots (predicted here,
 * applied by the server). A display-only pin never touches any slot.
 *
 * <p>All state lives on the render thread.
 */
public final class PreviewController {
    private static final int TITLE_GAP = 3;
    private static final int HINT_GAP = 3;
    /** Tooltip frame drawn around the content box; clicks on it belong to the panel too. */
    private static final int FRAME = 3;

    private static @Nullable Pin pin;
    private static int lastToken;
    private static int lastMouseX;
    private static int lastMouseY;

    private PreviewController() {}

    private static final class Pin {
        final AbstractContainerScreen<?> screen;
        final Slot source;
        final int anchorX;
        final int anchorY;
        boolean full;
        boolean locked;
        /** Client-side preview container while interactive, otherwise null. */
        @Nullable ShulkerPreviewContainer bound;
        int firstSlot;
        int containerId;
        /** Token of the last binding sent to the server, 0 if none was sent. */
        int token;
        boolean placed;
        // Layout of the last rendered frame, used for hit tests between frames.
        int x;
        int y;
        int width;
        int height;
        int panelX;
        int panelY;
        int columns;
        int rows;
        int entries;

        Pin(AbstractContainerScreen<?> screen, Slot source, int anchorX, int anchorY) {
            this.screen = screen;
            this.source = source;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    // ---------------------------------------------------------------- queries used by mixins

    public static boolean hasPin() {
        return pin != null;
    }

    /** Read-only view of the pin as of the last rendered frame, for diagnostics and the client gametest. */
    public record State(boolean full, boolean locked, boolean interactive, boolean placed, int panelX, int panelY,
                        int columns, int rows) {
        /** GUI-space center of grid cell {@code index}. */
        public int cellCenterX(int index) {
            return panelX + PreviewLayout.BORDER + PreviewLayout.SLOT * (index % columns) + PreviewLayout.SLOT / 2;
        }

        public int cellCenterY(int index) {
            return panelY + PreviewLayout.BORDER + PreviewLayout.SLOT * (index / columns) + PreviewLayout.SLOT / 2;
        }
    }

    public static @Nullable State state() {
        Pin p = pin;
        return p == null ? null : new State(p.full, p.locked, p.bound != null, p.placed, p.panelX, p.panelY, p.columns, p.rows);
    }

    public static boolean isPinned(AbstractContainerScreen<?> screen) {
        Pin p = pin;
        return p != null && p.screen == screen;
    }

    /** True if the point is on the pinned panel of this screen, frame included. */
    public static boolean inside(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Pin p = pin;
        if (p == null || p.screen != screen || !p.placed) return false;
        return mouseX >= p.x - FRAME && mouseX < p.x + p.width + FRAME
            && mouseY >= p.y - FRAME && mouseY < p.y + p.height + FRAME;
    }

    /**
     * While an unlocked pin is held open with Shift+Alt, Shift is part of the gesture and must not
     * turn clicks into quick-moves. A locked pin leaves Shift-click to vanilla.
     */
    public static boolean suppressShift(AbstractContainerScreen<?> screen) {
        Pin p = pin;
        return p != null && p.screen == screen && !p.locked;
    }

    /** Hover resolution while pinned: preview slots inside the panel, ordinary slots elsewhere. */
    public static @Nullable Slot hoveredSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Pin p = pin;
        if (p == null) return null;
        if (inside(screen, mouseX, mouseY)) {
            if (p.bound == null) return null;
            int index = slotIndexAt(p, mouseX, mouseY);
            return index < 0 ? null : screen.getMenu().slots.get(p.firstSlot + index);
        }
        // Shulker Box Tooltip's lock forces the hover onto its locked slot; while our panel is
        // pinned, the real cursor position decides, exactly as vanilla's own hit test would.
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive() && accessor.seamless$isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) return slot;
        }
        return null;
    }

    private static int slotIndexAt(Pin p, double mouseX, double mouseY) {
        int index = PreviewLayout.slotAt((int) Math.floor(mouseX) - p.panelX, (int) Math.floor(mouseY) - p.panelY,
            p.columns, p.rows);
        return index < p.entries ? index : -1;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Once per frame, before the pin is drawn: open, keep, upgrade or close the pin. */
    public static void frame(AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        Minecraft minecraft = Minecraft.getInstance();
        Pin p = pin;
        if (p != null && p.screen != screen) {
            close();
            p = null;
        }
        if (!ClientOptions.INSTANCE.preview || minecraft.player == null) {
            close();
            return;
        }
        boolean fullKeys = PreviewKeys.shift() && PreviewKeys.alt();
        if (p != null) {
            boolean dragging = ((AbstractContainerScreenAccessor) screen).seamless$isQuickCrafting();
            if (p.bound != null && !p.bound.isBound()) downgrade(p, true);
            if (!ShulkerRules.isShulker(p.source.getItem())) {
                close();
                p = null;
            } else if (!p.locked && !fullKeys && !dragging) {
                close();
                p = null;
            } else if (!p.full && fullKeys) {
                p.full = true;
                tryBind(p);
            }
        }
        if (p == null && fullKeys) {
            Slot hovered = ((AbstractContainerScreenAccessor) screen).seamless$getHoveredSlot(mouseX, mouseY);
            if (hovered != null && !(hovered instanceof ShulkerPreviewSlot) && ShulkerRules.isShulker(hovered.getItem())) {
                open(screen, hovered, true, false, mouseX, mouseY);
            }
        }
    }

    /** Control toggles the lock of an open pin, or (without Shulker Box Tooltip) locks the Shift preview. */
    public static void keyPressed(AbstractContainerScreen<?> screen, int key) {
        if (!PreviewKeys.isControlKey(key) || !ClientOptions.INSTANCE.preview) return;
        Pin p = pin;
        if (p != null && p.screen == screen) {
            p.locked = !p.locked;
            return;
        }
        if (p != null || ShulkerTooltips.externalTooltipOwnsCompact() || !PreviewKeys.shift()) return;
        Slot hovered = ((AbstractContainerScreenAccessor) screen).seamless$hoveredSlot();
        if (hovered == null || !ShulkerRules.isShulker(hovered.getItem())) return;
        if (PanelRenderer.nonEmptyCount(PanelRenderer.contents(hovered.getItem())) == 0) return;
        open(screen, hovered, PreviewKeys.alt(), true, lastMouseX, lastMouseY);
    }

    private static void open(AbstractContainerScreen<?> screen, Slot source, boolean full, boolean locked, int mouseX, int mouseY) {
        Pin p = new Pin(screen, source, mouseX, mouseY);
        p.full = full;
        p.locked = locked;
        pin = p;
        if (full) tryBind(p);
    }

    /** Binds the preview slots to the box if this pin may edit it; otherwise the pin stays display-only. */
    private static void tryBind(Pin p) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (!ClientOptions.INSTANCE.interactivePreview || player == null || connection == null || player.isSpectator()) return;
        if (p.screen.getMenu() != player.inventoryMenu || p.source.container != player.getInventory()) return;
        int inventorySlot = p.source.getContainerSlot();
        ItemStack box = p.source.getItem();
        if (!ShulkerRules.isBindableInventorySlot(inventorySlot) || !ShulkerRules.isEditable(box)) return;
        if (!ClientPlayNetworking.canSend(PreviewBindRequest.TYPE)) return;
        PreviewMenu menu = (PreviewMenu) player.inventoryMenu;
        ShulkerPreviewContainer container = menu.seamless$preview();
        if (!container.bind(inventorySlot)) return;
        p.bound = container;
        p.firstSlot = menu.seamless$firstPreviewSlot();
        p.containerId = player.inventoryMenu.containerId;
        p.token = ++lastToken;
        ClientPlayNetworking.send(new PreviewBindRequest(p.containerId, inventorySlot, p.token,
            HashedStack.create(box, connection.decoratedHashOpsGenenerator())));
    }

    /** Ends interaction but keeps showing the box. */
    private static void downgrade(Pin p, boolean tellServer) {
        if (p.bound == null) return;
        p.bound.unbind();
        p.bound = null;
        if (tellServer && ClientPlayNetworking.canSend(PreviewBindRequest.TYPE)) {
            ClientPlayNetworking.send(PreviewBindRequest.release(p.containerId, p.token));
        }
    }

    public static void close() {
        Pin p = pin;
        pin = null;
        if (p != null) downgrade(p, true);
    }

    public static void screenRemoved(AbstractContainerScreen<?> screen) {
        if (isPinned(screen)) close();
    }

    /** Connection gone: forget everything without sending. */
    public static void reset() {
        Pin p = pin;
        pin = null;
        if (p != null && p.bound != null) p.bound.unbind();
    }

    /** The server refused or ended binding {@code token}. Older tokens are stale and ignored. */
    public static void onReleased(int token) {
        Pin p = pin;
        if (p != null && p.bound != null && p.token == token) downgrade(p, false);
    }

    // ---------------------------------------------------------------- rendering

    private static List<ItemStack> contents(Pin p) {
        if (p.bound == null) return PanelRenderer.contents(p.source.getItem());
        NonNullList<ItemStack> items = NonNullList.withSize(ShulkerRules.SIZE, ItemStack.EMPTY);
        List<Slot> slots = p.screen.getMenu().slots;
        for (int i = 0; i < ShulkerRules.SIZE; i++) items.set(i, slots.get(p.firstSlot + i).getItem());
        return items;
    }

    /** Draws the pinned panel above the slots and below the carried stack and tooltips. */
    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Pin p = pin;
        if (p == null || p.screen != screen) return;
        Font font = Minecraft.getInstance().font;
        ItemStack box = p.source.getItem();
        List<ItemStack> items = contents(p);
        List<PreviewLayout.Merged<ItemStack>> merged = p.full ? List.of() : PanelRenderer.merge(items);
        p.columns = p.full ? PreviewLayout.FULL_COLUMNS : PreviewLayout.compactColumns(merged.size());
        p.rows = p.full ? PreviewLayout.FULL_ROWS : PreviewLayout.compactRows(merged.size());
        p.entries = p.full ? ShulkerRules.SIZE : merged.size();
        Component title = box.getHoverName();
        Component hint = PreviewKeys.hint(PreviewKeys.controlName(),
            p.locked ? "seamlessshulkers.hint.unlock" : "seamlessshulkers.hint.lock");
        int panelWidth = PreviewLayout.panelWidth(p.columns);
        int panelHeight = PreviewLayout.panelHeight(p.rows);
        p.width = Math.max(panelWidth, Math.max(font.width(title), font.width(hint)));
        p.height = font.lineHeight + TITLE_GAP + panelHeight + HINT_GAP + font.lineHeight;
        if (!p.placed) {
            int[] at = PreviewLayout.place(p.anchorX, p.anchorY, p.width, p.height, screen.width, screen.height);
            p.x = at[0];
            p.y = at[1];
            p.placed = true;
        } else {
            p.x = Math.max(4, Math.min(p.x, screen.width - p.width - 4));
            p.y = Math.max(4, Math.min(p.y, screen.height - p.height - 4));
        }
        p.panelX = p.x;
        p.panelY = p.y + font.lineHeight + TITLE_GAP;

        graphics.nextStratum();
        TooltipRenderUtil.extractTooltipBackground(graphics, p.x, p.y, p.width, p.height, box.get(DataComponents.TOOLTIP_STYLE));
        graphics.text(font, title, p.x, p.y, -1, true);
        int hovered = inside(screen, mouseX, mouseY) ? slotIndexAt(p, mouseX, mouseY) : -1;
        int tint = PanelRenderer.tintOf(box);
        if (p.bound != null) {
            interactiveGrid(p, graphics, font, tint, hovered);
        } else if (p.full) {
            PanelRenderer.fullGrid(graphics, font, items, p.panelX, p.panelY, tint, hovered);
        } else {
            PanelRenderer.compactGrid(graphics, font, merged, p.panelX, p.panelY, tint, hovered);
        }
        graphics.text(font, hint, p.x, p.panelY + panelHeight + HINT_GAP, -1, true);
    }

    /** Full grid of the live preview slots, including vanilla's drag (quick-craft) ghost stacks. */
    private static void interactiveGrid(Pin p, GuiGraphicsExtractor graphics, Font font, int tint, int hovered) {
        AbstractContainerMenu menu = p.screen.getMenu();
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) p.screen;
        ItemStack carried = menu.getCarried();
        Set<Slot> dragSlots = accessor.seamless$quickCraftSlots();
        boolean dragging = accessor.seamless$isQuickCrafting() && !carried.isEmpty();
        int columns = PreviewLayout.FULL_COLUMNS;
        PanelRenderer.panel(graphics, p.panelX, p.panelY, columns, PreviewLayout.FULL_ROWS, tint);
        for (int i = 0; i < ShulkerRules.SIZE; i++) {
            Slot slot = menu.slots.get(p.firstSlot + i);
            int ix = PanelRenderer.itemX(p.panelX, i, columns);
            int iy = PanelRenderer.itemY(p.panelY, i, columns);
            ItemStack shown = slot.getItem();
            String count = null;
            boolean ghost = false;
            boolean hidden = false;
            if (dragging && dragSlots.contains(slot)) {
                if (dragSlots.size() == 1) {
                    hidden = true;
                } else if (AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && menu.canDragTo(slot)) {
                    ghost = true;
                    int max = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                    int already = shown.isEmpty() ? 0 : shown.getCount();
                    int total = AbstractContainerMenu.getQuickCraftPlaceCount(dragSlots.size(),
                        accessor.seamless$quickCraftingType(), carried) + already;
                    if (total > max) {
                        total = max;
                        count = ChatFormatting.YELLOW.toString() + max;
                    }
                    shown = carried.copyWithCount(total);
                }
            }
            if (i == hovered) PanelRenderer.highlightBack(graphics, ix, iy);
            if (!hidden) {
                if (ghost) graphics.fill(ix, iy, ix + 16, iy + 16, PanelRenderer.DRAG_GHOST);
                PanelRenderer.item(graphics, font, shown, ix, iy, count);
            }
            if (i == hovered) PanelRenderer.highlightFront(graphics, ix, iy);
        }
    }

    /**
     * Tooltip handling while pinned. Returns true to cancel the screen's own tooltip. Inside an
     * interactive panel the hovered slot is a real preview slot, so vanilla shows its tooltip.
     */
    public static boolean tooltip(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Pin p = pin;
        if (p == null || p.screen != screen) return false;
        if (inside(screen, mouseX, mouseY)) {
            if (p.bound != null) return false;
            if (!screen.getMenu().getCarried().isEmpty()) return true;
            int index = slotIndexAt(p, mouseX, mouseY);
            if (index >= 0) {
                List<ItemStack> items = contents(p);
                ItemStack stack = p.full ? items.get(index) : PanelRenderer.merge(items).get(index).first();
                if (!stack.isEmpty()) graphics.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
            return true;
        }
        Slot hovered = ((AbstractContainerScreenAccessor) screen).seamless$hoveredSlot();
        return hovered != null && ShulkerRules.isShulker(hovered.getItem());
    }
}
