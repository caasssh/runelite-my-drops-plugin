package com.mydrops;

import net.runelite.api.ItemComposition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ResourceFilterTest
{
    private int classify(String name, String... actions)
    {
        ItemComposition item = mock(ItemComposition.class); when(item.getName()).thenReturn(name); when(item.getInventoryActions()).thenReturn(actions); return ResourceFilter.classify(item);
    }
    @Test public void foodUsesAction() { assertEquals(ResourceFilter.FOOD, classify("Shark", "Eat", null, "Drop")); }
    @Test public void drinksUseAction() { assertEquals(ResourceFilter.POTIONS, classify("Saradomin brew(4)", "Drink", "Drop")); }
    @Test public void ammoVariants()
    {
        for (String name : new String[]{"Rune arrow(p++)", "Dragon bolts (e)", "Bronze dart", "Rune knife(p)", "Dragon javelin", "Red chinchompa", "Granite cannonball", "Toktz-xil-ul"}) { assertEquals(name, ResourceFilter.AMMO, classify(name)); }
    }
    @Test public void utilityKnivesAreNotAmmo() { for (String name : new String[]{"Knife", "Kitchen knife", "Bone dagger", "Arrow shaft"}) { assertEquals(name, 0, classify(name)); } }
    @Test public void runeCategories() { assertEquals(ResourceFilter.RUNES, classify("Law rune")); assertEquals(ResourceFilter.RUNES, classify("Mist rune")); }
    @Test public void masksAndAllItemsMode()
    {
        ResourceFilter filter = new ResourceFilter(); assertTrue(filter.matches("shark", ResourceFilter.FOOD, true, ResourceFilter.FOOD));
        assertFalse(filter.matches("shark", ResourceFilter.FOOD, true, ResourceFilter.POTIONS)); assertTrue(filter.matches("logs", 0, false, 0));
    }
    @Test public void extrasAreExactTrimmedCaseInsensitiveAndReplaceable()
    {
        ResourceFilter filter = new ResourceFilter(); filter.setExtras(" Logs,  pure Essence, ,logs ");
        assertTrue(filter.matches("logs", 0, true, 0)); assertTrue(filter.matches("pure essence", 0, true, 0)); assertFalse(filter.matches("magic logs", 0, true, 0));
        filter.setExtras(""); assertFalse(filter.matches("logs", 0, true, 0));
    }
}
