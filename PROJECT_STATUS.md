# My Drops - local first build

22 October 2026 (America/Vancouver). Separate repository on `feature/initial-my-drops`. User reports the plugin works well after in-game testing and explicitly authorized public source publication and Plugin Hub submission. Native settings screenshot: `docs/settings.png`. Submission preparation is underway; upstream review is required before Plugin Hub availability.

## Initial release preparation

The concise player README now links to detailed build, testing and limitation notes in `docs/development.md`. The native 240x655 settings screenshot has been reviewed: controls are legible without clipping. It shows the user's food/potion setup with a red outline; GIM, labels and tile fill are off, so the screenshot does not establish those modes or all live-test cases. User-reported general own-drop testing is accepted as live feedback. Real GIM ownership/visibility transitions and multi-client performance remain unverified.

Descriptor explicitly selects `build=standard`, matching current Hub tooling and the absence of additional runtime dependencies. Submission compliance and QA reviews approve publication conditional on the final build, exact staged-file review, and an immutable public source commit. UI review approves the native settings presentation. Sources rechecked 22 October 2026: official Plugin Hub README, standard build/template descriptor, Jagex client guidance and RuneLite rejected features. Source publication and the new manifest use feature branches; no push to main is needed. The earlier sections below retain the initial development/test evidence.

Final release validation: `gradlew.bat --offline test build --rerun-tasks` passed with all tasks executed and **69 tests, zero failures/errors** (25 tracker, 21 integration/lifecycle, 16 overlay, 7 filter). The 18,393-byte thin JAR includes the explicit standard-build descriptor; SHA-256 for this local build instance is `B1A090313FCB781719399B8AB55D2340A21F480DDAF315949CF05E2DAC333A16`. This hash identifies the build instance, not a claim of reproducible archive timestamps. Gradle wrapper SHA-256 is `A8451EEDA314D0568B5340498B36EDF147A8F0D692C5FF58082D477ABE9146E4`. Public source repository: https://github.com/caasssh/runelite-my-drops-plugin . Plugin Hub submission pins the exact published source commit, not the local JAR.

## Implemented and checked

Five production Java files / 479 source lines. Config-only UI, resource category checkboxes, explicit extra item names, all-items option, individual item outlines, optional ground tile fill, optional labels and backgrounds, separate colors/opacity, outline width, and optional GIM group items with a separate color. No runtime dependencies beyond RuneLite, no network/data export, and no automated input.

User explicitly selected one color for GIM group-owned items without Party setup. No named-player list or Party transport was implemented. GIM candidates come only from locally emitted ItemSpawned/ItemQuantityChanged when OWNERSHIP_GROUP is present, including loot. They are retained while the optional toggle is off, capped at 256 with deterministic oldest-first eviction. Rendering rechecks ownership, applies the shared resource filter, uses group color/alpha for outline and text, multiplies group alpha by tile-fill alpha for fill, and hides the whole group mark at zero group opacity. Own confirmed marks win duplicate-object and mixed-tile-fill precedence; backgrounds stay shared.

Detection combines local inventory Drop intent with exact item ID/world view, clicked/current player tile, a self-owned ground spawn/quantity increase, and inventory loss inside a fixed three-tick window. Quantity budgets and matching candidate totals fail closed on ambiguous observations. Completed/ambiguous intents are consumed; another inventory action on the same item cancels intent. This remains an inference using public events, not authoritative provenance.

Food/drink categories use item-definition Eat/Drink actions. Ammo uses explicit common name suffixes and metal throwing-knife patterns; runes use rune/runes suffixes. Noted variants use base definitions. Classification and display strings are computed once per confirmed item; changing filter settings applies immediately to tracked items.

## Validation

- Exact pinned RuneLite **1.13.3** artifacts compiled with JDK **11.0.8.10-hotspot** and Gradle **8.2**.
- `gradlew.bat --offline test build renderPreview`: **BUILD SUCCESSFUL**. **69 tests, zero failures/errors**: tracker 25, integration/lifecycle 21, resource filter 7, rendering 16.
- Tests cover missing intent/ground/inventory evidence, ownership/tile/world-view rejection, moving-tile acceptance, expiry, rapid drops, stale-loot ambiguity, requested-quantity limits, quantity deltas, pickup/despawn, cancellation, scene reset/shutdown/unload, active/candidate caps, noted resources, action filtering, utility-knife exclusion, exact extras, cached definitions, opacity, per-item outlines, and one fill per tile.
- GIM regressions cover observation without local Drop/inventory loss, toggle-off retention, SELF/OTHER exclusion, quantity changes and cached definitions, despawn/loading/shutdown/world-view unload, oldest-first cap eviction, own-label preservation, current GROUP/SELF/OTHER/NONE rendering, separate outline/text color, shared filter, alpha multiplication, zero-alpha backgrounds, duplicate-object suppression, and own fill priority on mixed tiles.
- Thin JAR `build/libs/my-drops-0.1.0.jar`: **18,380 bytes**. Only production classes, manifest, descriptor, and BSD licence; tests, previews, and dependencies excluded.
- SHA-256: `6E15212DA1B6935FDBC7D200AA399A4E6EE18F005E7A6005763E00706AA3ABE3`.
- `git diff --check` passes; this is an initial untracked repository, so build/source/JAR inspection provides substantive validation beyond that command.
- UI reviewer inspected updated `build/previews/appearance-100.png` (1115x370) and `appearance-150.png` (1672x555), comparing own outlines, labels, group-owned items/loot, and mixed own/group piles: no label/title clipping or overlap; own/group colors are distinct and own mixed-tile fill wins. These are illustrative projected scenes with placeholder item shapes, not live RuneLite screenshots; native settings and actual outlines remain live checks.

## Performance and lifecycle

Only item/menu/game-state/world-view events and GameTick are subscribed. No ClientTick, scene-wide scans, polling, price lookups, network requests, asset decoding, panel rebuilds, or cross-plugin configuration edits. Idle GameTick checks inventory and the empty tracker; active work is bounded by 28 pending IDs, 28 ground candidates per ID, 256 own tracked objects and 256 GIM candidates (at most 512 cached labels). Inventory counts run only for pending IDs. Group definitions are read once per retained candidate; quantity changes reuse cached classification. Rendering visits own marks and, only when enabled/visible, GIM candidates; filter names/categories/text are cached, category mask/colors are read once per render, tile fill draws once per tile, and disabled/transparent outlines skip model work. A reused small per-tile row map positions labels. Game-state loading/logout/hops clear state; explicit despawn/unload/shutdown cleanup is included.

One/multiple-client CPU, allocation behavior, frame pacing, and native outlines remain live-test requirements. No measured live performance claim is made.

## Required review outcomes

- **runelite-plugin-compliance: APPROVE WITH CONDITIONS** before implementation. Public API/events and passive local rendering only; no ownership-only provenance, cross-plugin settings mutation, automation, or unreviewed runtime dependencies. Conditions addressed in implementation and README; public release still requires normal Hub review.
- **runelite-ui-review: APPROVE WITH CONDITIONS** after appearance preview inspection at 100%/150%. Native settings, actual model outline/alpha, camera motion, mixed piles, and core Ground Items coexistence remain live checks.
- **runelite-qa-release: APPROVE WITH CONDITIONS** after code/test report review. Stale-candidate attribution, quantity/window budgets, utility knives and hot-path normalization findings corrected. Conditions: accurate correlation/false-negative/merged-stack limits and the README live checklist; no public/live correctness claim.
- No combat, item-value, points, scaling or raid formula was added or changed, so no mechanics-formula review was needed.
- **GIM extension preimplementation compliance: APPROVE WITH CONDITIONS** for local ownership rendering with no Party/name/provenance inference. Conditions addressed: explicit loot inclusion, current-ownership check, bounded/oldest-first candidate handling, reset/cleanup, resource filters and own precedence. UI design and implementation reviewed for color/opacity, mixed piles and zero-alpha behavior. QA found no blocking defects; requested quantity/unload/NONE tests are included in the 69-test build. Actual GIM ownership/visibility transitions remain unverified live.

## Evidence log

Sources accessed 21 October 2026. Source branches can change; compilation checks target the pinned 1.13.3 public artifacts.

| Claim | Authoritative source | Implementation consequence |
| --- | --- | --- |
| Client rules are review-gated; this feature is assessed as passive display, not individually Jagex-approved | https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1 | No input generation, gameplay decisions/actions or game-state changes |
| Hub requirements and rejected features apply; modifying other plugin settings is disallowed | https://github.com/runelite/plugin-hub/blob/master/README.md ; https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features | Own config/overlay; no core Ground Items config changes; no submission claim |
| TileItem exposes ownership/private/quantity but no deliberate-drop-origin flag | https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/TileItem.java | Correlate events conservatively; document limits |
| Core Ground Items Drops filter accepts SELF and GROUP loot | https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/grounditems/GroundItemsPlugin.java | Ownership filter cannot implement the requested own-inventory-drop scope |
| Menu intent and confirmed ground events are public | https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/events/MenuOptionClicked.java ; https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/events/ItemSpawned.java ; https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/events/ItemQuantityChanged.java | No menu-only marks; require observed ground changes plus inventory loss |
| Individual item outline overload exists | https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/overlay/outline/ModelOutlineRenderer.java | Outline specific TileItem instead of whole pile |
| GROUP is an ownership category without a player name or deliberate-drop provenance | https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/TileItem.java | Optional GIM color includes group loot; no named attribution or Party needed; current ownership rechecked |

## Remaining live checks and known limits

Run all seven smoke-test groups in README before claiming live readiness. In particular, own dropped-item ownership and menu semantics, actual item outline alpha, event order, multiple same-ID clicks spanning server ticks, and scene/world-view behavior need game-client verification. GIM mode requires real two-account checks of ownership/visibility transitions, loot inclusion, mixed piles, cap behavior and toggle interaction. It does not backfill pre-existing scene items or discover a transition to GROUP without a new item event; changes away from GROUP suppress existing group marks at render. Consuming a combined own intent on its first confirmed inventory loss can intentionally miss later queued drops. Simultaneous same-item loot/failed Drop/unrelated loss is not perfectly disambiguated. Merged stacks receive a whole-stack mark; world/scene loading clears marks rather than reconstructing provenance. Filters are explicit approximations; native Ground Items text can duplicate this plugin's optional labels. Tile fill colors ground tiles, not model faces.

## IntelliJ development launch repair

User's IntelliJ-generated `:MyDropsLauncher.main()` task exited before client startup because assertions were disabled. The original `-ea` applied only to the named `run` task. All Gradle JavaExec tasks now use `enableAssertions = true`, including generated main tasks. The test-only launcher automatically adds `--developer-mode`, exposing the built-in Jagex Accounts development-login panel. Direct IntelliJ Application configurations still need VM option `-ea`; README documents both paths. `gradlew.bat --offline test` passed all 69 tests after these changes. No production plugin code or existing thin JAR was changed, and no account credentials were inspected or handled. Interactive startup/login remains to be confirmed by the user.
