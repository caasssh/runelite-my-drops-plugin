package com.mydrops;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DropTrackerTest
{
    private DropTracker tracker;
    private WorldView view;
    private Tile tile;
    private TileItem item;
    private final WorldPoint point = new WorldPoint(3200, 3200, 0);

    @Before public void setup()
    {
        tracker = new DropTracker(); view = mock(WorldView.class); tile = tile(view, point); item = item(100, TileItem.OWNERSHIP_SELF);
    }
    private Tile tile(WorldView world, WorldPoint location)
    {
        Tile result = mock(Tile.class); ItemLayer layer = mock(ItemLayer.class);
        when(result.getWorldLocation()).thenReturn(location); when(result.getItemLayer()).thenReturn(layer); when(layer.getWorldView()).thenReturn(world);
        return result;
    }
    private TileItem item(int id, int owner)
    {
        TileItem result = mock(TileItem.class); when(result.getId()).thenReturn(id); when(result.getOwnership()).thenReturn(owner); return result;
    }
    private void intent(int count, int quantity, int tick) { tracker.intent(100, count, quantity, tick, view, point); }
    private void ground(int amount, int tick) { tracker.ground(item, tile, amount, tick, point); }

    @Test public void needsIntent() { ground(1, 1); tracker.confirm(1, id -> 0); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void needsGroundChange() { intent(5, 1, 1); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void needsInventoryLoss() { intent(5, 1, 1); ground(1, 1); tracker.confirm(1, id -> 5); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void confirmsDrop() { intent(5, 1, 1); ground(1, 1); tracker.confirm(1, id -> 4); assertSame(tile, tracker.tracked().get(item)); }
    @Test public void inventoryCanUpdateBeforeGroundEvent() { intent(5, 1, 1); int remaining = 4; ground(1, 1); tracker.confirm(1, id -> remaining); assertEquals(1, tracker.tracked().size()); }
    @Test public void rejectsOtherOwners()
    {
        for (int owner : new int[]{TileItem.OWNERSHIP_NONE, TileItem.OWNERSHIP_OTHER, TileItem.OWNERSHIP_GROUP})
        {
            tracker.clear(); intent(5, 1, 1); tracker.ground(item(100, owner), tile, 1, 1, point); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty());
        }
    }
    @Test public void rejectsOtherWorldView() { intent(5, 1, 1); tracker.ground(item, tile(mock(WorldView.class), point), 1, 1, point); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void rejectsDistantTile() { intent(5, 1, 1); tracker.ground(item, tile(view, new WorldPoint(3210, 3200, 0)), 1, 1, point); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void acceptsCurrentPlayerTileAfterMovement()
    {
        WorldPoint moved = new WorldPoint(3201, 3200, 0); intent(5, 1, 1); tracker.ground(item, tile(view, moved), 1, 1, moved); tracker.confirm(1, id -> 4); assertEquals(1, tracker.tracked().size());
    }
    @Test public void rejectsWrongId() { intent(5, 1, 1); tracker.ground(item(101, TileItem.OWNERSHIP_SELF), tile, 1, 1, point); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void expiresWithoutRevival() { intent(5, 1, 1); ground(1, 1); tracker.confirm(5, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void newClicksDoNotRenewOldCandidates() { intent(5, 1, 1); ground(1, 1); intent(5, 1, 4); tracker.confirm(5, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void handlesRapidSameItemDrops()
    {
        intent(5, 1, 1); intent(5, 1, 1); ground(1, 1); TileItem second = item(100, TileItem.OWNERSHIP_SELF);
        tracker.ground(second, tile, 1, 1, point); tracker.confirm(1, id -> 3); assertEquals(2, tracker.tracked().size());
    }
    @Test public void rejectsAmbiguousLootAndDrop()
    {
        intent(5, 1, 1); ground(1, 1); tracker.confirm(1, id -> 5);
        intent(5, 1, 2); tracker.ground(item(100, TileItem.OWNERSHIP_SELF), tile, 1, 2, point);
        tracker.confirm(2, id -> 4); assertTrue(tracker.tracked().isEmpty());
    }
    @Test public void cannotSpendMoreThanClickedQuantity() { intent(10, 1, 1); ground(2, 1); tracker.confirm(1, id -> 8); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void quantityIncreaseMatchesDeltaNotStackTotal() { intent(50, 50, 1); when(item.getQuantity()).thenReturn(150); ground(50, 1); tracker.confirm(1, id -> 0); assertEquals(1, tracker.tracked().size()); }
    @Test public void ignoresQuantityDecrease() { intent(5, 1, 1); ground(-1, 1); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void despawnBeforeConfirmationCancelsCandidate() { intent(5, 1, 1); ground(1, 1); tracker.remove(item); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void despawnRemovesConfirmedItem() { confirmsDrop(); tracker.remove(item); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void consumedIntentCannotMarkLaterLoot() { confirmsDrop(); tracker.remove(item); ground(1, 2); tracker.confirm(2, id -> 3); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void nonDropActionCancelsIntent() { intent(5, 1, 1); ground(1, 1); tracker.cancel(100); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void resetClearsPendingAndConfirmed() { confirmsDrop(); intent(5, 1, 1); tracker.clear(); ground(1, 1); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void worldViewUnloadClearsPendingAndConfirmed() { confirmsDrop(); intent(5, 1, 1); tracker.unload(view); ground(1, 1); tracker.confirm(1, id -> 4); assertTrue(tracker.tracked().isEmpty()); }
    @Test public void trackingStaysBounded()
    {
        for (int i = 0; i < 300; i++)
        {
            tracker.intent(i, 1, 1, i, view, point); tracker.ground(item(i, TileItem.OWNERSHIP_SELF), tile, 1, i, point); tracker.confirm(i, id -> 0);
        }
        assertEquals(DropTracker.MAX_TRACKED, tracker.tracked().size());
    }
    @Test public void candidateOverflowFailsClosed()
    {
        intent(100, 100, 1);
        for (int i = 0; i < 29; i++) { tracker.ground(item(100, TileItem.OWNERSHIP_SELF), tile, 1, 1, point); }
        tracker.confirm(1, id -> 72); assertTrue(tracker.tracked().isEmpty());
    }
}
