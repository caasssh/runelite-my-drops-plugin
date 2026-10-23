package com.mydrops;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
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
        when(plugin.client.getWorldView(view.getId())).thenReturn(view);
        when(plugin.client.getTickCount()).thenReturn(1);
        when(plugin.client.getGameState()).thenReturn(GameState.LOGGED_IN);
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

    private MenuEntry groundMenu()
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(MenuAction.GROUND_ITEM_FIRST_OPTION);
        when(entry.getIdentifier()).thenReturn(100);
        when(entry.getWorldViewId()).thenReturn(7);
        when(entry.getParam0()).thenReturn(10);
        when(entry.getParam1()).thenReturn(20);
        when(entry.getTarget()).thenReturn("<img=1><col=ffffff>Shark");
        WorldView view = tile.getItemLayer().getWorldView();
        when(view.getId()).thenReturn(7);
        when(plugin.client.getWorldView(7)).thenReturn(view);
        when(tile.getSceneLocation()).thenReturn(new Point(10, 20));
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(item));
        return entry;
    }

    private void open(MenuEntry entry)
    {
        MenuOpened opened = new MenuOpened();
        opened.setMenuEntries(new MenuEntry[]{entry});
        plugin.onMenuOpened(opened);
    }

    @Test public void rightClickMenuColorsOnlyCurrentUniqueOwnDrop()
    {
        plugin.config = new MyDropsConfig() {
            @Override public boolean menuNames() { return true; }
            @Override public Color menuNameColor() { return Color.GREEN; }
        };
        drop();
        MenuEntry entry = groundMenu();
        open(entry);
        verify(entry).setTarget("<col=00ff00><img=1>Shark");
        verify(entry, never()).setOption(anyString());
        verify(entry, never()).setType(any());

        clearInvocations(entry);
        TileItem other = mock(TileItem.class); when(other.getId()).thenReturn(100);
        when(tile.getGroundItems()).thenReturn(Arrays.asList(item, other));
        open(entry);
        verify(entry, never()).setTarget(anyString());

        when(tile.getGroundItems()).thenReturn(Collections.singletonList(other));
        open(entry);
        verify(entry, never()).setTarget(anyString());
    }

    @Test public void rightClickMenuRespectsFilterAndSceneIdentity()
    {
        plugin.config = new MyDropsConfig() {
            @Override public boolean menuNames() { return true; }
            @Override public boolean food() { return false; }
        };
        drop();
        MenuEntry entry = groundMenu();
        open(entry);
        verify(entry, never()).setTarget(anyString());

        plugin.config = new MyDropsConfig() { @Override public boolean menuNames() { return true; } };
        when(entry.getWorldViewId()).thenReturn(8);
        open(entry);
        verify(entry, never()).setTarget(anyString());
        when(entry.getWorldViewId()).thenReturn(7);
        WorldView differentView = mock(WorldView.class);
        when(plugin.client.getWorldView(7)).thenReturn(differentView);
        open(entry);
        verify(entry, never()).setTarget(anyString());
        WorldView currentView = tile.getItemLayer().getWorldView();
        when(plugin.client.getWorldView(7)).thenReturn(currentView);
        when(entry.getParam0()).thenReturn(11);
        open(entry);
        verify(entry, never()).setTarget(anyString());

        when(entry.getParam0()).thenReturn(10);
        plugin.onItemDespawned(new ItemDespawned(tile, item));
        open(entry);
        verify(entry, never()).setTarget(anyString());
    }
    @Test public void sceneLoadingClearsEverything() { drop(); GameStateChanged state = new GameStateChanged(); state.setGameState(GameState.LOADING); plugin.onGameStateChanged(state); assertTrue(plugin.labels.isEmpty()); assertTrue(plugin.tracker.tracked().isEmpty()); }
    @Test public void confirmedDropCanRebindAfterSceneLoading()
    {
        when(item.getVisibleTime()).thenReturn(1); when(item.getDespawnTime()).thenReturn(200);
        drop(); GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING);
        plugin.onGameStateChanged(loading);
        TileItem replayed = mock(TileItem.class);
        when(replayed.getId()).thenReturn(100); when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(replayed.getQuantity()).thenReturn(1); when(replayed.getVisibleTime()).thenReturn(1);
        when(replayed.getDespawnTime()).thenReturn(200);
        plugin.onItemSpawned(new ItemSpawned(tile, replayed));
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(replayed));
        assertFalse(plugin.tracker.tracked().containsKey(item));
        assertEquals("Shark", plugin.labels.get(replayed).text);
    }

    @Test public void confirmedDropSurvivesTopLevelUnloadAfterLoading()
    {
        when(item.getVisibleTime()).thenReturn(1); when(item.getDespawnTime()).thenReturn(200);
        WorldView view = tile.getItemLayer().getWorldView();
        when(view.isTopLevel()).thenReturn(true);
        drop();
        GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING);
        plugin.onGameStateChanged(loading);
        plugin.onWorldViewUnloaded(new WorldViewUnloaded(view));
        TileItem replayed = mock(TileItem.class);
        when(replayed.getId()).thenReturn(100); when(replayed.getQuantity()).thenReturn(1);
        when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(replayed.getVisibleTime()).thenReturn(1); when(replayed.getDespawnTime()).thenReturn(200);
        plugin.onItemSpawned(new ItemSpawned(tile, replayed));
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(replayed));
    }

    @Test public void confirmedDropSurvivesTopLevelUnloadBeforeLoading()
    {
        when(item.getVisibleTime()).thenReturn(1); when(item.getDespawnTime()).thenReturn(200);
        WorldView view = tile.getItemLayer().getWorldView();
        when(view.isTopLevel()).thenReturn(true);
        drop();
        when(plugin.client.getGameState()).thenReturn(GameState.LOADING);
        plugin.onWorldViewUnloaded(new WorldViewUnloaded(view));
        GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING);
        plugin.onGameStateChanged(loading);
        TileItem replayed = mock(TileItem.class);
        when(replayed.getId()).thenReturn(100); when(replayed.getQuantity()).thenReturn(1);
        when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(replayed.getVisibleTime()).thenReturn(1); when(replayed.getDespawnTime()).thenReturn(200);
        plugin.onItemSpawned(new ItemSpawned(tile, replayed));
        when(plugin.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(replayed));
    }

    @Test public void confirmedDropSurvivesLoggedInTopLevelUnloadBeforeLoading()
    {
        when(item.getVisibleTime()).thenReturn(1); when(item.getDespawnTime()).thenReturn(200);
        WorldView view = tile.getItemLayer().getWorldView();
        when(view.isTopLevel()).thenReturn(true);
        drop();
        plugin.onWorldViewUnloaded(new WorldViewUnloaded(view));
        assertEquals(1, plugin.reload.savedCount());
        GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING);
        plugin.onGameStateChanged(loading);
        for (int tick = 1; tick <= 4; tick++)
        {
            when(plugin.client.getTickCount()).thenReturn(tick);
            plugin.onGameTick(new GameTick());
        }
        assertTrue(plugin.tracker.tracked().isEmpty());
        TileItem replayed = mock(TileItem.class);
        when(replayed.getId()).thenReturn(100); when(replayed.getQuantity()).thenReturn(1);
        when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(replayed.getVisibleTime()).thenReturn(1); when(replayed.getDespawnTime()).thenReturn(200);
        when(plugin.client.getTickCount()).thenReturn(5);
        plugin.onItemSpawned(new ItemSpawned(tile, replayed));
        plugin.onGameTick(new GameTick());
        when(plugin.client.getTickCount()).thenReturn(6);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(replayed));
    }

    @Test public void confirmedDropReturnsAfterLoggedInUnloadWithoutLoadingState()
    {
        when(item.getVisibleTime()).thenReturn(1); when(item.getDespawnTime()).thenReturn(200);
        WorldView view = tile.getItemLayer().getWorldView();
        when(view.isTopLevel()).thenReturn(true);
        drop();
        plugin.onWorldViewUnloaded(new WorldViewUnloaded(view));
        for (int tick = 1; tick <= 4; tick++)
        {
            when(plugin.client.getTickCount()).thenReturn(tick);
            plugin.onGameTick(new GameTick());
        }
        TileItem replayed = mock(TileItem.class);
        when(replayed.getId()).thenReturn(100); when(replayed.getQuantity()).thenReturn(1);
        when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(replayed.getVisibleTime()).thenReturn(1); when(replayed.getDespawnTime()).thenReturn(200);
        when(plugin.client.getTickCount()).thenReturn(5);
        plugin.onItemSpawned(new ItemSpawned(tile, replayed));
        plugin.onGameTick(new GameTick());
        when(plugin.client.getTickCount()).thenReturn(6);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(replayed));
    }

    @Test public void confirmedDropCanReturnAsPublicWithoutLoading()
    {
        when(item.getVisibleTime()).thenReturn(11); when(item.getDespawnTime()).thenReturn(200);
        drop();
        when(plugin.client.getTickCount()).thenReturn(12);
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        plugin.onItemDespawned(new ItemDespawned(tile, item));
        TileItem publicItem = mock(TileItem.class);
        when(publicItem.getId()).thenReturn(100); when(publicItem.getQuantity()).thenReturn(1);
        when(publicItem.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        when(publicItem.getVisibleTime()).thenReturn(11); when(publicItem.getDespawnTime()).thenReturn(200);
        plugin.onItemSpawned(new ItemSpawned(tile, publicItem));
        plugin.onGameTick(new GameTick());
        when(plugin.client.getTickCount()).thenReturn(13);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(publicItem));
    }

    @Test public void confirmedDropSurvivesObservedOtherOwnershipHandoff()
    {
        when(item.getVisibleTime()).thenReturn(11); when(item.getDespawnTime()).thenReturn(200);
        WorldView view = tile.getItemLayer().getWorldView();
        when(plugin.client.getWorldView(view.getId())).thenReturn(view);
        drop();
        when(plugin.client.getTickCount()).thenReturn(20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        plugin.onItemDespawned(new ItemDespawned(tile, item));
        TileItem changed = mock(TileItem.class);
        when(changed.getId()).thenReturn(100); when(changed.getQuantity()).thenReturn(1);
        when(changed.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(changed.getVisibleTime()).thenReturn(38); when(changed.getDespawnTime()).thenReturn(200);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(changed));
        plugin.onItemSpawned(new ItemSpawned(tile, changed));
        plugin.onGameTick(new GameTick());
        when(plugin.client.getTickCount()).thenReturn(21);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(changed));
        assertEquals("Shark", plugin.labels.get(changed).text);
    }

    @Test public void shiftedDropSurvivesOwnershipChangeDuringSceneLoad()
    {
        when(item.getVisibleTime()).thenReturn(11); when(item.getDespawnTime()).thenReturn(200);
        WorldView oldView = tile.getItemLayer().getWorldView();
        when(oldView.getId()).thenReturn(7);
        when(oldView.isTopLevel()).thenReturn(true);
        when(plugin.client.getWorldView(7)).thenReturn(oldView);
        drop();
        GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING);
        plugin.onGameStateChanged(loading);
        TileItem shifted = mock(TileItem.class);
        when(shifted.getId()).thenReturn(100); when(shifted.getQuantity()).thenReturn(1);
        when(shifted.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(shifted.getVisibleTime()).thenReturn(10); when(shifted.getDespawnTime()).thenReturn(199);
        when(plugin.client.getTickCount()).thenReturn(20);
        plugin.onItemSpawned(new ItemSpawned(tile, shifted));
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.tracker.tracked().containsKey(shifted));

        when(tile.getGroundItems()).thenReturn(Collections.singletonList(shifted));
        when(plugin.client.getTickCount()).thenReturn(31);
        plugin.onGameTick(new GameTick());
        when(plugin.client.getTickCount()).thenReturn(32);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        plugin.onItemDespawned(new ItemDespawned(tile, shifted));
        assertEquals(1, plugin.reload.savedCount());
        plugin.onGameStateChanged(loading);
        assertEquals(1, plugin.reload.savedCount());
        plugin.onWorldViewUnloaded(new WorldViewUnloaded(oldView));
        assertEquals(1, plugin.reload.savedCount());

        WorldView nextView = mock(WorldView.class); when(nextView.getId()).thenReturn(7);
        when(plugin.client.getWorldView(7)).thenReturn(nextView);
        Tile nextTile = mock(Tile.class); ItemLayer nextLayer = mock(ItemLayer.class);
        when(nextTile.getItemLayer()).thenReturn(nextLayer); when(nextLayer.getWorldView()).thenReturn(nextView);
        when(nextTile.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        TileItem changed = mock(TileItem.class);
        when(changed.getId()).thenReturn(100); when(changed.getQuantity()).thenReturn(1);
        when(changed.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(changed.getVisibleTime()).thenReturn(41); when(changed.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(changed));
        when(plugin.client.getTickCount()).thenReturn(33);
        plugin.onItemSpawned(new ItemSpawned(nextTile, changed));
        for (int tick = 33; tick <= 36; tick++)
        {
            when(plugin.client.getTickCount()).thenReturn(tick);
            plugin.onGameTick(new GameTick());
        }
        assertTrue(plugin.tracker.tracked().containsKey(changed));
    }

    @Test public void inactiveRemovalAndPreloadOtherWitnessRestoreOnlyFreshSceneItem()
    {
        when(item.getVisibleTime()).thenReturn(11); when(item.getDespawnTime()).thenReturn(200);
        WorldView oldView = tile.getItemLayer().getWorldView();
        when(oldView.getId()).thenReturn(7); when(oldView.isTopLevel()).thenReturn(true);
        when(plugin.client.getWorldView(7)).thenReturn(oldView);
        drop();
        GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING);
        plugin.onGameStateChanged(loading);
        TileItem shifted = mock(TileItem.class);
        when(shifted.getId()).thenReturn(100); when(shifted.getQuantity()).thenReturn(1);
        when(shifted.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(shifted.getVisibleTime()).thenReturn(10); when(shifted.getDespawnTime()).thenReturn(199);
        when(plugin.client.getTickCount()).thenReturn(20);
        plugin.onItemSpawned(new ItemSpawned(tile, shifted));
        plugin.onGameTick(new GameTick());
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(shifted));
        when(plugin.client.getTickCount()).thenReturn(31);
        plugin.onGameTick(new GameTick());

        Tile dyingTile = mock(Tile.class);
        when(plugin.client.getTickCount()).thenReturn(32);
        plugin.onItemDespawned(new ItemDespawned(dyingTile, shifted));
        assertEquals(1, plugin.reload.savedCount());
        TileItem beforeLoad = mock(TileItem.class);
        when(beforeLoad.getId()).thenReturn(100); when(beforeLoad.getQuantity()).thenReturn(1);
        when(beforeLoad.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(beforeLoad.getVisibleTime()).thenReturn(28); when(beforeLoad.getDespawnTime()).thenReturn(200);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(beforeLoad));
        plugin.onItemSpawned(new ItemSpawned(tile, beforeLoad));
        assertFalse(plugin.tracker.tracked().containsKey(beforeLoad));
        plugin.onGameStateChanged(loading);
        plugin.onWorldViewUnloaded(new WorldViewUnloaded(oldView));
        assertEquals(1, plugin.reload.savedCount());

        WorldView nextView = mock(WorldView.class); when(nextView.getId()).thenReturn(7);
        when(plugin.client.getWorldView(7)).thenReturn(nextView);
        Tile nextTile = mock(Tile.class); ItemLayer nextLayer = mock(ItemLayer.class);
        when(nextTile.getItemLayer()).thenReturn(nextLayer); when(nextLayer.getWorldView()).thenReturn(nextView);
        when(nextTile.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        TileItem afterLoad = mock(TileItem.class);
        when(afterLoad.getId()).thenReturn(100); when(afterLoad.getQuantity()).thenReturn(1);
        when(afterLoad.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(afterLoad.getVisibleTime()).thenReturn(27); when(afterLoad.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(afterLoad));
        when(plugin.client.getTickCount()).thenReturn(33);
        plugin.onItemSpawned(new ItemSpawned(nextTile, afterLoad));
        for (int tick = 33; tick <= 36; tick++)
        {
            when(plugin.client.getTickCount()).thenReturn(tick);
            plugin.onGameTick(new GameTick());
        }
        assertTrue(plugin.tracker.tracked().containsKey(afterLoad));
        assertFalse(plugin.tracker.tracked().containsKey(beforeLoad));
    }
    @Test public void newDropAfterRepeatedLoadingStillHighlights()
    {
        GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING);
        GameStateChanged loggedIn = new GameStateChanged(); loggedIn.setGameState(GameState.LOGGED_IN);
        for (int i = 0; i < 2; i++)
        {
            plugin.onGameStateChanged(loading); plugin.onGameStateChanged(loggedIn);
            when(inventory.count(100)).thenReturn(5);
            when(plugin.client.getTickCount()).thenReturn(i + 2);
            TileItem freshItem = mock(TileItem.class);
            when(freshItem.getId()).thenReturn(100); when(freshItem.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
            when(freshItem.getQuantity()).thenReturn(1);
            plugin.onMenuOptionClicked(click); plugin.onItemSpawned(new ItemSpawned(tile, freshItem));
            when(inventory.count(100)).thenReturn(4); plugin.onGameTick(new GameTick());
            assertTrue(plugin.tracker.tracked().containsKey(freshItem));
        }
    }
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
