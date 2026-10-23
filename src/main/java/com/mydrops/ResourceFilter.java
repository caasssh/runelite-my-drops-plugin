package com.mydrops;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.ItemComposition;

final class ResourceFilter
{
    static final int FOOD = 1, POTIONS = 2, AMMO = 4, RUNES = 8;
    private volatile Set<String> extras = new HashSet<>();

    void setExtras(String csv)
    {
        Set<String> parsed = new HashSet<>();
        for (String entry : csv.split(","))
        {
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) { parsed.add(name); }
        }
        extras = parsed;
    }

    boolean matches(String normalizedName, int categories, boolean resourcesOnly, int mask)
    {
        return !resourcesOnly || extras.contains(normalizedName) || (categories & mask) != 0;
    }

    static int classify(ItemComposition item)
    {
        int result = 0;
        String[] actions = item.getInventoryActions();
        if (actions != null)
        {
            for (String action : actions)
            {
                if ("Eat".equalsIgnoreCase(action)) { result |= FOOD; }
                if ("Drink".equalsIgnoreCase(action)) { result |= POTIONS; }
            }
        }
        // Name matching is a small, explicit approximation, evaluated once per confirmed drop.
        String name = item.getName().toLowerCase(Locale.ROOT).replaceAll("\\([^)]*\\)", "").trim();
        for (String ending : new String[]{"arrow", "arrows", "bolt", "bolts", "dart", "darts", "javelin", "javelins", "chinchompa", "chinchompas", "cannonball", "cannonballs"})
        {
            if (name.equals(ending) || name.endsWith(" " + ending)) { result |= AMMO; }
        }
        if (name.matches("(bronze|iron|steel|black|mithril|adamant|rune|dragon) (knife|knives)")) { result |= AMMO; }
        if (name.equals("toktz-xil-ul")) { result |= AMMO; }
        if (name.endsWith(" rune") || name.endsWith(" runes")) { result |= RUNES; }
        return result;
    }
}
