# Seamless Shulkers

Fabric mod for Minecraft **26.3**. One JAR contains both client and server code.

**Status: alpha / integration testing needed.** Compiling is not proof of correct behavior in Minecraft. Do not trust this pre-release with valuable survival inventories until transfer tests pass.

## Implemented

- Server-authoritative extraction and insertion from carried shulker boxes, without placing the shulker box.
- Own shulker preview using the **vanilla Minecraft shulker inventory texture** and its 27-slot layout.
- Hold **Alt** while hovering a carried shulker to keep its preview open; while holding Alt, left/right-click slots to pick up or place items. Release Alt to dismiss.
- Default behavior when `shulkerboxtooltip` is present: keep its ordinary hover preview, and use Seamless Shulkers' interactive view when Alt is held.
- Automatic refills for the **selected hotbar slot**, using configurable target and threshold; recovering a fully exhausted selected slot while the use button remains pressed.
- Optional **Litematica schematic pick-block hook**: when a schematic block is missing from normal inventory, request it from a carried shulker into an available hotbar slot. Placement may require repeating the Easy Place action after the network response; this is not yet zero-delay Direct Placement.
- Villager trading: pressing **Space** in the normal merchant screen requests shulker contents for the **selected offer's payment slots**. The server validates the selected offer and costs, including trades requiring two items.
- A native configuration screen available via **`/seamlessshulkers`** (in-world client command), with persisted settings. Its dependency on Modern Config is not required.

## Not yet implemented or verified

- Fully seamless **Litematica Easy Place on the first click** for missing materials: an optional material-request hook exists, but it has not yet been integration-tested and waits for the server to refill a slot.
- **True direct schematic placement** without any intermediate inventory transfer.
- Integration with Modern Config/Modern GUI (their available branches target 26.1.x, not 26.3).
- Optional alternate binding for Alt, Mod Menu entrypoint, accessibility enhancements, input conflicts, shift-click behavior, and richer inventory editing.
- In-game client/server tests, packet/latency tests, and item-conservation regression tests.

## Install

Install Fabric Loader + Fabric API compatible with Minecraft 26.3 and Java 25. Put the same built mod JAR in your client's `mods` directory. For a dedicated multiplayer Fabric server, install the same JAR there too.

Singleplayer uses Minecraft's integrated server. The preview can appear on unsupported servers, but interactive transfers and refills require the matching server support.

## Build

GitHub Actions builds every push to `main`. Download the `seamless-shulkers-jar` artifact from a **successful** Build workflow. Source builds: `gradle build` using Gradle 9.7.1 and JDK 25.

## Development principles

No Shulker Box Tooltip source is copied. Minecraft's original textures and APIs are used. Every transfer originates from server-owned inventory data; the client cannot submit arbitrary ItemStacks. Always validate input indices and bound quantities.

## License

**Not chosen yet.** The repository's author retains copyright over original code; no open-source license is granted merely by publishing the repository. External dependency and Minecraft asset licenses remain in effect.
