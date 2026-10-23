package com.mydrops;

import java.awt.Color;
import net.runelite.client.config.*;

@ConfigGroup(MyDropsConfig.GROUP)
public interface MyDropsConfig extends Config
{
    String GROUP = "mydrops";

    @ConfigSection(name = "Item filter", description = "Which own drops and optional GIM group items to mark", position = 0)
    String FILTER = "filter";
    @ConfigSection(name = "Appearance", description = "Colors include an opacity slider", position = 1)
    String APPEARANCE = "appearance";

    @ConfigItem(keyName = "resourcesOnly", name = "Resources only", description = "Apply the categories and extra items below to own drops and enabled GIM group items. Off: include all item types.", section = FILTER, position = 0)
    default boolean resourcesOnly() { return true; }

    @ConfigItem(keyName = "food", name = "Food", description = "Items with an Eat action", section = FILTER, position = 1)
    default boolean food() { return true; }

    @ConfigItem(keyName = "potions", name = "Potions and drinks", description = "Items with a Drink action", section = FILTER, position = 2)
    default boolean potions() { return true; }

    @ConfigItem(keyName = "ammunition", name = "Ammunition", description = "Common arrows, bolts, darts, knives, javelins, chinchompas and cannonballs; add exceptions below", section = FILTER, position = 3)
    default boolean ammunition() { return true; }

    @ConfigItem(keyName = "runes", name = "Runes", description = "Items named rune or runes, including combination runes", section = FILTER, position = 4)
    default boolean runes() { return true; }

    @ConfigItem(keyName = "extraItems", name = "Extra items", description = "Comma-separated exact item names to add in resource mode. No wildcards. Noted items use their unnoted name.", section = FILTER, position = 5)
    default String extraItems() { return ""; }

    @ConfigItem(keyName = "groupItems", name = "Highlight GIM group items", description = "Also mark group-owned ground items, including loot, using the same resource filters. No Party setup or dropper names.", section = FILTER, position = 6)
    default boolean groupItems() { return false; }

    @Alpha
    @ConfigItem(keyName = "groupColor", name = "GIM group color", description = "Color and opacity for group outlines and names. Tile fill also uses Tile fill color opacity. Text background stays shared. Zero opacity hides all group marks.", section = FILTER, position = 7)
    default Color groupColor() { return Color.MAGENTA; }

    @ConfigItem(keyName = "outline", name = "Item outline", description = "Outline the individual matching item", section = APPEARANCE, position = 0)
    default boolean outline() { return true; }

    @Alpha
    @ConfigItem(keyName = "outlineColor", name = "Outline color", description = "Outline color and opacity", section = APPEARANCE, position = 1)
    default Color outlineColor() { return Color.CYAN; }

    @Range(min = 1, max = 5)
    @ConfigItem(keyName = "outlineWidth", name = "Outline width", description = "Outline width in pixels", section = APPEARANCE, position = 2)
    default int outlineWidth() { return 2; }

    @ConfigItem(keyName = "tileFill", name = "Ground tile fill", description = "Fill the ground tile beneath matching items; this colors the tile, not the item model", section = APPEARANCE, position = 3)
    default boolean tileFill() { return false; }

    @Alpha
    @ConfigItem(keyName = "fillColor", name = "Tile fill color", description = "Ground tile color and opacity", section = APPEARANCE, position = 4)
    default Color fillColor() { return new Color(0, 255, 255, 45); }

    @ConfigItem(keyName = "text", name = "Show item names", description = "Show separate labels. Ground Items may also draw labels; use outlines only to avoid duplicates.", section = APPEARANCE, position = 5)
    default boolean text() { return false; }

    @Alpha
    @ConfigItem(keyName = "textColor", name = "Text color", description = "Item-name color and opacity", section = APPEARANCE, position = 6)
    default Color textColor() { return Color.CYAN; }

    @Alpha
    @ConfigItem(keyName = "backgroundColor", name = "Text background", description = "Label background color and opacity; zero opacity hides the background", section = APPEARANCE, position = 7)
    default Color backgroundColor() { return new Color(0, 0, 0, 150); }
}
