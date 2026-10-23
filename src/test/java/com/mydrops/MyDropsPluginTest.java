package com.mydrops;

import java.awt.Color;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MyDropsPluginTest
{
    private MyDropsPlugin plugin;
    private ItemContainer inventory;
    private Tile tile;
    private TileItem item;
    private MenuOptionClicked click;

    @Before public void setup()
    {
        plugin = new MyDropsPlugin(); plugin.client = mock(Client.class); plugin.itemManager = mock(ItemManager.class);
        plugin.config = new MyDropsConfig() {}; plugin.overlays = mock(OverlayManager.class);
        Player player = mock(Player.class); WorldView view = mock(WorldView.class); WorldPoint point = new WorldPoint(3200, 3200, 0);
        when(plugin.client.getLocalPlayer()).thenReturn(player); when(player.getWorldView()).thenReturn(view); when(player.getWorldLocation()).thenReturn(point);
        when(plugin.client.getTickCount()).thenReturn(1);
        inventory = mock(ItemContainer.class); when(plugin.client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
        when(inventory.count(100)).thenReturn(5); when(inventory.getItem(0)).thenReturn(new Item(100, 1));
        MenuEntry entry = mock(MenuEntry.class); when(entry.isItemOp()).thenReturn(true); when(entry.getOption()).thenReturn("Drop");
        when(entry.getParam1()).thenReturn(InterfaceID.INVENTORY << 16); when(entry.getItemId()).thenReturn(100); click = new MenuOptionClicked(entry);
        tile = mock(Tile.class); ItemLayer layer = mock(ItemLayer.class); when(tile.getItemLayer()).thenReturn(layer); when(layer.getWorldView()).thenReturn(view); when(tile.getWorldLocation()).thenReturn(point);
        item = mock(TileItem.class); when(item.getId()).thenReturn(100); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF); when(item.getQuantity()).thenReturn(1);
        ItemComposition definition = mock(ItemComposition.class); when(definition.getNote()).thenReturn(-1); when(definition.getName()).thenReturn("Shark"); when(definition.getInventoryActions()).thenReturn(new String[]{"Eat", "Drop"});
        when(plugin.itemManager.getItemComposition(100)).thenReturn(definition);
    }
    private void drop()
    {
        plugin.onMenuOptionClicked(click); plugin.onItemSpawned(new ItemSpawned(tile, item));
        when(inventory.count(100)).thenReturn(4); plugin.onGameTick(new GameTick());
    }
    @Test public void integratesDropToCachedLabel()
    {
        drop(); assertEquals(1, plugin.labels.size()); assertEquals("Shark", plugin.labels.get(item).text);
        assertEquals(ResourceFilter.FOOD, plugin.labels.get(item).categories);
        plugin.onGameTick(new GameTick()); verify(plugin.itemManager, times(1)).getItemComposition(100);
    }
    @Test public void consumedMenuDoesNotTrack() { click.consume(); drop(); assertTrue(plugin.labels.isEmpty()); }
    @Test public void rejectsNonInventoryWidget() { when(click.getMenuEntry().getParam1()).thenReturn(12 << 16); drop(); assertTrue(plugin.labels.isEmpty()); }
    @Test public void rejectsMismatchedSlot() { when(inventory.getItem(0)).thenReturn(new Item(101, 1)); drop(); assertTrue(plugin.labels.isEmpty()); }
    @Test public void rejectsNonDropAction() { when(click.getMenuEntry().getOption()).thenReturn("Eat"); drop(); assertTrue(plugin.labels.isEmpty()); }
    @Test public void pickupClearsLabelAndTracking() { drop(); plugin.onItemDespawned(new ItemDespawned(tile, item)); assertTrue(plugin.labels.isEmpty()); assertTrue(plugin.tracker.tracked().isEmpty()); }
    @Test public void sceneLoadingClearsEverything() { drop(); GameStateChanged state = new GameStateChanged(); state.setGameState(GameState.LOADING); plugin.onGameStateChanged(state); assertTrue(plugin.labels.isEmpty()); assertTrue(plugin.tracker.tracked().isEmpty()); }
    @Test public void shutdownClearsEverything() { drop(); plugin.shutDown(); assertTrue(plugin.labels.isEmpty()); assertTrue(plugin.tracker.tracked().isEmpty()); }
    @Test public void notedResourceUsesUnnotedCategory()
    {
        ItemComposition noted = mock(ItemComposition.class), base = mock(ItemComposition.class);
        when(noted.getName()).thenReturn("Shark"); when(noted.getNote()).thenReturn(799); when(noted.getLinkedNoteId()).thenReturn(101);
        when(base.getName()).thenReturn("Shark"); when(base.getInventoryActions()).thenReturn(new String[]{"Eat"});
        when(plugin.itemManager.getItemComposition(100)).thenReturn(noted); when(plugin.itemManager.getItemComposition(101)).thenReturn(base);
        drop(); assertEquals(ResourceFilter.FOOD, plugin.labels.get(item).categories);
    }
    @Test public void defaultsSupportCoreGroundItems()
    {
        assertTrue(plugin.config.outline()); assertFalse(plugin.config.text()); assertFalse(plugin.config.tileFill());
        assertEquals(Color.CYAN, plugin.config.outlineColor()); assertEquals(45, plugin.config.fillColor().getAlpha());
        assertFalse(plugin.config.groupItems()); assertEquals(Color.MAGENTA, plugin.config.groupColor());
    }
    private void groupSpawn()
    {
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP); plugin.onItemSpawned(new ItemSpawned(tile, item));
    }
    @Test public void groupItemNeedsNoDropIntentOrInventoryLoss()
    {
        groupSpawn(); assertSame(tile, plugin.groupItems.get(item)); assertEquals(ResourceFilter.FOOD, plugin.labels.get(item).categories); assertTrue(plugin.tracker.tracked().isEmpty());
    }
    @Test public void groupCandidatesAreObservedWhileToggleOff()
    {
        assertFalse(plugin.config.groupItems()); groupSpawn(); assertEquals(1, plugin.groupItems.size());
    }
    @Test public void selfAndOtherItemsAreNotGroupCandidates()
    {
        plugin.onItemSpawned(new ItemSpawned(tile, item)); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        plugin.onItemSpawned(new ItemSpawned(tile, item)); assertTrue(plugin.groupItems.isEmpty());
    }
    @Test public void groupPickupRemovesCandidateAndLabel()
    {
        groupSpawn(); plugin.onItemDespawned(new ItemDespawned(tile, item)); assertTrue(plugin.groupItems.isEmpty()); assertTrue(plugin.labels.isEmpty());
    }
    @Test public void loadingClearsGroupCandidates()
    {
        groupSpawn(); GameStateChanged state = new GameStateChanged(); state.setGameState(GameState.LOADING); plugin.onGameStateChanged(state); assertTrue(plugin.groupItems.isEmpty()); assertTrue(plugin.labels.isEmpty());
    }
    @Test public void shutdownClearsGroupCandidates()
    {
        groupSpawn(); plugin.shutDown(); assertTrue(plugin.groupItems.isEmpty()); assertTrue(plugin.labels.isEmpty());
    }
    @Test public void groupCandidateCapEvictsOldest()
    {
        groupSpawn();
        for (int i = 0; i < DropTracker.MAX_TRACKED; i++)
        {
            TileItem next = mock(TileItem.class); when(next.getId()).thenReturn(100); when(next.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP); when(next.getQuantity()).thenReturn(1);
            plugin.onItemSpawned(new ItemSpawned(tile, next));
        }
        assertEquals(DropTracker.MAX_TRACKED, plugin.groupItems.size()); assertFalse(plugin.groupItems.containsKey(item)); assertFalse(plugin.labels.containsKey(item)); assertEquals(DropTracker.MAX_TRACKED, plugin.labels.size());
    }
    @Test public void worldViewUnloadRemovesOnlyItsGroupCandidates()
    {
        groupSpawn(); plugin.onWorldViewUnloaded(new WorldViewUnloaded(mock(WorldView.class))); assertEquals(1, plugin.groupItems.size()); assertEquals(1, plugin.labels.size());
        plugin.onWorldViewUnloaded(new WorldViewUnloaded(tile.getItemLayer().getWorldView())); assertTrue(plugin.groupItems.isEmpty()); assertTrue(plugin.labels.isEmpty());
    }
    @Test public void groupQuantityChangeUpdatesCachedLabelWithoutDefinitionLookup()
    {
        groupSpawn(); when(item.getQuantity()).thenReturn(5); plugin.onItemQuantityChanged(new ItemQuantityChanged(item, tile, 1, 5));
        assertEquals("Shark (5)", plugin.labels.get(item).text); verify(plugin.itemManager, times(1)).getItemComposition(100);
    }
    @Test public void quantityChangeCanObserveNewGroupOwnership()
    {
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP); when(item.getQuantity()).thenReturn(5);
        plugin.onItemQuantityChanged(new ItemQuantityChanged(item, tile, 1, 5)); assertEquals(1, plugin.groupItems.size()); assertEquals("Shark (5)", plugin.labels.get(item).text);
    }
    @Test public void groupEvictionPreservesOwnDropLabel()
    {
        drop(); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP); plugin.onItemSpawned(new ItemSpawned(tile, item));
        for (int i = 0; i < DropTracker.MAX_TRACKED; i++)
        {
            TileItem next = mock(TileItem.class); when(next.getId()).thenReturn(100); when(next.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP); when(next.getQuantity()).thenReturn(1);
            plugin.onItemSpawned(new ItemSpawned(tile, next));
        }
        assertFalse(plugin.groupItems.containsKey(item)); assertTrue(plugin.tracker.tracked().containsKey(item)); assertNotNull(plugin.labels.get(item));
    }
}
