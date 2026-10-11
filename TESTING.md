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

## Tooltip overlay regression (alpha.3)

- [ ] With Shulker Box Tooltip installed and preferred, normal Shift-hover still shows its full preview. Merely hovering without Shift should retain the regular item tooltip.
- [ ] Press Left Alt with cursor over a carried shulker. The interactive vanilla-texture 9x3 panel should appear **in front** of Shulker Box Tooltip's preview; no blank tooltip state.
- [ ] Press Alt while already holding Shift: the interactive panel must be visible, not obscured by the other mod's deferred tooltip.
- [ ] Move cursor from shulker item into the pinned overlay with Alt held. Preview remains visible and clickable until Alt is released.
- [ ] Set Prefer Shulker Box Tooltip OFF; our own hover preview should render even with the other mod installed.
- [ ] Set Seamless Shulkers Preview OFF; normal vanilla / Shulker Box Tooltip remains fully functional.

## Interactive preview mouse isolation and SBT-style design (alpha.4)

- [ ] When Shulker Box Tooltip is present, Shift-hover keeps showing its normal compact tooltip.
- [ ] With only Seamless Shulkers, ordinary hover shows a compact, dark item-tooltip window with name, item preview, registry ID, component count, stack count and Alt hint.
- [ ] Alt pins the full 9x3 grid with a tooltip-like purple border. Its position should remain near the hovered shulker; it must not always be anchored to the bottom of the screen.
- [ ] Put exactly 46 arrows into a shulker. Hold Alt, **left-click** arrows once; all 46 appear on the cursor, and the shulker slot empties. Neither an inventory slot behind the preview nor an adjacent slot is modified.
- [ ] Put exactly 46 arrows back. Hold Alt, **right-click** once; 23 arrows move to the cursor and 23 remain in the shulker.
- [ ] While holding Alt, move the mouse while still holding a mouse button, then release above an inventory slot behind the tooltip; no items transfer there.
- [ ] Start clicking a tooltip slot, release Alt before releasing the mouse button; the underlying inventory must still ignore that release.
- [ ] Release Alt without holding a mouse button and then left-click a normal inventory slot; the carried items can be deposited normally.
- [ ] Rapidly repeat transfers with lag, a full inventory, a partly occupied cursor stack, and multiple equal-looking shulkers. Verify total item conservation.
- [ ] A preview interaction must not trigger Quick Craft / slot-drag mechanics in the underlying container.

## Mouse/inventory interaction regression (alpha.5)

- [ ] With Shulker Box Tooltip installed, hovering and Shift preview are unchanged without Alt.
- [ ] Without Shulker Box Tooltip, Shift displays a compact item preview using vanilla tooltip background and 24px slot sprites.
- [ ] With 46 arrows in a shulker slot and an empty cursor, hold Alt and LEFT-click it: the full 46 must move to the cursor, with the box slot empty. After that, while still holding Alt, click an ordinary inventory slot OUTSIDE the preview: all 46 must be deposited there.
- [ ] With 46 arrows in the box and empty cursor, hold Alt and RIGHT-click: 23 must move to the cursor.
- [ ] With 12 arrows on cursor and 17 in the box, LEFT-click the preview stack: cursor empties, destination becomes 29. RIGHT-click instead: cursor becomes 11 and box becomes 18; this matches normal Minecraft controls.
- [ ] Press inside preview, drag over an inventory slot and release: no underlying slot may be accidentally clicked.
- [ ] Start click in normal inventory OUTSIDE the pinned preview and release normally: vanilla still handles the interaction.
- [ ] While holding Alt, click another inventory stack OUTSIDE the preview, and then place it into an empty slot INSIDE the preview.
