# Seamless Shulkers Acceptance Checklist (Minecraft 26.3)

Use a disposable survival world and a fresh Fabric multiplayer test world. Record expected and actual item counts for every test. A successful Gradle build alone is **not** sufficient.

## Preview / inventory conservation

- [ ] With only Seamless Shulkers: hover each colored shulker, see vanilla 9x3 preview.
- [ ] Hold Alt over the shulker, move cursor to preview, pick up stacks (left click), half stacks (right click), insert and merge stacks, release Alt. Nothing is lost or duplicated.
- [ ] Move shulker mid-interaction; ensure the server rejects stale slots rather than moving items from the wrong box.
- [ ] Leave an item on the cursor while releasing Alt; it remains available.
- [ ] With Shulker Box Tooltip 5.4.2 (26.3) installed, normal Shift hover is handled by that mod; hold Left Alt (with or without Shift) directly over the box to replace the deferred tooltip with the clickable Seamless Shulkers inventory. No overlap or underlying tooltip should cover the panel.
- [ ] Continue holding Alt while moving the cursor into the 27 slots: the panel must remain fixed. Click both mouse buttons; then release Alt, confirm the preview disappears and regular Shulker Box Tooltip resumes.
- [ ] Toggle "Prefer Shulker Box Tooltip" off: only Seamless Shulkers preview appears on hover (with or without the other mod installed).

## Litematica Easy Place

- [ ] On the official Litematica 0.29.1 (Minecraft 26.3) with MaLiLib 0.30.2, enable Easy Place, set the Seamless Shulkers supply mode to DIRECT.
- [ ] Carry one shulker containing several block types, including stairs, slabs, logs, signs and blocks with specific item components; keep matching blocks out of normal inventory.
- [ ] Look at each block in an active schematic and use Easy Place: it should use one item from the right shulker slot, preserve orientations that Easy Place normally handles, and leave the player's original hotbar untouched.
- [ ] Right-click an adjacent interactable block. Confirm no unrelated interactions or accidental extra placements.
- [ ] Verify empty shulker slot and last remaining item behavior; one placement consumes exactly one block.
- [ ] Verify failed placement (occupied spot, too far away, protected area) consumes zero blocks.
- [ ] Repeat rapidly with large latency and other inventory modifications; confirm no item duplication or loss.
- [ ] Test Litematica legacy and experimental rewrite implementation modes.
- [ ] Set mode INVENTORY_REFILL: verify items move only to an empty hotbar slot and placement resumes after the server response.
- [ ] Set mode OFF: verify vanilla/Litematica behavior unchanged.

## Trading

- [ ] Choose the Fletcher stick-for-emerald trade, put sticks only in a carried shulker, press Space and confirm payment slot refill.
- [ ] Test trades requiring two ingredients; verify correct costs and no material swaps.
- [ ] Confirm no refill with out-of-stock trades, wrong item components, or empty shulkers.

## Config routes

- [ ] Install **Mod Menu + Cloth Config** only; the config button opens the Cloth screen.
- [ ] Install **Mod Menu + Modern Config + Modern GUI**; the button opens the Modern screen.
- [ ] Install all three UI libraries; Modern UI wins and no duplicate menu or settings appear.
- [ ] Remove Mod Menu; the command `/seamlessshulkers` continues to open the appropriate UI.
- [ ] Remove all UI libraries; built-in fallback screen remains available through command.
- [ ] Change DIRECT / INVENTORY_REFILL / OFF and restart Minecraft: JSON persists correctly.

## Dedicated server

- [ ] Install one identical mod JAR on a Fabric dedicated server and on its client, verify packets are accepted.
- [ ] Without server mod, client preview remains read-only and interactive/direct requests are disabled safely.
- [ ] Simultaneous players handling different shulkers cannot influence each other's items.
- [ ] Inspect latest CI server-smoke logs for runtime mixin failures.

**Release gate:** Do not label stable until tests above are verified and any failures fixed.
