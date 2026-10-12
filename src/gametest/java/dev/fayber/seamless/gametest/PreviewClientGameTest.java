package dev.fayber.seamless.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fayber.seamless.client.SettingsScreen;
import dev.fayber.seamless.client.mixin.AbstractContainerScreenAccessor;
import dev.fayber.seamless.client.preview.PreviewController;
import dev.fayber.seamless.client.preview.ShulkerTooltip;
import dev.fayber.seamless.preview.PreviewMenu;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * End-to-end check with the real client, real input events and a real server connection, once in
 * singleplayer and once against a dedicated server. Every interaction is asserted on the server.
 */
public class PreviewClientGameTest implements FabricClientGameTest {
    static final int BOX = 9;
    static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    static final boolean SBT = FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override
    public void runTest(ClientGameTestContext c) {
        try (TestSingleplayerContext world = c.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            scenario(c, world.getServer(), world.getConnection(), (SBT ? "sbt-" : "") + "singleplayer");
            // /seamlessshulkers opens the settings screen (built-in, Cloth or Modern Config, whichever is installed)
            c.runOnClient(mc -> mc.getConnection().sendCommand("seamlessshulkers"));
            c.waitFor(mc -> mc.gui.screen() instanceof SettingsScreen
                || mc.gui.screen() != null && mc.gui.screen().getClass().getName().contains("config"), 100);
            c.takeScreenshot("settings");
            c.setScreen(() -> null);
        }
        try (TestDedicatedServerContext server = c.worldBuilder().createServer()) {
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksRender();
                scenario(c, server, connection, (SBT ? "sbt-" : "") + "dedicated");
            }
        }
        System.out.println("[SeamlessShulkers client test] all preview scenarios passed in singleplayer and on a dedicated server"
            + (SBT ? " with Shulker Box Tooltip installed" : " without Shulker Box Tooltip"));
    }

    static ItemStack box() {
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.ARROW, 12));
        items.set(1, new ItemStack(Items.ARROW, 17));
        items.set(2, new ItemStack(Items.ARROW, 46));
        items.set(3, new ItemStack(Items.STONE, 5));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return box;
    }

    /** Scenarios A to G driven through the mouse and keyboard. */
    static void scenario(ClientGameTestContext c, TestServerContext server, TestServerConnection conn, String tag) {
        TestInput input = c.getInput();
        server.runCommand("gamemode survival @a");
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            p.getInventory().clearContent();
            p.getInventory().setItem(BOX, box());
            p.getInventory().setItem(19, new ItemStack(Items.ARROW, 1));
            p.inventoryMenu.broadcastChanges();
        });
        sync(c, conn);
        int arrows = serverTotal(server, Items.ARROW);
        check(arrows == 76, tag + ": setup arrows " + arrows);

        input.pressKey(options -> options.keyInventory);
        c.waitForScreen(InventoryScreen.class);
        c.waitTicks(2);

        hoverSlot(c, BOX);
        c.waitTicks(3);
        List<String> lines = c.computeOnClient(mc -> Screen.getTooltipFromItem(mc, mc.player.inventoryMenu.getSlot(BOX).getItem())
            .stream().map(Component::getString).toList());
        if (!SBT) {
            // G, state A: hover shows the summary and the Shift hint, no vanilla item list
            check(lines.stream().anyMatch(l -> l.contains("Contains 4 stacks")), tag + ": state A summary missing: " + lines);
            check(lines.stream().anyMatch(l -> l.contains("view contents")), tag + ": state A hint missing: " + lines);
            check(lines.stream().noneMatch(l -> l.contains("Arrow x")), tag + ": vanilla container lines still shown: " + lines);
            check(tooltipImage(c).isEmpty(), tag + ": state A must not show a grid");
        } else {
            // Shulker Box Tooltip owns hover and Shift; we must add nothing on top of it
            check(lines.stream().noneMatch(l -> l.contains("Contains 4 stacks")), tag + ": our summary doubled SBT's tooltip: " + lines);
            check(!(tooltipImage(c).orElse(null) instanceof ShulkerTooltip), tag + ": our grid doubled SBT's tooltip");
        }
        c.takeScreenshot(tag + "-state-a-hover");

        input.holdShift();
        c.waitTicks(3);
        Optional<TooltipComponent> image = tooltipImage(c);
        if (!SBT) {
            // State B: Shift shows the compact merged preview, still a plain tooltip
            check(image.isPresent() && image.get() instanceof ShulkerTooltip t && !t.full(), tag + ": state B compact grid missing: " + image);
        } else {
            check(image.isPresent() && !(image.get() instanceof ShulkerTooltip), tag + ": SBT compact preview missing or doubled: " + image);
        }
        check(c.computeOnClient(mc -> PreviewController.state()) == null, tag + ": state B must not pin");
        c.takeScreenshot(tag + "-state-b-compact");

        // State C: Shift+Alt pins the full, editable grid
        input.holdAlt();
        c.waitTicks(3);
        PreviewController.State state = c.computeOnClient(mc -> PreviewController.state());
        check(state != null && state.full() && state.interactive() && state.placed(), tag + ": state C pin missing: " + state);
        server.waitFor(s -> serverBound(s), 100);
        c.takeScreenshot(tag + "-state-c-full");

        int[] free = freeSlots(c, 3);

        // A: left-click 12 arrows picks up all 12
        clickCell(c, 0, LEFT);
        sync(c, conn);
        check(serverCarried(server).is(Items.ARROW) && serverCarried(server).getCount() == 12, tag + ": A cursor " + serverCarried(server));
        check(clientCarried(c).getCount() == 12, tag + ": A client cursor " + clientCarried(c));

        // B: the cursor leaves the panel, the panel stays, and the stack lands in the inventory with Shift+Alt held
        clickSlot(c, free[0], LEFT);
        sync(c, conn);
        check(c.computeOnClient(mc -> PreviewController.state()) != null, tag + ": B panel closed when the cursor left");
        check(serverSlot(server, free[0]).getCount() == 12, tag + ": B deposit " + serverSlot(server, free[0]));
        check(serverCarried(server).isEmpty(), tag + ": B cursor not empty");

        // C: and back into the shulker
        clickSlot(c, free[0], LEFT);
        clickCell(c, 0, LEFT);
        sync(c, conn);
        check(serverBox(server).get(0).getCount() == 12, tag + ": C put back " + serverBox(server).get(0));

        // D: 12 onto 17
        clickCell(c, 0, LEFT);
        clickCell(c, 1, LEFT);
        sync(c, conn);
        check(serverBox(server).get(1).getCount() == 29 && serverBox(server).get(0).isEmpty(), tag + ": D merge " + serverBox(server));

        // E: right-click 46 takes 23, right-click an empty slot puts exactly 1
        clickCell(c, 2, RIGHT);
        sync(c, conn);
        check(serverCarried(server).getCount() == 23 && serverBox(server).get(2).getCount() == 23, tag + ": E split " + serverCarried(server));
        clickSlot(c, free[1], RIGHT);
        sync(c, conn);
        check(serverSlot(server, free[1]).getCount() == 1 && serverCarried(server).getCount() == 22, tag + ": E single " + serverSlot(server, free[1]));
        clickCell(c, 2, LEFT);
        sync(c, conn);
        check(serverBox(server).get(2).getCount() == 45, tag + ": E put rest back " + serverBox(server).get(2));

        // F: left drag 29 arrows over two shulker cells and one inventory slot: 9 each, 2 stay on the cursor
        clickCell(c, 1, LEFT);
        moveToCell(c, 5);
        input.holdMouse(LEFT);
        c.waitTicks(2);
        // a small move inside the first cell, as a hand would make, so the drag registers it
        nudge(c, 5);
        moveToCell(c, 6);
        c.waitTicks(2);
        moveToSlot(c, free[2]);
        c.waitTicks(2);
        input.releaseMouse(LEFT);
        sync(c, conn);
        check(serverBox(server).get(5).getCount() == 9 && serverBox(server).get(6).getCount() == 9, tag + ": F box " + serverBox(server));
        check(serverSlot(server, free[2]).getCount() == 9, tag + ": F inventory " + serverSlot(server, free[2]));
        check(serverCarried(server).getCount() == 2, tag + ": F cursor " + serverCarried(server));
        clickCell(c, 1, LEFT);
        sync(c, conn);

        // Reported in game: the harness drops modifier flags from mouse events, so these clicks are
        // dispatched with the flags a real click carries while the keys are held.
        int before = serverBox(server).get(2).getCount();
        // unlocked, Shift+Alt held: Shift is part of the gesture, a plain pickup
        modClick(c, 2, MOD_SHIFT | MOD_ALT);
        sync(c, conn);
        check(serverCarried(server).getCount() == before, tag + ": Shift+Alt click did not pick up: cursor " + serverCarried(server) + " box " + serverBox(server).get(2));
        modClick(c, 2, MOD_SHIFT | MOD_ALT);
        sync(c, conn);
        // lock with Ctrl while Shift+Alt are held and keep all three held
        input.holdControl();
        c.waitTicks(3);
        state = c.computeOnClient(mc -> PreviewController.state());
        check(state != null && state.locked() && state.interactive(), tag + ": ctrl did not lock: " + state);
        modClick(c, 2, MOD_SHIFT | MOD_ALT | MOD_CTRL);
        sync(c, conn);
        System.out.println("[SeamlessShulkers client test] " + tag + " held-lock click: box " + serverBox(server).get(2) + " cursor " + serverCarried(server));
        check(serverCarried(server).getCount() == before, tag + ": click with Shift+Alt+Ctrl held did not pick up: cursor " + serverCarried(server) + " box " + serverBox(server).get(2));
        modClick(c, 2, MOD_SHIFT | MOD_ALT | MOD_CTRL);
        sync(c, conn);
        check(serverBox(server).get(2).getCount() == before, tag + ": held-lock put back " + serverBox(server).get(2));
        input.releaseControl();
        input.releaseAlt();
        input.releaseShift();
        c.waitTicks(3);
        // locked, no keys: plain pickup
        modClick(c, 2, 0);
        sync(c, conn);
        check(serverCarried(server).getCount() == before, tag + ": locked click without keys did not pick up: " + serverCarried(server));
        modClick(c, 2, 0);
        sync(c, conn);
        // locked, Shift alone: quick move out of the box into the inventory
        input.holdShift();
        modClick(c, 2, MOD_SHIFT);
        input.releaseShift();
        sync(c, conn);
        check(serverCarried(server).isEmpty() && serverBox(server).get(2).getCount() < before,
            tag + ": locked Shift-click did not quick move out: box " + serverBox(server).get(2) + " cursor " + serverCarried(server));
        check(serverTotal(server, Items.ARROW) == arrows, tag + ": arrows not conserved after quick move");
        input.pressKey(InputConstants.KEY_LCONTROL);
        c.waitTicks(3);
        check(c.computeOnClient(mc -> PreviewController.state()) == null, tag + ": unlock after held-lock check did not close");
        input.holdShift();
        input.holdAlt();
        c.waitTicks(3);
        hoverSlot(c, BOX);
        c.waitTicks(3);
        server.waitFor(s -> serverBound(s), 100);

        // Ctrl locks: releasing Shift+Alt keeps the panel, Shift-click is a quick move again
        input.pressKey(InputConstants.KEY_LCONTROL);
        input.releaseAlt();
        input.releaseShift();
        c.waitTicks(3);
        state = c.computeOnClient(mc -> PreviewController.state());
        check(state != null && state.locked() && state.interactive(), tag + ": lock did not keep the panel: " + state);
        c.takeScreenshot(tag + "-state-c-locked");
        // locked, Shift alone on an inventory stack: quick move into the box (not a pickup)
        int inBox = serverBoxTotal(server, Items.ARROW);
        ItemStack moving = serverSlot(server, free[1]).copy();
        input.holdShift();
        modClickSlot(c, free[1], MOD_SHIFT);
        input.releaseShift();
        sync(c, conn);
        check(serverSlot(server, free[1]).isEmpty() && serverCarried(server).isEmpty()
                && serverBoxTotal(server, Items.ARROW) == inBox + moving.getCount(),
            tag + ": locked shift-click did not quick move into the box: slot " + serverSlot(server, free[1]) + " cursor " + serverCarried(server));

        // Unlock: with no keys held the panel closes and the server releases the binding
        input.pressKey(InputConstants.KEY_LCONTROL);
        c.waitTicks(3);
        check(c.computeOnClient(mc -> PreviewController.state()) == null, tag + ": unlock did not close the panel");
        sync(c, conn);
        check(!server.computeOnServer(PreviewClientGameTest::serverBound), tag + ": server binding not released");

        check(serverTotal(server, Items.ARROW) == arrows, tag + ": arrows not conserved " + serverTotal(server, Items.ARROW));
        check(serverTotal(server, Items.STONE) == 5, tag + ": stone not conserved");
        ItemStack serverBoxStack = server.computeOnServer(s -> player(s).getInventory().getItem(BOX).copy());
        check(c.computeOnClient(mc -> ItemStack.matches(mc.player.inventoryMenu.getSlot(BOX).getItem(), serverBoxStack)),
            tag + ": client box differs from server");

        c.setScreen(() -> null);
        c.waitTicks(2);
        System.out.println("[SeamlessShulkers client test] " + tag + " passed");
    }

    // ---------------------------------------------------------------- client helpers

    static void sync(ClientGameTestContext c, TestServerConnection conn) {
        c.waitTicks(2);
        conn.waitForServerboundPackets();
        conn.waitForClientboundPackets();
        c.waitTicks(1);
    }

    static Optional<TooltipComponent> tooltipImage(ClientGameTestContext c) {
        return c.computeOnClient(mc -> mc.player.inventoryMenu.getSlot(BOX).getItem().getTooltipImage());
    }

    static ItemStack clientCarried(ClientGameTestContext c) {
        return c.computeOnClient(mc -> mc.player.inventoryMenu.getCarried().copy());
    }

    static InventoryScreen screen(Minecraft mc) {
        if (!(mc.gui.screen() instanceof InventoryScreen screen)) throw new AssertionError("inventory screen closed: " + mc.gui.screen());
        return screen;
    }

    /** Moves the real cursor to a GUI-space point. */
    static void moveTo(ClientGameTestContext c, double guiX, double guiY) {
        double[] physical = c.computeOnClient(mc -> new double[] {
            guiX * mc.getWindow().getWidth() / (double) mc.getWindow().getGuiScaledWidth(),
            guiY * mc.getWindow().getHeight() / (double) mc.getWindow().getGuiScaledHeight()});
        c.getInput().setCursorPos(physical[0], physical[1]);
        c.waitTicks(1);
    }

    static int[] slotCenter(Minecraft mc, int menuSlot) {
        InventoryScreen screen = screen(mc);
        Slot slot = mc.player.inventoryMenu.getSlot(menuSlot);
        AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
        return new int[] {pos.seamless$leftPos() + slot.x + 8, pos.seamless$topPos() + slot.y + 8};
    }

    static void hoverSlot(ClientGameTestContext c, int menuSlot) {
        moveToSlot(c, menuSlot);
    }

    static void moveToSlot(ClientGameTestContext c, int menuSlot) {
        int[] at = c.computeOnClient(mc -> slotCenter(mc, menuSlot));
        moveTo(c, at[0] + 0.5, at[1] + 0.5);
    }

    static void moveToCell(ClientGameTestContext c, int index) {
        PreviewController.State state = c.computeOnClient(mc -> PreviewController.state());
        if (state == null) throw new AssertionError("no pinned panel for cell " + index);
        moveTo(c, state.cellCenterX(index) + 0.5, state.cellCenterY(index) + 0.5);
    }

    static void nudge(ClientGameTestContext c, int index) {
        PreviewController.State state = c.computeOnClient(mc -> PreviewController.state());
        if (state == null) throw new AssertionError("no pinned panel for cell " + index);
        moveTo(c, state.cellCenterX(index) + 3.5, state.cellCenterY(index) + 2.5);
    }

    static void press(ClientGameTestContext c, int button) {
        c.getInput().pressMouse(button);
        c.waitTicks(2);
    }

    static final int MOD_SHIFT = 0x0001, MOD_CTRL = 0x0040, MOD_ALT = 0x0100;

    /** Left click on a panel cell, delivered to the screen with real modifier flags (press and release). */
    static void modClick(ClientGameTestContext c, int index, int modifiers) {
        moveToCell(c, index);
        c.runOnClient(mc -> {
            PreviewController.State state = PreviewController.state();
            if (state == null) throw new AssertionError("no pinned panel for cell " + index);
            Screen screen = mc.gui.screen();
            MouseButtonEvent event = new MouseButtonEvent(state.cellCenterX(index) + 0.5, state.cellCenterY(index) + 0.5,
                new MouseButtonInfo(1, modifiers));
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
        });
        c.waitTicks(2);
    }

    /** Left click on an inventory slot with real modifier flags. */
    static void modClickSlot(ClientGameTestContext c, int menuSlot, int modifiers) {
        moveToSlot(c, menuSlot);
        c.runOnClient(mc -> {
            InventoryScreen screen = screen(mc);
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            Slot slot = mc.player.inventoryMenu.getSlot(menuSlot);
            MouseButtonEvent event = new MouseButtonEvent(accessor.seamless$leftPos() + slot.x + 8.5, accessor.seamless$topPos() + slot.y + 8.5,
                new MouseButtonInfo(1, modifiers));
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
        });
        c.waitTicks(2);
    }

    static void clickCell(ClientGameTestContext c, int index, int button) {
        moveToCell(c, index);
        press(c, button);
    }

    static void clickSlot(ClientGameTestContext c, int menuSlot, int button) {
        moveToSlot(c, menuSlot);
        Slot hovered = c.computeOnClient(mc -> ((AbstractContainerScreenAccessor) screen(mc)).seamless$hoveredSlot());
        check(hovered != null && hovered.index == menuSlot, "cursor is not over slot " + menuSlot + " but " + (hovered == null ? null : hovered.index));
        press(c, button);
    }

    /** Empty main-inventory slots that the pinned panel does not cover. */
    static int[] freeSlots(ClientGameTestContext c, int n) {
        int[] found = c.computeOnClient(mc -> {
            InventoryScreen screen = screen(mc);
            int[] result = new int[n];
            int count = 0;
            for (int menuSlot = 9; menuSlot < 45 && count < n; menuSlot++) {
                if (menuSlot == BOX || mc.player.inventoryMenu.getSlot(menuSlot).hasItem()) continue;
                int[] at = slotCenter(mc, menuSlot);
                boolean covered = false;
                for (int dx = -7; dx <= 7 && !covered; dx += 7) {
                    for (int dy = -7; dy <= 7 && !covered; dy += 7) covered = PreviewController.inside(screen, at[0] + dx, at[1] + dy);
                }
                if (!covered) result[count++] = menuSlot;
            }
            if (count < n) throw new AssertionError("only " + count + " inventory slots are outside the panel");
            return result;
        });
        return found;
    }

    // ---------------------------------------------------------------- server helpers

    static ServerPlayer player(net.minecraft.server.MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    static boolean serverBound(net.minecraft.server.MinecraftServer s) {
        return ((PreviewMenu) player(s).inventoryMenu).seamless$preview().isBound();
    }

    static ItemStack serverCarried(TestServerContext server) {
        return server.computeOnServer(s -> player(s).containerMenu.getCarried().copy());
    }

    static ItemStack serverSlot(TestServerContext server, int menuSlot) {
        return server.computeOnServer(s -> player(s).inventoryMenu.getSlot(menuSlot).getItem().copy());
    }

    static List<ItemStack> serverBox(TestServerContext server) {
        return server.computeOnServer(s -> {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            player(s).getInventory().getItem(BOX).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            return items;
        });
    }

    static int serverBoxTotal(TestServerContext server, Item item) {
        return serverBox(server).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    static int serverTotal(TestServerContext server, Item item) {
        return server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            int n = p.containerMenu.getCarried().is(item) ? p.containerMenu.getCarried().getCount() : 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack stack = p.getInventory().getItem(i);
                if (stack.is(item)) n += stack.getCount();
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                if (contents != null) for (ItemStack inner : contents.itemCopies().toList()) if (inner.is(item)) n += inner.getCount();
            }
            return n;
        });
    }
}
