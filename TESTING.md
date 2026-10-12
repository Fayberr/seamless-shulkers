# Seamless Shulkers Testing (Minecraft 26.3)

A successful Gradle build alone is **not** sufficient. This file lists what the automated tests cover and what still needs to be checked by hand in a disposable world.

## Automated

| Command | Covers |
| --- | --- |
| `gradle build` | Unit tests (`src/test`): compact merge and sort, empty box, grid sizes, slot hit test on the frame border, count labels, placement, tint. Server game tests (`src/gametest`, run by `runGameTest`) listed below |
| `xvfb-run -a -s "-screen 0 1920x1080x24" gradle runClientGameTest` | Client game test with real mouse and keyboard input, once in singleplayer and once on a dedicated server |
| same with `-PwithSbt` | The same client test with Shulker Box Tooltip 5.4.2 loaded |

### Server game tests (`PreviewServerGameTest`)

Each test drives a real `ServerPlayer` through `handleContainerClick` packets, predicts every click on a mirror player exactly as the client does, applies the server's answers to the mirror, and checks that both sides converge and that every item is conserved (inventory, cursor and box contents).

- `pickupDepositMergeAndSplit`: scenarios A to E (take 12, deposit 12 into the inventory, put them back, 12 + 17 = 29, right-click 46 takes 23, right-click into an empty cell places 1).
- `dragDistributesAcrossBothInventories`: left drag of 64 over four slots (two in the box, two in the inventory) gives 16 each, right drag gives 1 each.
- `quickMoveCollectAndSwap`: Shift-click out and in, another shulker keeps the vanilla route, double-click collects across both inventories, number-key swaps in and out, self swap refused.
- `unboundAndForgedClicksAreHarmless`: clicks into an unbound preview, slot 500, and a client that lies about the result are corrected without changes.
- `staleClickResyncsWithoutPreviewSlots`: a stale state id leads to a full resync of the 46 vanilla slots only.
- `bindRefusals`: wrong hash, different box, stacked boxes, oversized container, non-shulker, armor and offhand slots, wrong container id, other container open, spectator, refused rebind drops the old binding, release.
- `bindingDiesWhenTheBoxChanges`: an identical copy cannot inherit a binding, the box changing or being moved ends it.
- `componentsAndNesting`: named and damaged items and named boxes keep their components, shulker boxes cannot be nested.
- `refillWhileBound`: auto refill from the bound box keeps both views consistent.

### Client game test (`PreviewClientGameTest`)

A box with 12, 17 and 46 arrows and 5 stone, plus one loose arrow:

- State A shows the summary and hint and no item list. State B shows the compact merged preview. State C opens the full grid, the server binds it.
- With Shulker Box Tooltip loaded: A and B are its tooltips with none of our lines, State C shows only our panel.
- Scenarios A to F through real clicks and a real mouse drag, checked against the server inventory.
- Control locks the panel so the keys can be released, Control again closes it and the server releases the binding.
- Clicks with real modifier flags (the harness drops them from simulated mouse input, so these are dispatched to the screen directly): Shift+Alt click picks up, a click with Shift+Alt+Ctrl held after locking picks up, a locked click without keys picks up, a locked Shift-click quick-moves out of and into the box.
- Item conservation and client/server box equality at the end.
- `/seamlessshulkers` opens the settings screen.

Screenshots of every state are written to `build/run/clientGameTest/screenshots`.

## Debug build

For in-game reports, install the debug JAR (see README) and send the `[SS-DEBUG]` lines from `logs/latest.log` (`findstr SS-DEBUG latest.log` on Windows).

## Manual checklist (not covered by automated tests)

### Preview

- [ ] Real-lag server: repeated fast transfers, drags and double-clicks with 200+ ms latency. No loss, no duplication, no stuck ghost items.
- [ ] Offhand key (F) over a preview cell swaps with the offhand.
- [ ] Double-click collect with real mouse input.
- [ ] Two players editing different boxes at the same time cannot affect each other.
- [ ] Chest, barrel and creative screens: the panel is display-only and no click changes anything.
- [ ] Server without the mod: the panel is display-only.
- [ ] With Shulker Box Tooltip: its own lock key and our Control lock pressed together do not leave a stuck tooltip.

### Litematica Easy Place

- [ ] On the official Litematica 0.29.1 (Minecraft 26.3) with MaLiLib 0.30.2, enable Easy Place and set the supply mode to DIRECT.
- [ ] Carry one shulker containing several block types, including stairs, slabs, logs, signs and blocks with specific item components; keep matching blocks out of the normal inventory.
- [ ] Easy Place each block of an active schematic: one item comes from the right shulker slot, orientations are kept, the hotbar is untouched.
- [ ] Right-click an adjacent interactable block: no unrelated interactions or extra placements.
- [ ] Last remaining item and empty slots: one placement consumes exactly one block.
- [ ] Failed placement (occupied spot, too far away, protected area) consumes nothing.
- [ ] Legacy and experimental rewrite implementation modes.
- [ ] INVENTORY_REFILL: items move only to an empty hotbar slot and placement resumes after the server response.
- [ ] OFF: vanilla and Litematica behavior unchanged.

### Trading

- [ ] Fletcher stick-for-emerald trade with sticks only in a carried shulker: Space refills the payment slot.
- [ ] Trades with two ingredients take the correct costs.
- [ ] No refill for out-of-stock trades, wrong item components or empty shulkers.

### Config routes

- [ ] Mod Menu + Cloth Config: the config button opens the Cloth screen.
- [ ] Mod Menu + Modern Config + Modern GUI: the button opens the Modern screen.
- [ ] All three UI libraries: Modern wins, no duplicate screens.
- [ ] Without Mod Menu, `/seamlessshulkers` opens the right screen.
- [ ] Changed options persist across a restart in `config/seamless-shulkers.json`.

**Release gate:** do not label a release stable until the manual items above are checked and any failures fixed.
