package dev.fayber.seamless.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DynamicOps;
import com.google.common.hash.HashCode;
import dev.fayber.seamless.RefillRequest;
import dev.fayber.seamless.ShulkerStorage;
import dev.fayber.seamless.preview.PreviewBindRequest;
import dev.fayber.seamless.preview.PreviewMenu;
import dev.fayber.seamless.preview.PreviewServer;
import dev.fayber.seamless.preview.ShulkerPreviewContainer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.HashOps;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * Drives real {@link ServerboundContainerClickPacket}s through the vanilla server handler. A second,
 * client-mode {@link InventoryMenu} plays the client: it predicts each click exactly like
 * {@code MultiPlayerGameMode.handleContainerInput}, sends the hashed prediction, and applies every
 * packet the server sends back. After each step the test checks that the server state is what the
 * scenario expects, that the client mirror converged to it, and that no item was created or lost.
 */
public class PreviewServerGameTest {
    /** Inventory slot of the box in most scenarios; also its menu index (main inventory starts at 9). */
    static final int BOX = 9;

    /** Captures everything the server sends to the test player instead of writing it to a channel. */
    static final class CapturingConnection extends Connection {
        final List<Packet<?>> sent = new ArrayList<>();

        CapturingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet) {
            sent.add(packet);
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
            sent.add(packet);
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
            sent.add(packet);
        }
    }

    /** One server player plus the client-side mirror of its inventory menu. */
    static final class Rig {
        final GameTestHelper ctx;
        final ServerPlayer player;
        final CapturingConnection connection;
        final ServerPlayer mirrorPlayer;
        final InventoryMenu mirror;
        final HashedPatchMap.HashGenerator hashes;
        int corrections;
        int token;

        Rig(GameTestHelper ctx) {
            this.ctx = ctx;
            var server = ctx.getLevel().getServer();
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "pv" + (int) (Math.random() * 1e6)), false);
            player = new ServerPlayer(server, ctx.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            connection = new CapturingConnection();
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();

            // The mirror lives in the server level, so vanilla's crafting-grid hook wants a connection to
            // report to. Give it one that discards everything; nothing it sends matters to the test.
            CommonListenerCookie mirrorCookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mi" + (int) (Math.random() * 1e6)), false);
            mirrorPlayer = new ServerPlayer(server, ctx.getLevel(), mirrorCookie.gameProfile(), mirrorCookie.clientInformation());
            CapturingConnection discard = new CapturingConnection();
            new EmbeddedChannel(discard);
            server.getPlayerList().placeNewPlayer(discard, mirrorPlayer, mirrorCookie);
            mirrorPlayer.getInventory().clearContent();
            mirror = new InventoryMenu(mirrorPlayer.getInventory(), false, mirrorPlayer);

            DynamicOps<HashCode> ops = server.registryAccess().createSerializationContext(HashOps.CRC32C_INSTANCE);
            hashes = component -> component.encodeValue(ops).getOrThrow().asInt();
            resync();
        }

        ShulkerPreviewContainer serverPreview() {
            return ((PreviewMenu) player.inventoryMenu).seamless$preview();
        }

        ShulkerPreviewContainer mirrorPreview() {
            return ((PreviewMenu) mirror).seamless$preview();
        }

        int first() {
            int first = ((PreviewMenu) player.inventoryMenu).seamless$firstPreviewSlot();
            if (first != ((PreviewMenu) mirror).seamless$firstPreviewSlot()) ctx.fail("preview slot offset differs between sides");
            return first;
        }

        /** Server-side setup write followed by a full sync, like joining with these items. */
        void put(int inventorySlot, ItemStack stack) {
            player.getInventory().setItem(inventorySlot, stack);
        }

        void resync() {
            connection.sent.clear();
            player.inventoryMenu.sendAllDataToRemote();
            deliver();
            corrections = 0;
        }

        /** Binds the preview on both sides the way the client controller does. Returns server acceptance. */
        boolean bind(int inventorySlot) {
            mirrorPreview().bind(inventorySlot);
            return bindServerOnly(inventorySlot, mirrorPlayer.getInventory().getItem(inventorySlot));
        }

        boolean bindServerOnly(int inventorySlot, ItemStack claimed) {
            token++;
            PreviewServer.handleBind(player, new PreviewBindRequest(player.inventoryMenu.containerId, inventorySlot, token,
                HashedStack.create(claimed, hashes)));
            boolean accepted = serverPreview().isBound() && serverPreview().token() == token;
            if (!accepted) mirrorPreview().unbind();
            return accepted;
        }

        /** Predict on the mirror, send the click, apply the answer. Returns the number of correction packets. */
        int click(int slot, int button, ContainerInput input) {
            NonNullList<net.minecraft.world.inventory.Slot> slots = mirror.slots;
            List<ItemStack> before = new ArrayList<>(slots.size());
            for (var s : slots) before.add(s.getItem().copy());
            mirror.clicked(slot, button, input, mirrorPlayer);
            Int2ObjectMap<HashedStack> changed = new Int2ObjectOpenHashMap<>();
            for (int i = 0; i < slots.size(); i++) {
                ItemStack after = slots.get(i).getItem();
                if (!ItemStack.matches(before.get(i), after)) changed.put(i, HashedStack.create(after, hashes));
            }
            HashedStack carried = HashedStack.create(mirror.getCarried(), hashes);
            return send(new ServerboundContainerClickPacket(mirror.containerId, mirror.getStateId(), (short) slot, (byte) button,
                input, changed, carried));
        }

        int send(ServerboundContainerClickPacket packet) {
            connection.sent.clear();
            player.connection.handleContainerClick(packet);
            int before = corrections;
            deliver();
            return corrections - before;
        }

        void deliver() {
            List<Packet<?>> packets = new ArrayList<>(connection.sent);
            connection.sent.clear();
            for (Packet<?> packet : packets) apply(packet);
        }

        void apply(Packet<?> packet) {
            if (packet instanceof BundlePacket<?> bundle) {
                for (Packet<?> sub : bundle.subPackets()) apply(sub);
            } else if (packet instanceof ClientboundContainerSetContentPacket content) {
                corrections++;
                if (content.items().size() != InventoryMenu.SHIELD_SLOT + 1) {
                    ctx.fail("full sync carried " + content.items().size() + " slots, a vanilla client expects 46");
                }
                List<ItemStack> copies = new ArrayList<>();
                for (ItemStack s : content.items()) copies.add(s.copy());
                mirror.initializeContents(content.stateId(), copies, content.carriedItem().copy());
            } else if (packet instanceof ClientboundContainerSetSlotPacket set) {
                corrections++;
                if (set.getSlot() > InventoryMenu.SHIELD_SLOT) ctx.fail("server sent preview slot " + set.getSlot() + " over the network");
                if (set.getContainerId() == mirror.containerId) mirror.setItem(set.getSlot(), set.getStateId(), set.getItem().copy());
            } else if (packet instanceof ClientboundSetCursorItemPacket cursor) {
                corrections++;
                mirror.setCarried(cursor.contents().copy());
            } else if (packet instanceof ClientboundSetPlayerInventoryPacket inv) {
                corrections++;
                mirrorPlayer.getInventory().setItem(inv.slot(), inv.contents().copy());
            }
        }

        AbstractContainerMenu menu() {
            return player.inventoryMenu;
        }

        ItemStack server(int menuSlot) {
            return menu().getSlot(menuSlot).getItem();
        }

        List<ItemStack> boxContents(int inventorySlot) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            player.getInventory().getItem(inventorySlot).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            return items;
        }

        /** Every slot, the cursor and the preview agree between server and mirror. */
        void converged(String step) {
            for (int i = 0; i < menu().slots.size(); i++) {
                ItemStack s = menu().getSlot(i).getItem();
                ItemStack m = mirror.getSlot(i).getItem();
                if (!ItemStack.matches(s, m)) ctx.fail(step + ": slot " + i + " server=" + s + " client=" + m);
            }
            if (!ItemStack.matches(menu().getCarried(), mirror.getCarried())) {
                ctx.fail(step + ": cursor server=" + menu().getCarried() + " client=" + mirror.getCarried());
            }
            for (int i = 0; i < 41; i++) {
                if (!ItemStack.matches(player.getInventory().getItem(i), mirrorPlayer.getInventory().getItem(i))) {
                    ctx.fail(step + ": inventory " + i + " differs");
                }
            }
        }

        /** Total of an item across the inventory, the cursor and every shulker's contents. */
        int total(Item item) {
            int n = count(menu().getCarried(), item);
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                n += count(s, item);
                ItemContainerContents c = s.get(DataComponents.CONTAINER);
                if (c != null) for (ItemStack inner : c.itemCopies().toList()) n += count(inner, item);
            }
            return n;
        }

        static int count(ItemStack s, Item item) {
            return s.is(item) ? s.getCount() : 0;
        }

        void expect(boolean condition, String message) {
            if (!condition) ctx.fail(message);
        }

        void expectStack(ItemStack actual, Item item, int count, String what) {
            if (count == 0 ? !actual.isEmpty() : !(actual.is(item) && actual.getCount() == count)) {
                ctx.fail(what + ": expected " + (count == 0 ? "empty" : count + " " + item) + ", got " + actual);
            }
        }

        void noCorrections(int corrections, String step) {
            if (corrections != 0) ctx.fail(step + ": correctly predicted click needed " + corrections + " correction packet(s)");
        }
    }

    static ItemStack box(ItemStack... contents) {
        return box(Items.SHULKER_BOX, contents);
    }

    static ItemStack box(Item type, ItemStack... contents) {
        ItemStack box = new ItemStack(type);
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        for (int i = 0; i < contents.length; i++) items.set(i, contents[i]);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return box;
    }

    static ItemStack arrows(int n) {
        return n == 0 ? ItemStack.EMPTY : new ItemStack(Items.ARROW, n);
    }

    /** Scenarios A to E from the bug report, chained exactly as a player would click. */
    @GameTest(maxTicks = 100)
    public void pickupDepositMergeAndSplit(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(BOX, box(arrows(12), arrows(17), arrows(46)));
        r.resync();
        r.expect(r.bind(BOX), "bind refused");
        int p = r.first();
        r.expect(p == InventoryMenu.SHIELD_SLOT + 1, "preview slots should follow the 46 vanilla slots, start=" + p);
        int total = r.total(Items.ARROW);

        // A: left-click 12 arrows picks up all 12
        r.noCorrections(r.click(p, 0, ContainerInput.PICKUP), "A");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 12, "A cursor");
        r.expectStack(r.server(p), Items.ARROW, 0, "A source");
        r.expectStack(r.boxContents(BOX).get(0), Items.ARROW, 0, "A box component");
        r.converged("A");

        // B: deposit all 12 into an empty inventory slot while the panel stays open
        r.noCorrections(r.click(20, 0, ContainerInput.PICKUP), "B");
        r.expectStack(r.server(20), Items.ARROW, 12, "B target");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 0, "B cursor");
        r.converged("B");

        // C: and back into the shulker
        r.noCorrections(r.click(20, 0, ContainerInput.PICKUP), "C pick");
        r.noCorrections(r.click(p, 0, ContainerInput.PICKUP), "C place");
        r.expectStack(r.boxContents(BOX).get(0), Items.ARROW, 12, "C box");
        r.converged("C");

        // D: 12 onto 17 merges to 29
        r.noCorrections(r.click(p, 0, ContainerInput.PICKUP), "D pick");
        r.noCorrections(r.click(p + 1, 0, ContainerInput.PICKUP), "D merge");
        r.expectStack(r.boxContents(BOX).get(1), Items.ARROW, 29, "D merged");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 0, "D cursor");
        r.converged("D");

        // E: right-click 46 takes 23, right-click an empty slot deposits exactly 1
        r.noCorrections(r.click(p + 2, 1, ContainerInput.PICKUP), "E split");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 23, "E cursor");
        r.expectStack(r.boxContents(BOX).get(2), Items.ARROW, 23, "E remainder");
        r.noCorrections(r.click(21, 1, ContainerInput.PICKUP), "E single");
        r.expectStack(r.server(21), Items.ARROW, 1, "E deposited");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 22, "E cursor after");
        r.noCorrections(r.click(p + 5, 1, ContainerInput.PICKUP), "E single into shulker");
        r.expectStack(r.boxContents(BOX).get(5), Items.ARROW, 1, "E deposited in box");
        r.converged("E");

        r.expect(r.total(Items.ARROW) == total, "arrows not conserved: " + r.total(Items.ARROW) + " != " + total);
        ctx.succeed();
    }

    /** F: drag distribution across the shulker and the inventory, left and right drag. */
    @GameTest(maxTicks = 100)
    public void dragDistributesAcrossBothInventories(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(BOX, box());
        r.put(10, arrows(64));
        r.put(11, arrows(10));
        r.resync();
        r.expect(r.bind(BOX), "bind refused");
        int p = r.first();
        int total = r.total(Items.ARROW);

        r.noCorrections(r.click(10, 0, ContainerInput.PICKUP), "pick 64");
        // left drag over two preview slots and two inventory slots: 64 / 4 = 16 each
        r.noCorrections(r.click(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ContainerInput.QUICK_CRAFT), "drag start");
        for (int slot : new int[] {p, p + 4, 12, 13}) {
            r.noCorrections(r.click(slot, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT), "drag add " + slot);
        }
        r.noCorrections(r.click(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ContainerInput.QUICK_CRAFT), "drag end");
        r.expectStack(r.boxContents(BOX).get(0), Items.ARROW, 16, "F box 0");
        r.expectStack(r.boxContents(BOX).get(4), Items.ARROW, 16, "F box 4");
        r.expectStack(r.server(12), Items.ARROW, 16, "F inv 12");
        r.expectStack(r.server(13), Items.ARROW, 16, "F inv 13");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 0, "F cursor");
        r.converged("F left");

        // right drag: one per slot, including onto an existing stack in the inventory
        r.noCorrections(r.click(11, 0, ContainerInput.PICKUP), "pick 10");
        r.noCorrections(r.click(-999, AbstractContainerMenu.getQuickcraftMask(0, 1), ContainerInput.QUICK_CRAFT), "rdrag start");
        for (int slot : new int[] {p + 1, p + 2, 12}) {
            r.noCorrections(r.click(slot, AbstractContainerMenu.getQuickcraftMask(1, 1), ContainerInput.QUICK_CRAFT), "rdrag add " + slot);
        }
        r.noCorrections(r.click(-999, AbstractContainerMenu.getQuickcraftMask(2, 1), ContainerInput.QUICK_CRAFT), "rdrag end");
        r.expectStack(r.boxContents(BOX).get(1), Items.ARROW, 1, "F r box 1");
        r.expectStack(r.boxContents(BOX).get(2), Items.ARROW, 1, "F r box 2");
        r.expectStack(r.server(12), Items.ARROW, 17, "F r inv 12");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 7, "F r cursor");
        r.converged("F right");

        r.noCorrections(r.click(11, 0, ContainerInput.PICKUP), "put rest down");
        r.expectStack(r.server(11), Items.ARROW, 7, "F rest");
        r.converged("F done");
        r.expect(r.total(Items.ARROW) == total, "arrows not conserved");
        ctx.succeed();
    }

    /** Shift-click both ways, double-click collect, and number-key swaps. */
    @GameTest(maxTicks = 100)
    public void quickMoveCollectAndSwap(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(0, box(arrows(10), new ItemStack(Items.STONE, 5)));
        r.put(14, arrows(10));
        r.put(15, arrows(5));
        r.put(16, new ItemStack(Items.DIRT, 20));
        r.resync();
        r.expect(r.bind(0), "bind hotbar box refused");
        int p = r.first();
        int arrows = r.total(Items.ARROW);
        int hotbarBox = InventoryMenu.USE_ROW_SLOT_START;

        // shift-click a preview slot: stone goes to the player inventory, hotbar first from the right
        r.noCorrections(r.click(p + 1, 0, ContainerInput.QUICK_MOVE), "shift out");
        r.expectStack(r.boxContents(0).get(1), Items.STONE, 0, "shift out source");
        r.expectStack(r.server(InventoryMenu.USE_ROW_SLOT_END - 1), Items.STONE, 5, "shift out target");
        r.converged("shift out");

        // shift-click an inventory slot: dirt goes into the bound shulker
        r.noCorrections(r.click(16, 0, ContainerInput.QUICK_MOVE), "shift in");
        r.expectStack(r.server(16), Items.DIRT, 0, "shift in source");
        r.expectStack(r.boxContents(0).get(1), Items.DIRT, 20, "shift in target");
        r.converged("shift in");

        // shift-clicking a shulker box never nests it; it takes the vanilla inventory-to-hotbar route
        r.put(17, new ItemStack(Items.DYED_SHULKER_BOX.red()));
        r.resync();
        r.noCorrections(r.click(17, 0, ContainerInput.QUICK_MOVE), "shift shulker");
        r.expect(r.server(hotbarBox + 1).is(Items.DYED_SHULKER_BOX.red()), "shulker box did not take the vanilla route: " + r.server(hotbarBox + 1));
        for (ItemStack inner : r.boxContents(0)) r.expect(!inner.is(Items.DYED_SHULKER_BOX.red()), "shulker box nested into a shulker box");
        r.converged("shift shulker");
        r.put(1, ItemStack.EMPTY);
        r.resync();

        // double-click collect gathers from the inventory and the shulker
        r.noCorrections(r.click(14, 0, ContainerInput.PICKUP), "dbl pick");
        r.noCorrections(r.click(14, 0, ContainerInput.PICKUP_ALL), "dbl collect");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 25, "collected");
        r.expectStack(r.boxContents(0).get(0), Items.ARROW, 0, "collected from box");
        r.converged("collect");
        r.noCorrections(r.click(14, 0, ContainerInput.PICKUP), "put back");

        // number key over a preview slot swaps with the hotbar
        r.noCorrections(r.click(p + 2, 1, ContainerInput.SWAP), "swap key 2");
        r.expectStack(r.boxContents(0).get(2), Items.ARROW, 0, "swap empty hotbar into box");
        r.put(1, new ItemStack(Items.FLINT, 3));
        r.resync();
        r.noCorrections(r.click(p + 2, 1, ContainerInput.SWAP), "swap flint in");
        r.expectStack(r.boxContents(0).get(2), Items.FLINT, 3, "flint in box");
        r.expectStack(r.server(hotbarBox + 1), Items.FLINT, 0, "hotbar emptied");
        r.noCorrections(r.click(p + 1, 1, ContainerInput.SWAP), "swap dirt out");
        r.expectStack(r.server(hotbarBox + 1), Items.DIRT, 20, "dirt to hotbar");
        r.converged("swap");

        // number key of the box's own hotbar slot must not put the box inside itself
        r.click(p + 3, 0, ContainerInput.SWAP);
        r.expect(r.player.getInventory().getItem(0).is(Items.SHULKER_BOX), "box must stay in its hotbar slot after a self swap");
        r.expect(r.serverPreview().isBound(), "self swap should be refused, not unbind");
        r.converged("self swap");

        r.expect(r.total(Items.ARROW) == arrows, "arrows not conserved");
        r.expect(r.total(Items.DIRT) == 20 && r.total(Items.STONE) == 5 && r.total(Items.FLINT) == 3, "items not conserved");
        ctx.succeed();
    }

    /** Clicks into an unbound preview and lying clients are no-ops that get corrected. */
    @GameTest(maxTicks = 100)
    public void unboundAndForgedClicksAreHarmless(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(BOX, box(arrows(12)));
        r.put(10, arrows(30));
        r.resync();
        int p = r.first();
        int total = r.total(Items.ARROW);

        // unbound: the slot reads empty and refuses placement on both sides
        r.click(p, 0, ContainerInput.PICKUP);
        r.expectStack(r.menu().getCarried(), Items.ARROW, 0, "unbound pick");
        r.click(10, 0, ContainerInput.PICKUP);
        r.click(p, 0, ContainerInput.PICKUP);
        r.expectStack(r.menu().getCarried(), Items.ARROW, 30, "unbound place refused");
        r.click(10, 0, ContainerInput.PICKUP);
        r.converged("unbound");

        // forged: claim to have picked up 64 diamonds from the unbound preview
        Int2ObjectMap<HashedStack> claimed = new Int2ObjectOpenHashMap<>();
        claimed.put(p, HashedStack.EMPTY);
        HashedStack fake = HashedStack.create(new ItemStack(Items.DIAMOND, 64), r.hashes);
        r.send(new ServerboundContainerClickPacket(0, r.menu().getStateId(), (short) p, (byte) 0, ContainerInput.PICKUP, claimed, fake));
        r.expect(r.menu().getCarried().isEmpty(), "forged pickup gave the server cursor " + r.menu().getCarried());

        // forged slot index beyond the menu is ignored by vanilla
        r.send(new ServerboundContainerClickPacket(0, r.menu().getStateId(), (short) 500, (byte) 0, ContainerInput.PICKUP,
            new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));

        // bound but the client lies about the result: the server ignores the claim and corrects it
        r.expect(r.bind(BOX), "bind refused");
        claimed.clear();
        claimed.put(p, HashedStack.EMPTY);
        r.send(new ServerboundContainerClickPacket(0, r.menu().getStateId(), (short) p, (byte) 1, ContainerInput.PICKUP, claimed,
            HashedStack.create(arrows(12), r.hashes)));
        r.expectStack(r.menu().getCarried(), Items.ARROW, 6, "server applies its own right-click half");
        r.expectStack(r.boxContents(BOX).get(0), Items.ARROW, 6, "box keeps the other half");
        // the mirror never predicted that click; the server's corrections alone must bring it in line
        r.converged("forged result");

        r.expect(r.total(Items.ARROW) == total, "arrows not conserved");
        r.expect(r.total(Items.DIAMOND) == 0, "diamonds were created");
        ctx.succeed();
    }

    /** A stale state id triggers a vanilla full resync of 46 slots and the client converges. */
    @GameTest(maxTicks = 100)
    public void staleClickResyncsWithoutPreviewSlots(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(BOX, box(arrows(12), arrows(17)));
        r.resync();
        r.expect(r.bind(BOX), "bind refused");
        int p = r.first();
        int total = r.total(Items.ARROW);

        // the server changes the box behind the client's back (refill, another mod)
        r.player.getInventory().getItem(BOX).set(DataComponents.CONTAINER,
            ItemContainerContents.fromItems(List.of(arrows(5), arrows(17))));
        total -= 7;
        int stale = r.mirror.getStateId();
        r.menu().incrementStateId();
        NonNullList<net.minecraft.world.inventory.Slot> slots = r.mirror.slots;
        r.mirror.clicked(p, 0, ContainerInput.PICKUP, r.mirrorPlayer);
        Int2ObjectMap<HashedStack> changed = new Int2ObjectOpenHashMap<>();
        changed.put(p, HashedStack.EMPTY);
        int fixes = r.send(new ServerboundContainerClickPacket(0, stale, (short) p, (byte) 0, ContainerInput.PICKUP, changed,
            HashedStack.create(r.mirror.getCarried(), r.hashes)));
        r.expect(fixes > 0, "stale click got no resync");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 5, "server picked up what it had");
        r.converged("stale");
        r.expect(slots.get(p).getItem().isEmpty(), "client preview slot not corrected");
        r.expect(r.total(Items.ARROW) == total, "arrows not conserved");
        ctx.succeed();
    }

    /** Every condition under which the server must refuse to bind. */
    @GameTest(maxTicks = 100)
    public void bindRefusals(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(BOX, box(arrows(12)));
        ItemStack stacked = box(arrows(3));
        stacked.setCount(2);
        r.put(10, stacked);
        NonNullList<ItemStack> big = NonNullList.withSize(28, ItemStack.EMPTY);
        big.set(27, arrows(1));
        ItemStack oversized = new ItemStack(Items.DYED_SHULKER_BOX.blue());
        oversized.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(big));
        r.put(11, oversized);
        r.put(36, box());
        r.put(12, arrows(5));
        r.resync();

        r.expect(!r.bindServerOnly(BOX, arrows(12)), "bound with a hash of a different stack");
        r.expect(!r.bindServerOnly(BOX, box(arrows(11))), "bound with a hash of a different box");
        r.expect(!r.bind(10), "bound a stack of two boxes");
        r.expect(!r.bind(11), "bound a box whose contents exceed 27 slots");
        r.expect(!r.bind(12), "bound a non-shulker");
        r.expect(!r.bind(36), "bound an armor slot");
        r.expect(!r.bind(41), "bound an invalid inventory slot");

        r.token++;
        PreviewServer.handleBind(r.player, new PreviewBindRequest(7, BOX, r.token, HashedStack.create(r.player.getInventory().getItem(BOX), r.hashes)));
        r.expect(!r.serverPreview().isBound(), "bound with a wrong container id");

        r.player.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv), Component.literal("chest")));
        r.expect(r.player.containerMenu != r.player.inventoryMenu, "chest did not open");
        r.expect(!r.bind(BOX), "bound while another menu is open");
        r.player.closeContainer();

        r.player.setGameMode(GameType.SPECTATOR);
        r.expect(!r.bind(BOX), "bound as spectator");
        r.player.setGameMode(GameType.SURVIVAL);
        r.resync();

        r.expect(r.bind(BOX), "valid bind refused");
        r.expect(r.serverPreview().token() == r.token, "token not recorded");
        // a refused rebind drops the previous binding rather than keeping a stale one
        r.expect(!r.bind(12), "rebind to arrows accepted");
        r.expect(!r.serverPreview().isBound(), "refused rebind left the old binding");
        // release
        r.expect(r.bind(BOX), "bind refused");
        PreviewServer.handleBind(r.player, PreviewBindRequest.release(0, r.token));
        r.expect(!r.serverPreview().isBound(), "release ignored");
        ctx.succeed();
    }

    /** The binding follows the box object: moving or replacing it, even with an identical box, ends it. */
    @GameTest(maxTicks = 100)
    public void bindingDiesWhenTheBoxChanges(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(BOX, box(arrows(12)));
        r.put(10, arrows(3));
        r.resync();
        int p = r.first();

        r.expect(r.bind(BOX), "bind refused");
        ItemStack original = r.player.getInventory().getItem(BOX);
        r.player.getInventory().setItem(BOX, original.copy());
        r.expect(!r.serverPreview().isBound(), "binding survived replacement by an identical box");
        r.expect(r.serverPreview().consumeInvalidation(), "invalidation not reported");
        r.expect(!r.serverPreview().consumeInvalidation(), "invalidation reported twice");
        r.resync();

        // a click aimed at the old preview after the box was replaced touches nothing
        int total = r.total(Items.ARROW);
        r.click(10, 0, ContainerInput.PICKUP);
        r.click(p, 0, ContainerInput.PICKUP);
        r.expectStack(r.menu().getCarried(), Items.ARROW, 3, "placed into a dead binding");
        r.click(10, 0, ContainerInput.PICKUP);
        r.expect(r.total(Items.ARROW) == total, "arrows not conserved");

        // moving the box through the menu ends the binding too
        r.expect(r.bind(BOX), "rebind refused");
        r.noCorrections(r.click(BOX, 0, ContainerInput.PICKUP), "pick box up");
        r.expect(!r.serverPreview().isBound(), "binding followed the box onto the cursor");
        r.noCorrections(r.click(20, 0, ContainerInput.PICKUP), "put box down");
        r.expect(!r.serverPreview().isBound(), "binding followed the box to another slot");
        r.click(p, 0, ContainerInput.PICKUP);
        r.expect(r.menu().getCarried().isEmpty(), "picked up from a moved box");
        r.converged("moved");
        ctx.succeed();
    }

    /** Components of the box and of stored items survive editing; nesting is refused. */
    @GameTest(maxTicks = 100)
    public void componentsAndNesting(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Edge"));
        named.setDamageValue(17);
        ItemStack box = box(Items.DYED_SHULKER_BOX.lime(), named.copy());
        box.set(DataComponents.CUSTOM_NAME, Component.literal("Kit"));
        r.put(BOX, box);
        r.put(10, new ItemStack(Items.DYED_SHULKER_BOX.purple()));
        r.resync();
        r.expect(r.bind(BOX), "bind refused");
        int p = r.first();

        r.noCorrections(r.click(p, 0, ContainerInput.PICKUP), "pick sword");
        r.expect(ItemStack.matches(r.menu().getCarried(), named), "sword lost components: " + r.menu().getCarried());
        r.noCorrections(r.click(p + 8, 0, ContainerInput.PICKUP), "put sword");
        r.expect(ItemStack.matches(r.boxContents(BOX).get(8), named), "stored sword lost components");
        ItemStack after = r.player.getInventory().getItem(BOX);
        r.expect(after.is(Items.DYED_SHULKER_BOX.lime()) && "Kit".equals(after.getHoverName().getString()), "box lost its name or color");

        // nesting: a shulker box on the cursor cannot go into the preview
        r.noCorrections(r.click(10, 0, ContainerInput.PICKUP), "pick shulker");
        r.noCorrections(r.click(p + 1, 0, ContainerInput.PICKUP), "try nest");
        r.expect(r.boxContents(BOX).get(1).isEmpty(), "shulker nested into shulker");
        r.expect(r.menu().getCarried().is(Items.DYED_SHULKER_BOX.purple()), "cursor lost the shulker");
        r.noCorrections(r.click(10, 0, ContainerInput.PICKUP), "put shulker back");
        r.converged("components");
        ctx.succeed();
    }

    /** Auto refill still works, also while the box it draws from is bound. */
    @GameTest(maxTicks = 100)
    public void refillWhileBound(GameTestHelper ctx) {
        Rig r = new Rig(ctx);
        r.put(BOX, box(arrows(40), arrows(40)));
        r.put(3, arrows(2));
        r.resync();
        r.expect(r.bind(BOX), "bind refused");
        int p = r.first();
        int total = r.total(Items.ARROW);

        ShulkerStorage.handleRefill(r.player, new RefillRequest("minecraft:arrow", 3, 64));
        r.deliver();
        r.expectStack(r.player.getInventory().getItem(3), Items.ARROW, 64, "refilled");
        r.expect(r.serverPreview().isBound(), "refill should not end the binding");
        r.expectStack(r.server(p), Items.ARROW, 0, "preview reflects refill slot 0");
        r.expectStack(r.server(p + 1), Items.ARROW, 18, "preview reflects refill slot 1");
        r.converged("refill");
        r.noCorrections(r.click(p + 1, 0, ContainerInput.PICKUP), "click after refill");
        r.expectStack(r.menu().getCarried(), Items.ARROW, 18, "picked the refilled remainder");
        r.expect(r.total(Items.ARROW) == total, "arrows not conserved");
        org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
        ctx.succeed();
    }
}
