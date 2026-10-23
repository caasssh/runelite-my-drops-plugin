# My Drops

Highlight items you drop with custom colors, outlines, tile fill, and optional item labels.

## How to use

1. Install **My Drops** from RuneLite's Plugin Hub once the submission is approved, then enable it.
2. Leave **Resources only** on and choose food, potions, ammunition, or runes. Add other exact item names under **Extra items**, separated by commas. Turn Resources only off to include all item types.
3. Under **Appearance**, choose your outline, tile fill, text, and background colors. Enable **Recolor menu names** to give confirmed own-drop names a separate right-click menu color.
4. Drop an item from your inventory to highlight it.

For group ironmen, enable **Highlight GIM group items** and choose a separate **GIM group color**. This includes group-owned loot and uses the same item filters. No Party setup is needed, and individual droppers are not identified.

![My Drops settings in RuneLite, configured for food and potions with a red item outline](docs/settings.png)

Optional on-ground names can overlap labels from RuneLite's Ground Items plugin; leave names off here if you prefer its labels. Menu recoloring skips ambiguous same-item piles. The user observed repeated loading-line returns working; near expiry, a mark may disappear shortly before its item does. Matching remains conservative because RuneLite does not expose a stable identity for a reloaded ground item. GIM behavior and menu-color coexistence still need separate live verification.

[Development and testing](docs/development.md)
