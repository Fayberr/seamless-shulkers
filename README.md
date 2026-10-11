# Seamless Shulkers

Fabric mod for **Minecraft 26.3** (Java 25). A single mod JAR provides both client and dedicated-server functionality.

**Status: pre-release (0.1.0-alpha.2).** CI compiles the mod and checks official Litematica bytecode compatibility. Multiplayer inventory conservation and in-game UI interactions still require hands-on verification before a stable release.

## Features

- **27-slot Shulker Preview:** Vanilla-style inventory texture. Hold **Alt** while hovering a carried shulker to pin the preview and pick up/place item stacks. Release Alt to close it.
- **Auto-Refill:** Optional (off by default) refill of the selected hotbar stack from carried shulkers when its count drops below a configured threshold. This independent setting does not override the selected Easy Place supply mode.
- **Easy Place Supply Modes:**
  - **DIRECT (default):** Optional integration with Litematica 26.3. When Litematica needs an item absent from the normal inventory, a one-item virtual hand use temporarily supplies it from a carried shulker. Vanilla executes placement with Litematica's chosen hit target, then the server deducts exactly the block that vanilla consumed. Nothing remains in the player's inventory.
  - **INVENTORY_REFILL:** Requests the needed block from a carried shulker into a free hotbar slot. A second Easy Place use may be necessary after network latency.
  - **OFF:** No special Litematica material supply.
- **Trading:** Press **Space** in a villager screen to request missing payment items from carried shulkers for the selected offer.
- **Client + Server:** Works in singleplayer using the integrated server. For multiplayer, install the **same JAR** on both the Fabric client and the dedicated Fabric server. On servers without it, the preview is display-only.
- **Shulker Box Tooltip compatibility:** On normal hover, Shulker Box Tooltip remains in control when installed (and preferred). While hovering a carried shulker, press and hold **Left Alt** to pin the interactive Seamless Shulkers 27-slot preview. Its tooltip is suppressed for that frame so it cannot cover the clickable preview. Move the mouse into the pinned slots and release Alt to close it. No change to the other mod's configuration is required.
- **Settings:** Modern Config + Modern GUI (preferred), otherwise Cloth Config, otherwise a minimal built-in screen. Both **Mod Menu**'s configuration button and the client command **`/seamlessshulkers`** use the same router and save into `config/seamless-shulkers.json`.

### Configuration libraries

All GUI libraries are optional. To use Fayber's Modern Config UI, install compatible Minecraft 26.3 builds of [Modern Config](https://github.com/Fayberr/modern-config/tree/fabric/26.3) and [Modern GUI](https://github.com/Fayberr/modern-gui/tree/fabric/26.3) together. Otherwise install [Cloth Config](https://modrinth.com/mod/cloth-config). [Mod Menu](https://modrinth.com/mod/modmenu) adds the Mods list button. Both the Mod Menu button and the client command are available independently of the GUI choice.

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
