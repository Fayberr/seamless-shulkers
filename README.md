# Seamless Shulkers

Fabric mod for **Minecraft 26.3** (Java 25). A single mod JAR provides both client and dedicated-server functionality.

**Status: pre-release (0.1.0-alpha.5).** CI compiles the mod and checks official Litematica bytecode compatibility. Multiplayer inventory conservation and in-game UI interactions still require hands-on verification before a stable release.

## Features

- **Shulker Box Tooltip-compatible preview:** Normal hover is left entirely to Shulker Box Tooltip, if installed. Otherwise normal vanilla tooltip remains available; hold **Shift** to preview the contents in compact form. Hold **Left Alt** over a carried shulker to lock a complete, native-tooltip-style 9x3 preview and access its slots. While Alt remains held, **click outside the preview to interact with your ordinary inventory**. The tooltip uses Minecraft's own tooltip background and bundle slot sprites, not a custom gray inventory window. The renderer is a greenfield implementation and does not copy Shulker Box Tooltip source.
- **Auto-Refill:** Optional (off by default) refill of the selected hotbar stack from carried shulkers when its count drops below a configured threshold. This independent setting does not override the selected Easy Place supply mode.
- **Easy Place Supply Modes:**
  - **DIRECT (default):** Optional integration with Litematica 26.3. When Litematica needs an item absent from the normal inventory, a one-item virtual hand use temporarily supplies it from a carried shulker. Vanilla executes placement with Litematica's chosen hit target, then the server deducts exactly the block that vanilla consumed. Nothing remains in the player's inventory.
  - **INVENTORY_REFILL:** Requests the needed block from a carried shulker into a free hotbar slot. A second Easy Place use may be necessary after network latency.
  - **OFF:** No special Litematica material supply.
- **Trading:** Press **Space** in a villager screen to request missing payment items from carried shulkers for the selected offer.
- **Client + Server:** Works in singleplayer using the integrated server. For multiplayer, install the **same JAR** on both the Fabric client and the dedicated Fabric server. On servers without it, the preview is display-only.
- **Shulker Box Tooltip compatibility:** Shift and other non-Alt controls continue to work normally when that mod is installed. Alt activates Seamless Shulkers' interactive full 27-slot preview above the other tooltip layer; the original tooltip is temporarily suppressed only for the locked view.
- **Settings:** Modern Config + Modern GUI (preferred), otherwise Cloth Config, otherwise a minimal built-in screen. Both **Mod Menu**'s configuration button and the client command **`/seamlessshulkers`** use the same router and save into `config/seamless-shulkers.json`.

### Configuration libraries

All GUI libraries are optional. To use Fayber's Modern Config UI, install compatible Minecraft 26.3 builds of [Modern Config](https://github.com/Fayberr/modern-config/tree/fabric/26.3) and [Modern GUI](https://github.com/Fayberr/modern-gui/tree/fabric/26.3) together. Otherwise install [Cloth Config](https://modrinth.com/mod/cloth-config). [Mod Menu](https://modrinth.com/mod/modmenu) adds the Mods list button. Both the Mod Menu button and the client command are available independently of the GUI choice.


### Interactive preview controls

| Gesture | Result |
| --- | --- |
| Hover Shulker | Original tooltip; with Shulker Box Tooltip installed, that mod owns it |
| Hold Shift (without Shulker Box Tooltip) | Compact read-only item preview |
| Hold Left Alt on Shulker | Pin a 27-slot interactive preview; release Alt to dismiss |
| Left-click a preview slot | Take its entire stack; or deposit/merge your entire carried stack |
| Right-click a preview slot | Take half (rounded up); or place **one** item into it (vanilla right-click semantics) |
| Left-click a normal inventory slot outside the preview, **while still holding Alt** | Deposit your carried stack in the ordinary inventory, as in vanilla |

Preview mouse events only intercept gestures starting **inside** the panel. They do not block the entire inventory or let preview mouse releases activate slots underneath.

## Installation

1. Install Minecraft 26.3 with Fabric Loader (0.19.5+), Fabric API, and Java 25.
2. Download a JAR from a **successful Build workflow** artifact on [GitHub Actions](https://github.com/Fayberr/seamless-shulkers/actions/workflows/build.yml). Do not install the `-sources.jar`.
3. Place the mod JAR in the client's `mods` directory. For multiplayer, install the same JAR into your Fabric server's `mods` directory.
4. Add Litematica and MaLiLib on the client for schematic modes. Add Cloth Config/Mod Menu, or both Modern Config and Modern GUI, if desired.

## Development and validation

- `gradle build` (Gradle 9.7.1 and JDK 25).
- GitHub Actions `Build` compiles and publishes pre-release JARs.
- `Litematica 26.3 Compatibility` downloads official Litematica 0.29.1 and checks its Easy Place bytecode entry points.
- `Dedicated Server Smoke` checks that the Fabric server boots with the server mixins loaded.
- Follow [TESTING.md](TESTING.md) for the manual feature acceptance and item conservation checklist.

**Do not assume compiled code implies the mod is fully tested in-game.** Test with disposable worlds/inventories first.

## License

No project license has been selected by the owner. This repository's original code was built without copying Shulker Box Tooltip source; publishing code does not implicitly license it for redistribution. Minecraft assets and separately installed dependencies retain their own licenses.
