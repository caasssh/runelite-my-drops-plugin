package com.mydrops;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemLayer;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReloadedDropsTest
{
    private ReloadedDrops reload;
    private WorldView view;
    private Tile tile;
    private TileItem original;
    private Map<TileItem, Tile> confirmed;

    @Before public void setup()
    {
        reload = new ReloadedDrops(); view = mock(WorldView.class); when(view.getId()).thenReturn(7);
        tile = mock(Tile.class); ItemLayer layer = mock(ItemLayer.class);
        when(tile.getItemLayer()).thenReturn(layer); when(layer.getWorldView()).thenReturn(view);
        when(tile.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        original = item(); confirmed = new IdentityHashMap<>(); confirmed.put(original, tile);
    }

    private TileItem item()
    {
        TileItem result = mock(TileItem.class);
        when(result.getId()).thenReturn(100); when(result.getQuantity()).thenReturn(1);
        when(result.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        when(result.getVisibleTime()).thenReturn(11); when(result.getDespawnTime()).thenReturn(200);
        return result;
    }

    @Test public void restoresOnlyPreviouslyConfirmedExactReplay()
    {
        reload.loading(confirmed, 330, 20);
        TileItem replayed = item(); reload.spawned(replayed, tile, 330, 20);
        assertSame(replayed, reload.finish(330, 20).get(0).item);
        reload.loading(new IdentityHashMap<>(), 330, 21);
        reload.spawned(item(), tile, 330, 21);
        assertEquals(1, reload.finish(330, 21).size());
    }

    @Test public void restoresAfterLeavingAndReturningAcrossTwoLoadingLines()
    {
        reload.loading(confirmed, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        reload.loading(new IdentityHashMap<>(), 330, 25);
        TileItem replayed = item(); reload.spawned(replayed, tile, 330, 25);
        assertSame(replayed, reload.finish(330, 25).get(0).item);
    }

    @Test public void ignoresUnconfirmedSelfOwnedReplay()
    {
        reload.loading(new IdentityHashMap<>(), 330, 20);
        reload.spawned(item(), tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
    }

    @Test public void rejectsDuplicateOldAndNewFingerprints()
    {
        confirmed.put(item(), tile); reload.loading(confirmed, 330, 20);
        reload.spawned(item(), tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        reload.clear(); confirmed.clear(); confirmed.put(original, tile);
        reload.loading(confirmed, 330, 20);
        reload.spawned(item(), tile, 330, 20); reload.spawned(item(), tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        reload.loading(new IdentityHashMap<>(), 330, 21);
        reload.spawned(item(), tile, 330, 21);
        assertTrue(reload.finish(330, 21).isEmpty());
    }

    @Test public void changedQuantityOrLifetimeFailsClosed()
    {
        reload.loading(confirmed, 330, 20);
        TileItem changed = item(); when(changed.getQuantity()).thenReturn(2);
        reload.spawned(changed, tile, 330, 20); assertTrue(reload.finish(330, 20).isEmpty());
        reload.loading(new IdentityHashMap<>(), 330, 21);
        changed = item(); when(changed.getDespawnTime()).thenReturn(201);
        reload.spawned(changed, tile, 330, 21); assertTrue(reload.finish(330, 21).isEmpty());
    }

    @Test public void ownershipChangeBeforeReplayFinishesFailsClosed()
    {
        reload.loading(confirmed, 330, 20);
        TileItem replayed = item(); reload.spawned(replayed, tile, 330, 20);
        when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        assertTrue(reload.finish(330, 20).isEmpty());
    }

    @Test public void expiresAndRejectsUnknownLifetimes()
    {
        reload.loading(confirmed, 330, 20);
        reload.loading(new IdentityHashMap<>(), 330, 200);
        reload.spawned(item(), tile, 330, 200); assertTrue(reload.finish(330, 200).isEmpty());
        when(original.getDespawnTime()).thenReturn(0);
        reload.loading(confirmed, 330, 20);
        reload.spawned(item(), tile, 330, 20); assertTrue(reload.finish(330, 20).isEmpty());
    }

    @Test public void savedFingerprintExpiresWithoutAnotherLoadingLine()
    {
        reload.loading(confirmed, 330, 20);
        assertEquals(1, reload.savedCount());
        reload.finish(330, 20);
        reload.expire(199); assertEquals(1, reload.savedCount());
        reload.expire(200); assertEquals(0, reload.savedCount());
    }

    @Test public void hoppingOrExplicitClearRejectsOldReplay()
    {
        reload.loading(confirmed, 330, 20);
        reload.loading(new IdentityHashMap<>(), 331, 21);
        reload.spawned(item(), tile, 331, 21); assertTrue(reload.finish(331, 21).isEmpty());
        reload.loading(confirmed, 330, 22); reload.clear();
        reload.spawned(item(), tile, 330, 22); assertTrue(reload.finish(330, 22).isEmpty());
    }

    @Test public void unloadedOrDespawnedCandidateIsNotRestored()
    {
        reload.loading(confirmed, 330, 20);
        reload.spawned(item(), tile, 330, 20);
        reload.unloaded(view); assertTrue(reload.finish(330, 20).isEmpty());
        reload.loading(confirmed, 330, 21);
        TileItem replayed = item(); reload.spawned(replayed, tile, 330, 21);
        reload.despawned(replayed, tile, 330, 21);
        assertTrue(reload.finish(330, 21).isEmpty());
    }

    @Test public void unloadedViewCannotRestoreInNewViewWithSameNumericId()
    {
        reload.loading(confirmed, 330, 20);
        reload.unloaded(view);
        WorldView nextView = mock(WorldView.class); when(nextView.getId()).thenReturn(7);
        Tile nextTile = mock(Tile.class); ItemLayer nextLayer = mock(ItemLayer.class);
        when(nextTile.getItemLayer()).thenReturn(nextLayer); when(nextLayer.getWorldView()).thenReturn(nextView);
        WorldPoint location = tile.getWorldLocation();
        when(nextTile.getWorldLocation()).thenReturn(location);
        reload.loading(new IdentityHashMap<>(), 330, 21);
        reload.spawned(item(), nextTile, 330, 21);
        assertTrue(reload.finish(330, 21).isEmpty());
    }

    @Test public void oldSceneDespawnDuringLoadingDoesNotErasePriorConfirmation()
    {
        reload.loading(confirmed, 330, 20);
        reload.despawned(original, tile, 330, 20);
        TileItem replayed = item(); reload.spawned(replayed, tile, 330, 20);
        assertSame(replayed, reload.finish(330, 20).get(0).item);
    }

    @Test public void removingOldQuantityPreventsStaleMatch()
    {
        reload.loading(confirmed, 330, 20);
        TileItem replayed = item(); reload.spawned(replayed, tile, 330, 20);
        assertEquals(1, reload.finish(330, 20).size());
        reload.quantityChanged(replayed, tile, 1, 330, 21);
        reload.loading(new IdentityHashMap<>(), 330, 22);
        reload.spawned(item(), tile, 330, 22);
        List<ReloadedDrops.Candidate> matches = reload.finish(330, 22);
        assertTrue(matches.isEmpty());
    }

    @Test public void fingerprintStorageRemainsCappedEvenWithOverflowDuplicates()
    {
        confirmed.clear();
        for (int id = 1; id <= DropTracker.MAX_TRACKED + 20; id++)
        {
            TileItem distinct = item(); when(distinct.getId()).thenReturn(id);
            confirmed.put(distinct, tile);
        }
        TileItem duplicateA = item(), duplicateB = item();
        when(duplicateA.getId()).thenReturn(999); when(duplicateB.getId()).thenReturn(999);
        confirmed.put(duplicateA, tile); confirmed.put(duplicateB, tile);
        reload.loading(confirmed, 330, 20);
        assertEquals(DropTracker.MAX_TRACKED, reload.savedCount());
    }
}
