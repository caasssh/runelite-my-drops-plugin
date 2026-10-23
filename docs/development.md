# My Drops

A small, standalone RuneLite plugin that marks items you deliberately drop from your inventory, with optional GIM group-item highlighting. No added runtime libraries, network requests, sidebar, or persisted drop history.

## Settings

- **Resources only:** food (Eat action), potions/drinks (Drink action), common ammunition, and runes. Each category has a checkbox. The same filter applies to own drops and enabled GIM items; disable it to include all item types.
- **Extra items:** comma-separated exact names for additional resources, e.g. `Logs, Pure essence`. Matching ignores capitalization; noted items use their unnoted name.
- **Highlight GIM group items:** optional (off by default), includes locally observed group-owned items and loot. No RuneLite Party, teammate installation, or player-name list is needed.
- **GIM group color:** separate color/opacity for group outlines and names. Group tile fill uses this color and combines its opacity with Tile fill color opacity. Text backgrounds stay shared; zero group opacity hides all group marks.
- **Item outline:** individual item outline, color, opacity, and width.
- **Ground tile fill:** optional color beneath matching items, with opacity. This fills the tile rather than recoloring the item model.
- **Show item names:** optional names and quantities, custom text color, and background color. Every color picker includes opacity; set background opacity to zero for no background.

Outlines are on by default; names and tile fill are off. This default works alongside core Ground Items without duplicate names. Enabling names here can duplicate labels drawn by Ground Items; choose your preferred labeling plugin yourself.

Your confirmed drops use your own appearance colors. On a mixed tile, your fill color takes priority; each item's outline/name keeps its own or group color. The same item object is never highlighted twice.

## Build and try locally

Use JDK 11 with the included Gradle wrapper:

```powershell
$env:JAVA_HOME = 'C:/Program Files/AdoptOpenJDK/jdk-11.0.8.10-hotspot'
./gradlew.bat test build
./gradlew.bat run
```

The `run` task launches a development RuneLite client with My Drops available in its plugin list. Enable **My Drops**, then open its settings. The JAR is `build/libs/my-drops-0.1.0.jar`; it is not a drag-and-drop installer for the ordinary RuneLite client. Public availability requires Plugin Hub submission/review.

In IntelliJ, reload the Gradle project, open `src/test/java/com/mydrops/MyDropsLauncher.java`, and run its `main()` using the gutter play button. Assertions are enabled for Gradle JavaExec tasks, including IntelliJ's generated main task; the test launcher adds developer mode automatically. If running as a direct IntelliJ Application configuration instead of through Gradle, add `-ea` under VM options yourself.

For a loading-line check, drop an item, run beyond its loaded scene and return twice while the item remains on the ground; repeat after it becomes public. The mark can return only when the item enters the loaded scene again. The user observed repeated successful returns, including after the ownership change. In the last near-expiry test, the highlight disappeared about two seconds before the item itself; the saved fingerprint never extends beyond its original expiry. The development launcher also loads RuneLite's built-in Developer Tools if normal plugin discovery misses them. Temporary item-replay diagnostics used during development have been removed.

For a Jagex Account, use the development client's **Jagex Accounts** sidebar panel, click **Add Jagex Account**, complete the login yourself, select a character, and click **Play Now**. This follows RuneLite's [current developer-login guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts); no launcher credential export is needed.

`./gradlew.bat renderPreview` generates illustrative appearance previews at 100% and 150% scale. Their scene/item shapes are placeholders; actual overlay code draws the labels, backgrounds, and tile fill.

## Detection and limits

The plugin observes an inventory Drop action, a matching self-owned ground spawn or stack increase on the clicked/current player tile in the same world view, and the corresponding inventory quantity reduction within three ticks. It does not generate input or change game state. RuneLite ownership alone includes monster loot, so it is insufficient for this feature.

GIM mode separately uses RuneLite's current `OWNERSHIP_GROUP` flag. It includes group-owned loot and does not identify a dropper or prove that someone deliberately dropped the item. It only marks items delivered to your client through ordinary item events; it never reveals invisible items. The flag is rechecked when drawing, so an item that stops being group-owned loses its group mark. This mode requires live GIM verification before release.

This is a conservative correlation, not an authoritative drop-origin flag. Ambiguous quantities are rejected. Unusual event timing, movement, batched same-item drops across server ticks, replacement of pending intent after movement, or simultaneous same-item gains can miss a drop. Exact same-item loot coinciding with an unsuccessful Drop and an unrelated inventory loss cannot be perfectly disambiguated by these public events. Food/Drink actions and ammunition-name patterns are lightweight filters, not an exhaustive resource catalog; use Extra items for exceptions.

Tracking starts with the exact ground-item object. A merged stack is highlighted as a whole; individual ownership/origin within that stack cannot be colored separately. Picking up/despawning that object removes its active mark. A confirmed drop also gets a bounded fingerprint until its originally observed despawn time. After a scene load or top-level world-view unload while logged in, a unique replay with matching world, view, tile, ID, quantity, and timer shifts of zero or one tick earlier can restore the mark. A late spawn outside that replay window needs an exact or one-tick-earlier timer pair before the same candidate is checked again. SELF ownership is accepted; NONE ownership is accepted only after the item's original public visibility time. An OTHER-owned replacement usually requires a recent, uniquely identified snapshot of the tracked item plus its explicit removal. A separate local retest path handles a missed transition across scene loads: it records a scalar witness from a recently bound confirmed own item with a unique pile and known inventory count before loading, then accepts one unique OTHER replay only if world/view ID, tile/plane, ID, quantity, active scene, instance template chunk (in instances), and last observed despawn deadline or one tick earlier agree, never later than the original expiry after the original visibility deadline. Its changed visible time is not treated as identity proof. If the removal tile is inactive, no item is marked immediately: the plugin retains a bounded scalar witness while the item is out of range, through empty scene loads, only until the earlier of its original and last observed ground expiry. A unique OTHER spawn before loading can contribute a timer witness; after loading, a fresh item in the current active scene must match its world, view ID, tile, ID, quantity, and observed expiry within one tick. Inventory gain, same-place quantity change, another same-ID pile, ambiguity, world change, logout, or expiry cancels the witness. SELF/NONE replay cancels the OTHER handoff and follows the usual confirmed-item path. A handoff while the item remains in view settles by the next tick; an out-of-range handoff waits for a return replay and then three settle ticks. Only accepted OTHER replacements can replay across later loading lines. Ordinary OTHER and GROUP candidates, changed or duplicate matches, and expired records remain unmarked. RuneLite exposes no stable item identity across scene reloads, so another item with identical attributes and event timing cannot be fully distinguished; even a matching instance template chunk does not prove the same raid instance. Logging out, hopping, and disabling the plugin discard the fingerprints. Active highlights have no separate configurable duration; pickup or despawn clears them, and a retained mark can end slightly before ground despawn when its original expiry is reached. Safety caps are 28 pending own-drop IDs, 28 candidate changes per ID, 256 confirmed own-drop objects, 256 retained fingerprints, and 256 observed GIM objects (at most 512 cached labels). Excess/ambiguous own candidates are discarded; new GIM candidates evict the oldest GIM observation.

For an already accepted OTHER item, a same-place spawn just before loading may briefly report a despawn deadline one tick beyond the original cap. This remains an unmarked provisional candidate. It must be followed by a load on the next tick and a unique active-scene OTHER replay whose timers shift together back within the original deadline; otherwise the saved mark is discarded. This cannot prove item identity if another player drops an identical item with the same timing.

Optional **Recolor menu names** uses **Menu name color** for the name in a confirmed own drop's right-click ground-item entries. It follows the same resource filter as the overlay, does not color GIM-only items, and leaves menu actions and ordering intact. A menu entry identifies an item by type and tile rather than by ground-item object; if two stacks with the same item ID share a tile, My Drops leaves that menu name unchanged. This option is off by default and separate from the on-ground **Text color** setting. The color is RGB; the menu has no opacity control. Coexistence with RuneLite Ground Items menu coloring still needs live verification.

GIM candidates are retained while the GIM toggle is off, so turning it on covers items already observed during that plugin session. Enabling the plugin halfway through a scene does not scan/backfill existing items. Group ownership first appearing without a new spawn or quantity event can also be missed; these are deliberate limits of the small event-driven implementation.

All drop observations stay in memory. Only ordinary RuneLite plugin preferences are persisted.

## Live smoke test

1. Drop food, a potion, arrows, runes, and a non-resource. Confirm only selected categories mark; toggle Resources only and Extra items while marked items are present.
2. Check normal and shift-click drops, rapid repeated same-item drops, a whole ammunition stack, merging onto a stack, partial stack pickup, full pickup, and natural despawn.
3. With GIM highlighting off, check cancelled drop warnings, eating/drinking after a cancelled drop, monster loot and other players' drops on the same tile. Ordinary unrelated loot should remain unmarked; ambiguous cases should be skipped.
4. Drop an item, cross a raid loading line, and return while the item remains on the ground. Repeat two far-away returns after its ownership becomes public; check whether its highlight returns each time once the item enters the loaded scene. Try a competing same-ID stack, a pickup, and expiry: these must stay unmarked. Also check a fresh drop after loading, then hopping, logout, disable/re-enable, and secondary-world-view unloading for stale marks.
5. Check all appearance modes, every opacity slider including zero, stacked labels, bright/dark terrain, camera movement, normal panel width at 100%/150% scale, and core Ground Items enabled.
6. Compare idle and repeated-drop behavior in one and several clients for CPU, memory, and frame pacing. Runtime bounds and automated tests do not substitute for this check.
7. With two GIM accounts, enable Highlight GIM group items and check group supplies, group-owned loot, non-group items, ownership becoming public, quantity changes, and mixed own/group piles. Verify separate colors, combined fill opacity, zero group opacity, cleanup, and off/on behavior. No Party setup or teammate plugin is required for this mode.

Build/test evidence, reviews, sources, and remaining limitations are in [PROJECT_STATUS.md](../PROJECT_STATUS.md).
