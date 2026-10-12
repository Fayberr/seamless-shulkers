# Seamless Shulkers

Fabric mod for **Minecraft 26.3** (Java 25). A single mod JAR provides both client and dedicated-server functionality.

**Status: pre-release (0.1.0-alpha.7).** `gradle build` runs unit tests and server-side game tests of every preview interaction. A client game test drives the real inventory screen with mouse and keyboard input in singleplayer and on a dedicated server, with and without Shulker Box Tooltip. See [TESTING.md](TESTING.md) for what is automated and what still needs hands-on verification.

## Features

- **Shulker preview:** a three-state preview modeled on Shulker Box Tooltip, written from scratch (no Shulker Box Tooltip source is used).
- **Interactive full preview:** the full 9x3 grid of a carried shulker can be edited with normal vanilla inventory controls, while the rest of the inventory keeps working.
- **Auto-Refill:** optional (off by default) refill of the selected hotbar stack from carried shulkers when its count drops below a configured threshold. This setting is independent of the Easy Place supply mode.
- **Easy Place supply modes:**
  - **DIRECT (default):** optional integration with Litematica 26.3. When Litematica needs an item absent from the normal inventory, a one-item virtual hand use temporarily supplies it from a carried shulker. Vanilla executes placement with Litematica's chosen hit target, then the server deducts exactly the block that vanilla consumed. Nothing remains in the player's inventory.
  - **INVENTORY_REFILL:** requests the needed block from a carried shulker into a free hotbar slot. A second Easy Place use may be necessary after network latency.
  - **OFF:** no special Litematica material supply.
- **Trading:** press **Space** in a villager screen to request missing payment items from carried shulkers for the selected offer.
- **Client + server:** works in singleplayer using the integrated server. For multiplayer, install the **same JAR** on both the Fabric client and the dedicated Fabric server. On servers without it, the preview is display-only.
- **Settings:** Modern Config + Modern GUI (preferred), otherwise Cloth Config, otherwise a built-in screen. **Mod Menu**'s configuration button and the client command **`/seamlessshulkers`** use the same router and save into `config/seamless-shulkers.json`.

## Shulker preview

| State | Keys | What you see |
| --- | --- | --- |
| A | Hover a shulker | "Contains N stacks" (or "Empty") and the hint "Left Shift: view contents" |
| B | Hold Shift | Compact read-only preview: equal items merged, sorted by total count, empty slots hidden. Hints for the full view and the lock key |
| C | Hold Shift + Alt | Full 9x3 grid in original slot order. It stays where it opened while the cursor moves into it |

- **Left Control** locks the open panel, so Shift and Alt can be released. While Shift and Alt are still held, clicks stay normal clicks; once Alt is released, Shift-click on a locked panel is a quick move. Press Control again to unlock and close it. Without Shulker Box Tooltip, Control in State B locks the compact preview.
- The State C panel is **interactive** in the player inventory screen (survival and adventure) when the server has the mod. In every other screen (chests, creative), and on servers without the mod, it is display-only.

### Interacting with the full preview

The 27 preview cells are real inventory menu slots while the panel is bound, so every gesture is ordinary vanilla inventory behavior, predicted on the client and applied by the server:

- Left-click: take a stack, deposit, merge or swap with the carried stack.
- Right-click: take half (rounded up), or place one item.
- Drag with left or right button: distribute evenly, or one each, across preview cells and normal inventory slots in the same gesture.
- Double-click: collect matching items from both inventories.
- Shift-click: move a stack out of the box into the inventory, or (while bound) from the inventory into the box. Other shulker boxes keep the vanilla inventory/hotbar route, since they cannot be stored.
- Number keys: swap with the hotbar (the offhand key uses the same vanilla swap). Swapping the box into itself is refused.

Clicks on the panel never reach the slots behind it. The rest of the inventory stays usable while the panel is open.

### Shulker Box Tooltip compatibility

Shulker Box Tooltip is optional. When it is installed it owns States A and B (its own wording, layout and controls, and its config is never touched), so there is never a second tooltip. Seamless Shulkers only adds the pinned State C panel on Shift + Alt, which temporarily replaces the other tooltip while it is open.

### Server safety

All inventory changes are made by the server. A panel binding names the inventory slot and a hash of the exact box the client sees. The server refuses it if the box differs, is stacked, is not a shulker, sits in an armor slot or the offhand, if another container is open, or if the player is a spectator. A binding ends as soon as that box changes or moves, and old release packets carry tokens so they cannot end a newer binding. Stale or forged clicks are corrected with a full resync. Item components (names, damage, enchantments) are preserved, and shulker boxes cannot be put inside the box.

## Configuration libraries

All GUI libraries are optional. To use Fayber's Modern Config UI, install compatible Minecraft 26.3 builds of [Modern Config](https://github.com/Fayberr/modern-config/tree/fabric/26.3) and [Modern GUI](https://github.com/Fayberr/modern-gui/tree/fabric/26.3) together. Otherwise install [Cloth Config](https://modrinth.com/mod/cloth-config). [Mod Menu](https://modrinth.com/mod/modmenu) adds the Mods list button. The client command works regardless of which GUI is used.

Options: shulker preview on/off, editable full preview on/off, hotbar auto-refill, Space trading refill, refill threshold and target, Easy Place supply mode.

## Installation

1. Install Minecraft 26.3 with Fabric Loader (0.19.5+), Fabric API and Java 25.
2. Download the `seamless-shulkers-jar` artifact from a **successful Build workflow** run on [GitHub Actions](https://github.com/Fayberr/seamless-shulkers/actions/workflows/build.yml). Do not install the `-sources.jar`.
3. Place the mod JAR in the client's `mods` directory. For multiplayer, install the same JAR into your Fabric server's `mods` directory.
4. Add Litematica and MaLiLib on the client for schematic modes. Add Cloth Config and Mod Menu, or both Modern Config and Modern GUI, if desired.

## Development and validation

- `gradle build` (Gradle 9.7.1, JDK 25): compiles, runs the unit tests and the server game tests.
- `xvfb-run -a -s "-screen 0 1920x1080x24" gradle runClientGameTest`: client game test with real input, in singleplayer and on a dedicated server. Add `-PwithSbt` to run it with Shulker Box Tooltip 5.4.2 on the runtime classpath (never bundled). Screenshots land in `build/run/clientGameTest/screenshots`.
- GitHub Actions: `Build` (publishes the JAR), `Client Game Test` (manual trigger only; with and without Shulker Box Tooltip, uploads screenshots), `Litematica 26.3 Compatibility`, `Dedicated Server Smoke`, `Client Loader Smoke`.

### Debug build

`gradle build -PdebugBuild` (or the `seamless-shulkers-debug-jar` CI artifact) produces `seamless-shulkers-<version>-debug.jar`. It writes every mouse button, every container click the client sends and predicts, every server correction, the panel's open/lock/bind/release decisions, and the server's handling of each click to `logs/latest.log` with the prefix `[SS-DEBUG]`, plus the list of loaded mods at startup. The same trace can be enabled in a release JAR with the JVM flag `-Dseamlessshulkers.debug=true`. Use it only for diagnosis; it logs a lot.

**A green build is not proof that every in-game interaction works.** Test with disposable worlds and inventories first.

## License

No project license has been selected by the owner. This repository's original code was built without copying Shulker Box Tooltip source; publishing code does not implicitly license it for redistribution. Minecraft assets and separately installed dependencies retain their own licenses.
