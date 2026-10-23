package com.mydrops;

import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemLayer;
import net.runelite.api.Point;
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

    @Test public void restoresUniqueReplayWhenBothTimersShiftEqually()
    {
        reload.loading(confirmed, 330, 20);
        TileItem replayed = item();
        when(replayed.getVisibleTime()).thenReturn(10);
        when(replayed.getDespawnTime()).thenReturn(199);
        reload.spawned(replayed, tile, 330, 20);
        assertSame(replayed, reload.finish(330, 20).get(0).item);
    }

    @Test public void sceneReplayRejectsSameLifetimeAtLaterDeadline()
    {
        reload.loading(confirmed, 330, 20);
        TileItem later = item();
        when(later.getVisibleTime()).thenReturn(41);
        when(later.getDespawnTime()).thenReturn(230);
        reload.spawned(later, tile, 330, 20);
        for (int tick = 20; tick <= 23; tick++)
        {
            assertTrue(reload.finish(330, tick).isEmpty());
        }
    }

    @Test public void sceneReplayRejectsInactiveView()
    {
        reload.loading(confirmed, 330, 20);
        TileItem same = item();
        reload.spawned(same, tile, 330, 20);
        assertTrue(reload.finish(330, 20, id -> 4, current -> false).isEmpty());
    }

    @Test public void shiftedReplayKeepsOriginalExpiryAcrossMoreLoadingLines()
    {
        reload.loading(confirmed, 330, 20);
        TileItem firstReplay = item();
        when(firstReplay.getVisibleTime()).thenReturn(10);
        when(firstReplay.getDespawnTime()).thenReturn(199);
        reload.spawned(firstReplay, tile, 330, 20);
        assertEquals(1, reload.finish(330, 20).size());

        confirmed.clear(); confirmed.put(firstReplay, tile);
        reload.loading(confirmed, 330, 25);
        TileItem secondReplay = item();
        when(secondReplay.getVisibleTime()).thenReturn(10);
        when(secondReplay.getDespawnTime()).thenReturn(199);
        reload.spawned(secondReplay, tile, 330, 25);
        assertEquals(1, reload.finish(330, 25).size());
        reload.expire(200);
        assertEquals(0, reload.savedCount());
    }

    @Test public void restoresAfterLeavingAndReturningAcrossTwoLoadingLines()
    {
        reload.loading(confirmed, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        reload.loading(new IdentityHashMap<>(), 330, 25);
        TileItem replayed = item(); reload.spawned(replayed, tile, 330, 25);
        assertSame(replayed, reload.finish(330, 25).get(0).item);
    }

    @Test public void replayCanArriveAfterFirstLoggedInTick()
    {
        reload.loading(confirmed, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        TileItem replayed = item();
        reload.spawned(replayed, tile, 330, 21);
        assertSame(replayed, reload.finish(330, 21).get(0).item);
    }

    @Test public void waitsForTwoConfirmedItemsReplayedOnDifferentTicks()
    {
        TileItem second = item(); when(second.getId()).thenReturn(101);
        confirmed.put(second, tile);
        reload.loading(confirmed, 330, 20);
        TileItem firstReplay = item();
        reload.spawned(firstReplay, tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        TileItem secondReplay = item(); when(secondReplay.getId()).thenReturn(101);
        reload.spawned(secondReplay, tile, 330, 21);
        List<ReloadedDrops.Candidate> restored = reload.finish(330, 21);
        assertEquals(2, restored.size());
    }

    @Test public void missingReplayStopsWaitingAfterThreeLoggedInTicks()
    {
        reload.loading(confirmed, 330, 20);
        for (int tick = 20; tick < 23; tick++)
        {
            assertTrue(reload.finish(330, tick).isEmpty());
            assertTrue(reload.isReplaying());
        }
        assertTrue(reload.finish(330, 23).isEmpty());
        assertFalse(reload.isReplaying());
        reload.spawned(item(), tile, 330, 24);
        assertTrue(reload.finish(330, 24).isEmpty());
    }

    @Test public void matchingSpawnAfterReplayWindowStillRestores()
    {
        reload.loading(confirmed, 330, 20);
        for (int tick = 20; tick <= 23; tick++) { assertTrue(reload.finish(330, tick).isEmpty()); }
        TileItem late = item(); reload.spawned(late, tile, 330, 30);
        assertTrue(reload.finish(330, 30).isEmpty());
        assertSame(late, reload.finish(330, 31).get(0).item);
    }

    @Test public void lateCandidateTimerRebaseDuringSettleStillRestores()
    {
        reload.loading(confirmed, 330, 20);
        for (int tick = 20; tick <= 23; tick++) { reload.finish(330, tick); }
        TileItem late = item(); reload.spawned(late, tile, 330, 30);
        assertTrue(reload.finish(330, 30).isEmpty());
        when(late.getVisibleTime()).thenReturn(10);
        when(late.getDespawnTime()).thenReturn(199);
        assertSame(late, reload.finish(330, 31).get(0).item);
    }

    @Test public void rejectedLateCandidateErasesConfirmation()
    {
        reload.loading(confirmed, 330, 20);
        for (int tick = 20; tick <= 23; tick++) { reload.finish(330, tick); }
        TileItem late = item(); reload.spawned(late, tile, 330, 30);
        assertTrue(reload.finish(330, 30).isEmpty());
        when(late.getDespawnTime()).thenReturn(201); // Unequal timer shift.
        assertTrue(reload.finish(330, 31).isEmpty());
        assertEquals(0, reload.savedCount());
        TileItem stable = item(); reload.spawned(stable, tile, 330, 32);
        assertTrue(reload.finish(330, 32).isEmpty());
        assertTrue(reload.finish(330, 33).isEmpty());
    }

    @Test public void unrelatedEqualLifetimeCannotOpenLateReplay()
    {
        reload.loading(confirmed, 330, 20);
        for (int tick = 20; tick <= 23; tick++) { reload.finish(330, tick); }
        TileItem replacement = item();
        when(replacement.getVisibleTime()).thenReturn(41);
        when(replacement.getDespawnTime()).thenReturn(230);
        reload.spawned(replacement, tile, 330, 30);
        assertTrue(reload.finish(330, 30).isEmpty());
        assertFalse(reload.isReplaying());
    }

    @Test public void lateSpawnStillRejectsDuplicatesAndExpiredConfirmation()
    {
        reload.loading(confirmed, 330, 20);
        for (int tick = 20; tick <= 23; tick++) { reload.finish(330, tick); }
        reload.spawned(item(), tile, 330, 30);
        reload.spawned(item(), tile, 330, 30);
        assertTrue(reload.finish(330, 31).isEmpty());
        reload.expire(200);
        reload.spawned(item(), tile, 330, 201);
        assertTrue(reload.finish(330, 201).isEmpty());
    }

    @Test public void previouslyConfirmedDropCanReplayAsPublicAfterVisibilityTime()
    {
        reload.loading(confirmed, 330, 20);
        TileItem publicItem = item(); when(publicItem.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        reload.spawned(publicItem, tile, 330, 20);
        assertSame(publicItem, reload.finish(330, 20).get(0).item);
    }

    @Test public void publicCandidateRemovalInvalidatesConfirmation()
    {
        reload.loading(confirmed, 330, 20);
        TileItem publicItem = item(); when(publicItem.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        reload.spawned(publicItem, tile, 330, 20);
        assertSame(publicItem, reload.finish(330, 20).get(0).item);
        reload.despawned(publicItem, tile, 330, 21);
        assertEquals(0, reload.savedCount());

        reload.loading(confirmed, 330, 22);
        reload.spawned(publicItem, tile, 330, 22);
        assertSame(publicItem, reload.finish(330, 22).get(0).item);
        reload.quantityChanged(publicItem, tile, 1, 330, 23);
        assertEquals(0, reload.savedCount());
    }

    @Test public void confirmedRemovalCanHandoffToOtherOwnershipAndReplayAfterLoading()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        when(original.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER); // May already change before despawn event.
        reload.despawned(original, tile, 330, 20, true, 4);
        assertEquals(1, reload.savedCount());
        TileItem transferred = item();
        when(transferred.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(transferred.getVisibleTime()).thenReturn(38);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.spawned(transferred, tile, 330, 20);
        assertTrue(reload.finish(330, 20, id -> 4).isEmpty());
        assertSame(transferred, reload.finish(330, 21, id -> 4).get(0).item);

        confirmed.clear(); confirmed.put(transferred, tile);
        reload.loading(confirmed, 330, 22);
        TileItem replayed = item();
        when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(replayed.getVisibleTime()).thenReturn(38);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(replayed));
        reload.spawned(replayed, tile, 330, 22);
        assertSame(replayed, reload.finish(330, 22).get(0).item);
    }

    @Test public void transitionedOtherReplayRejectsCompetingSameItemPile()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        TileItem transferred = item();
        when(transferred.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.spawned(transferred, tile, 330, 20);
        reload.finish(330, 21, id -> 4);
        confirmed.clear(); confirmed.put(transferred, tile);
        reload.loading(confirmed, 330, 22);
        TileItem replayed = item();
        when(replayed.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        TileItem competing = item();
        when(tile.getGroundItems()).thenReturn(java.util.Arrays.asList(replayed, competing));
        reload.spawned(replayed, tile, 330, 22);
        assertTrue(reload.finish(330, 22).isEmpty());
        assertEquals(0, reload.savedCount());
    }

    @Test public void handoffMustSettleByNextTickEvenWhenReplacementArrives()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        TileItem transferred = item();
        when(transferred.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.spawned(transferred, tile, 330, 20);
        assertTrue(reload.finish(330, 22, id -> 4).isEmpty());
        assertEquals(0, reload.savedCount());
    }

    @Test public void noneReplacementUsesExistingLateReplayPath()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        TileItem none = item();
        when(none.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        reload.spawned(none, tile, 330, 20);
        assertTrue(reload.finish(330, 20, id -> 4).isEmpty());
        assertSame(none, reload.finish(330, 21, id -> 4).get(0).item);
    }

    @Test public void inactiveViewConfirmedOtherRemovalInvalidatesConfirmation()
    {
        reload.remember(confirmed, 330, 20);
        when(original.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        reload.despawned(original, tile, 330, 20, true, false, 4);
        assertEquals(0, reload.savedCount());
    }

    @Test public void boundNoneItemCanHandoffAtVisibilityChange()
    {
        reload.loading(confirmed, 330, 20);
        TileItem none = item();
        when(none.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        reload.spawned(none, tile, 330, 20);
        assertSame(none, reload.finish(330, 20).get(0).item);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(none, tile, 330, 21, true, 4);
        TileItem transferred = item();
        when(transferred.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.spawned(transferred, tile, 330, 21);
        assertSame(transferred, reload.finish(330, 22, id -> 4).get(0).item);
    }

    @Test public void otherOwnershipWithoutTrackedHandoffIsNotAttributed()
    {
        reload.remember(confirmed, 330, 20);
        TileItem other = item(); when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        reload.spawned(other, tile, 330, 20);
        assertTrue(reload.finish(330, 21).isEmpty());
    }

    @Test public void handoffRejectsDuplicateStackOrInventoryGain()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        TileItem first = item(), second = item();
        when(first.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(second.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(first.getVisibleTime()).thenReturn(38);
        when(second.getVisibleTime()).thenReturn(38);
        when(tile.getGroundItems()).thenReturn(java.util.Arrays.asList(first, second));
        reload.spawned(first, tile, 330, 20); reload.spawned(second, tile, 330, 20);
        assertTrue(reload.finish(330, 21, id -> 4).isEmpty());
        assertEquals(0, reload.savedCount());

        reload.remember(confirmed, 330, 22);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 22, true, 4);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(first));
        reload.spawned(first, tile, 330, 22);
        assertTrue(reload.finish(330, 23, id -> 5).isEmpty());
        assertEquals(0, reload.savedCount());
    }

    @Test public void missingOrLateHandoffReplacementExpires()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        assertTrue(reload.finish(330, 21, id -> 4).isEmpty());
        assertTrue(reload.finish(330, 22, id -> 4).isEmpty());
        assertEquals(0, reload.savedCount());
        TileItem other = item(); when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        reload.spawned(other, tile, 330, 22);
        assertTrue(reload.finish(330, 23).isEmpty());
    }

    @Test public void mismatchedSameTileSpawnPoisonsHandoff()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        TileItem wrong = item(); when(wrong.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(wrong.getQuantity()).thenReturn(2);
        reload.spawned(wrong, tile, 330, 20);
        TileItem right = item(); when(right.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(right));
        reload.spawned(right, tile, 330, 20);
        assertTrue(reload.finish(330, 21, id -> 4).isEmpty());
        assertEquals(0, reload.savedCount());
    }

    @Test public void firstLoadCarriesPendingHandoffButSecondLoadCancelsIt()
    {
        reload.remember(confirmed, 330, 20);
        when(view.isTopLevel()).thenReturn(true);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 20);
        TileItem other = item(); when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        assertEquals(1, reload.savedCount());
        reload.loading(new IdentityHashMap<>(), 330, 21);
        assertEquals(0, reload.savedCount());
    }

    @Test public void shiftedTrackedItemCanHandoffThroughOneSceneLoad()
    {
        shiftedTrackedItemAfterReplay();
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 32, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 32);
        when(view.isTopLevel()).thenReturn(true);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(41);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 33);
        for (int tick = 33; tick < 36; tick++) { assertTrue(reload.finish(330, tick, id -> 4).isEmpty()); }
        assertSame(other, reload.finish(330, 36, id -> 4).get(0).item);
        assertEquals(1, reload.savedCount());
    }

    @Test public void sceneHandoffCannotSurviveASecondLoad()
    {
        shiftedTrackedItemAfterReplay();
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 32, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 32);
        reload.loading(new IdentityHashMap<>(), 330, 33);
        assertEquals(0, reload.savedCount());
    }

    @Test public void shiftedTrackedNoneItemCanHandoffThroughSceneLoad()
    {
        shiftedTrackedItemAfterReplay();
        when(original.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 32, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 32);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 33);
        for (int tick = 33; tick < 36; tick++) { assertTrue(reload.finish(330, tick, id -> 4).isEmpty()); }
        assertSame(other, reload.finish(330, 36, id -> 4).get(0).item);
    }

    @Test public void subviewHandoffIsNotCarriedThroughLoad()
    {
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 20);
        assertEquals(0, reload.savedCount());
    }

    @Test public void unloadingCandidateViewCancelsSceneHandoff()
    {
        shiftedTrackedItemAfterReplay();
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 32, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 32);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 33);
        reload.unloaded(view, true);
        assertEquals(0, reload.savedCount());
        for (int tick = 33; tick <= 36; tick++) { assertTrue(reload.finish(330, tick, id -> 4).isEmpty()); }
    }

    @Test public void inactiveViewWitnessRequiresFreshActiveScene()
    {
        inactiveViewWitnessWithPreloadOther();
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(27);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 32);
        for (int tick = 32; tick <= 35; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4, current -> false).isEmpty());
        }
        assertEquals(0, reload.savedCount());
    }

    @Test public void preloadOtherWitnessRequiresPairedReplayShift()
    {
        inactiveViewWitnessWithPreloadOther();
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(28);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 32);
        for (int tick = 32; tick <= 35; tick++) { assertTrue(reload.finish(330, tick, id -> 4).isEmpty()); }
        assertEquals(0, reload.savedCount());
    }

    @Test public void inactiveRemovalFollowedBySelfReplayKeepsOwnMark()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 30);
        reload.despawned(original, mock(Tile.class), 330, 31, true, true, false, 4);
        TileItem same = item();
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(same));
        reload.spawned(same, tile, 330, 31);
        assertTrue(reload.finish(330, 31, id -> 4).isEmpty());
        assertSame(same, reload.finish(330, 32, id -> 4).get(0).item);
        assertEquals(1, reload.savedCount());
    }

    @Test public void inactiveRemovalSurvivesLongAbsenceAndEmptyLoadsUntilOriginalExpiry()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 30);
        reload.despawned(original, mock(Tile.class), 330, 31, true, true, false, 4);
        for (int tick = 32; tick < 65; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(1, reload.savedCount());
        reload.loading(new IdentityHashMap<>(), 330, 65);
        reload.unloaded(view, true);
        for (int tick = 65; tick < 69; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(1, reload.savedCount());
        reload.loading(new IdentityHashMap<>(), 330, 70);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(38);
        when(returned.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 70);
        for (int tick = 70; tick < 73; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertSame(returned, reload.finish(330, 73, id -> 4).get(0).item);
        reload.expire(200);
        assertEquals(0, reload.savedCount());
    }

    @Test public void inactiveRemovalRejectsPickupObservedDuringAbsence()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 30);
        reload.despawned(original, mock(Tile.class), 330, 31, true, true, false, 4);
        reload.finish(330, 32, id -> 5);
        assertEquals(0, reload.savedCount());
        reload.loading(new IdentityHashMap<>(), 330, 70);
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        reload.spawned(other, tile, 330, 70);
        assertTrue(reload.finish(330, 70, id -> 4).isEmpty());
    }

    @Test public void inactiveRemovalKeepsPickupRejectionAfterInventoryFallsAgain()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 30);
        reload.despawned(original, mock(Tile.class), 330, 31, true, true, false, 4);
        reload.finish(330, 32, id -> 5);
        reload.finish(330, 33, id -> 4);
        assertEquals(0, reload.savedCount());
    }

    @Test public void inactiveRemovalExpiresEvenWithoutReturn()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 30);
        reload.despawned(original, mock(Tile.class), 330, 31, true, true, false, 4);
        reload.finish(330, 150, id -> 4);
        assertEquals(1, reload.savedCount());
        reload.expire(200);
        assertEquals(0, reload.savedCount());
        reload.loading(new IdentityHashMap<>(), 330, 201);
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        reload.spawned(other, tile, 330, 201);
        assertTrue(reload.finish(330, 201, id -> 4).isEmpty());
    }

    @Test public void inactiveRemovalUsesEarlierObservedExpiry()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        when(original.getVisibleTime()).thenReturn(10);
        when(original.getDespawnTime()).thenReturn(199);
        reload.remember(confirmed, 330, 30);
        reload.despawned(original, mock(Tile.class), 330, 31, true, true, false, 4);
        assertEquals(1, reload.savedCount());
        reload.expire(199);
        assertEquals(0, reload.savedCount());
    }

    @Test public void publicReturnAfterUnseenChangeCanMatchConfirmedDrop()
    {
        leaveConfirmedItemOutOfRange();
        Tile nextTile = reloadedTile();
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(52); // Changed visibility is not identity proof.
        when(returned.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 60);
        for (int tick = 60; tick < 63; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertSame(returned, reload.finish(330, 63, id -> 4).get(0).item);
        reload.expire(200);
        assertEquals(0, reload.savedCount());
    }

    @Test public void unseenPublicReturnRejectsInventoryGainEvenIfCountLaterFalls()
    {
        leaveConfirmedItemOutOfRange();
        reload.finish(330, 50, id -> 5);
        reload.finish(330, 51, id -> 4);
        assertEquals(0, reload.savedCount());
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(52);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 60);
        assertTrue(reload.finish(330, 60, id -> 4).isEmpty());
    }

    @Test public void unseenPublicReturnRejectsExtendedOrUnrelatedExpiry()
    {
        leaveConfirmedItemOutOfRange();
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(52);
        when(other.getDespawnTime()).thenReturn(198);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 60);
        for (int tick = 60; tick <= 63; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(0, reload.savedCount());
    }

    @Test public void unseenPublicReturnRejectsCompetingSameItemPile()
    {
        leaveConfirmedItemOutOfRange();
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(52);
        when(other.getDespawnTime()).thenReturn(199);
        TileItem competing = item();
        when(nextTile.getGroundItems()).thenReturn(java.util.Arrays.asList(other, competing));
        reload.spawned(other, nextTile, 330, 60);
        for (int tick = 60; tick <= 63; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(0, reload.savedCount());
    }

    @Test public void unseenPublicReturnRequiresMatchingInstanceChunk()
    {
        when(view.isInstance()).thenReturn(true);
        when(tile.getSceneLocation()).thenReturn(new Point(16, 24));
        int[][][] chunks = new int[1][13][13];
        chunks[0][2][3] = 123;
        when(view.getInstanceTemplateChunks()).thenReturn(chunks);
        leaveConfirmedItemOutOfRange();
        Tile nextTile = reloadedTile();
        WorldView nextView = nextTile.getItemLayer().getWorldView();
        when(nextView.isInstance()).thenReturn(true);
        when(nextTile.getSceneLocation()).thenReturn(new Point(16, 24));
        int[][][] changedChunks = new int[1][13][13];
        changedChunks[0][2][3] = 124;
        when(nextView.getInstanceTemplateChunks()).thenReturn(changedChunks);
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(52);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 60);
        for (int tick = 60; tick <= 63; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(0, reload.savedCount());
    }

    @Test public void unseenPublicReturnMatchesSameInstanceChunk()
    {
        when(view.isInstance()).thenReturn(true);
        when(tile.getSceneLocation()).thenReturn(new Point(16, 24));
        int[][][] chunks = new int[1][13][13];
        chunks[0][2][3] = 123;
        when(view.getInstanceTemplateChunks()).thenReturn(chunks);
        leaveConfirmedItemOutOfRange();
        Tile nextTile = reloadedTile();
        WorldView nextView = nextTile.getItemLayer().getWorldView();
        when(nextView.isInstance()).thenReturn(true);
        when(nextTile.getSceneLocation()).thenReturn(new Point(16, 24));
        when(nextView.getInstanceTemplateChunks()).thenReturn(chunks);
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(52);
        when(returned.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 60);
        for (int tick = 60; tick < 63; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertSame(returned, reload.finish(330, 63, id -> 4).get(0).item);
    }

    @Test public void unseenPublicReturnNeedsKnownInventorySnapshot()
    {
        when(view.isTopLevel()).thenReturn(true);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 20);
        reload.loading(confirmed, 330, 21);
        reload.unloaded(view, true);
        reload.loading(new IdentityHashMap<>(), 330, 60);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(52);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 60);
        for (int tick = 60; tick <= 63; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
    }

    @Test public void unseenPublicReturnCancelsOnSamePlaceQuantityChange()
    {
        leaveConfirmedItemOutOfRange();
        Tile nextTile = reloadedTile();
        TileItem changed = item();
        when(changed.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(changed.getQuantity()).thenReturn(2);
        reload.quantityChanged(changed, nextTile, 1, 330, 60);
        assertEquals(0, reload.savedCount());
    }

    @Test public void unseenPublicReturnWaitsForOriginalVisibilityDeadline()
    {
        when(original.getVisibleTime()).thenReturn(50);
        when(view.isTopLevel()).thenReturn(true);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 20);
        reload.loading(confirmed, 330, 21, id -> 4);
        reload.unloaded(view, true);
        reload.loading(new IdentityHashMap<>(), 330, 35, id -> 4);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(60);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 35);
        for (int tick = 35; tick <= 38; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(0, reload.savedCount());
    }

    @Test public void previouslyRestoredOtherCanReturnAfterEmptyLoads()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 100);
        reload.loading(confirmed, 330, 101, id -> 4);
        reload.unloaded(view, true);
        for (int tick = 101; tick <= 104; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        reload.loading(new IdentityHashMap<>(), 330, 150, id -> 4);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(112);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 150);
        for (int tick = 150; tick < 153; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        List<ReloadedDrops.Candidate> matches = reload.finish(330, 153, id -> 4);
        assertEquals(1, matches.size());
        assertSame(returned, matches.get(0).item);
    }

    @Test public void transitionedOtherExactReplayRestoresOnlyOnce()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 100);
        reload.loading(confirmed, 330, 101, id -> 4);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(38);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 101);
        int restored = 0;
        for (int tick = 101; tick <= 104; tick++)
        {
            List<ReloadedDrops.Candidate> matches = reload.finish(330, tick, id -> 4);
            restored += matches.size();
            if (!matches.isEmpty()) { assertSame(returned, matches.get(0).item); }
        }
        assertEquals(1, restored);
        assertEquals(1, reload.savedCount());
    }

    @Test public void transitionedScalarWithoutTrackedObjectCannotCreateReturnWitness()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 100);
        // The old object is no longer in the confirmed tracker at the load.
        reload.loading(new IdentityHashMap<>(), 330, 101, id -> 4);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(112);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 101);
        for (int tick = 101; tick <= 104; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
    }

    @Test public void transitionedOtherCanReturnAfterExplicitInactiveRemoval()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 100);
        reload.despawned(transferred, mock(Tile.class), 330, 101, true, true, false, 4);
        reload.loading(new IdentityHashMap<>(), 330, 150, id -> 4);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(112);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 150);
        for (int tick = 150; tick < 153; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertSame(returned, reload.finish(330, 153, id -> 4).get(0).item);
    }

    @Test public void transitionedOtherActiveRemovalCannotRebind()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 100);
        reload.despawned(transferred, tile, 330, 101, true, true, true, 4);
        assertEquals(0, reload.savedCount());
    }

    @Test public void transitionedOtherPreloadRebaseDoesNotExtendOriginalExpiry()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 129);
        reload.despawned(transferred, mock(Tile.class), 330, 130, true, true, false, 4);
        TileItem beforeLoad = item();
        when(beforeLoad.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(beforeLoad.getVisibleTime()).thenReturn(89);
        when(beforeLoad.getDespawnTime()).thenReturn(201);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(beforeLoad));
        reload.spawned(beforeLoad, tile, 330, 130);
        reload.loading(new IdentityHashMap<>(), 330, 131, id -> 4);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(88);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 131);
        for (int tick = 131; tick < 134; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertSame(returned, reload.finish(330, 134, id -> 4).get(0).item);
        reload.expire(200);
        assertEquals(0, reload.savedCount());
    }

    @Test public void transitionedOtherPreloadRebaseRejectsExtendedFinalExpiry()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 129);
        reload.despawned(transferred, mock(Tile.class), 330, 130, true, true, false, 4);
        TileItem beforeLoad = item();
        when(beforeLoad.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(beforeLoad.getVisibleTime()).thenReturn(89);
        when(beforeLoad.getDespawnTime()).thenReturn(201);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(beforeLoad));
        reload.spawned(beforeLoad, tile, 330, 130);
        reload.loading(new IdentityHashMap<>(), 330, 131, id -> 4);
        reload.unloaded(view, true);
        Tile nextTile = reloadedTile();
        TileItem returned = item();
        when(returned.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(returned.getVisibleTime()).thenReturn(89);
        when(returned.getDespawnTime()).thenReturn(201);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(returned));
        reload.spawned(returned, nextTile, 330, 131);
        for (int tick = 131; tick <= 134; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(0, reload.savedCount());
    }

    @Test public void transitionedOtherExtendedPreloadNeedsImmediateLoad()
    {
        TileItem transferred = acceptObservedOtherHandoff();
        confirmed.clear(); confirmed.put(transferred, tile);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.remember(confirmed, 330, 129);
        reload.despawned(transferred, mock(Tile.class), 330, 130, true, true, false, 4);
        TileItem beforeLoad = item();
        when(beforeLoad.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(beforeLoad.getVisibleTime()).thenReturn(89);
        when(beforeLoad.getDespawnTime()).thenReturn(201);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(beforeLoad));
        reload.spawned(beforeLoad, tile, 330, 130);
        assertTrue(reload.finish(330, 132, id -> 4).isEmpty());
        assertEquals(0, reload.savedCount());
    }

    private TileItem acceptObservedOtherHandoff()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 20, true, 4);
        TileItem transferred = item();
        when(transferred.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(transferred.getVisibleTime()).thenReturn(38);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(transferred));
        reload.spawned(transferred, tile, 330, 20);
        assertSame(transferred, reload.finish(330, 21, id -> 4).get(0).item);
        return transferred;
    }

    @Test public void activeHandoffWithoutPriorBoundSnapshotDoesNotCrash()
    {
        reload.remember(confirmed, 330, 20, current -> false);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.despawned(original, tile, 330, 20, true, true, true, 4);
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getVisibleTime()).thenReturn(38);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, tile, 330, 20);
        assertSame(other, reload.finish(330, 21, id -> 4).get(0).item);
    }

    private void leaveConfirmedItemOutOfRange()
    {
        when(view.isTopLevel()).thenReturn(true);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 20);
        reload.loading(confirmed, 330, 21, id -> 4);
        reload.unloaded(view, true);
        for (int tick = 21; tick <= 24; tick++)
        {
            assertTrue(reload.finish(330, tick, id -> 4).isEmpty());
        }
        assertEquals(1, reload.savedCount());
        reload.loading(new IdentityHashMap<>(), 330, 60, id -> 4);
        reload.unloaded(view, true);
    }

    private void inactiveViewWitnessWithPreloadOther()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.remember(confirmed, 330, 20);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(original));
        reload.remember(confirmed, 330, 30);
        reload.despawned(original, mock(Tile.class), 330, 31, true, true, false, 4);
        TileItem beforeLoad = item();
        when(beforeLoad.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(beforeLoad.getVisibleTime()).thenReturn(28);
        when(tile.getGroundItems()).thenReturn(Collections.singletonList(beforeLoad));
        reload.spawned(beforeLoad, tile, 330, 31);
        reload.loading(new IdentityHashMap<>(), 330, 31);
        reload.unloaded(view, true);
        assertEquals(1, reload.savedCount());
    }

    @Test public void sceneHandoffRejectsWrongExpiry()
    {
        shiftedTrackedItemAfterReplay();
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 32, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 32);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getDespawnTime()).thenReturn(198);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 33);
        for (int tick = 33; tick <= 36; tick++) { assertTrue(reload.finish(330, tick, id -> 4).isEmpty()); }
        assertEquals(0, reload.savedCount());
    }

    @Test public void sceneHandoffRejectsInventoryGain()
    {
        shiftedTrackedItemAfterReplay();
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 32, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 32);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getDespawnTime()).thenReturn(199);
        when(nextTile.getGroundItems()).thenReturn(Collections.singletonList(other));
        reload.spawned(other, nextTile, 330, 33);
        for (int tick = 33; tick <= 36; tick++) { assertTrue(reload.finish(330, tick, id -> 5).isEmpty()); }
        assertEquals(0, reload.savedCount());
    }

    @Test public void sceneHandoffRejectsCompetingPile()
    {
        shiftedTrackedItemAfterReplay();
        when(tile.getGroundItems()).thenReturn(Collections.emptyList());
        reload.despawned(original, tile, 330, 32, true, 4);
        reload.loading(new IdentityHashMap<>(), 330, 32);
        Tile nextTile = reloadedTile();
        TileItem other = item();
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);
        when(other.getDespawnTime()).thenReturn(199);
        TileItem competing = item();
        when(nextTile.getGroundItems()).thenReturn(java.util.Arrays.asList(other, competing));
        reload.spawned(other, nextTile, 330, 33);
        for (int tick = 33; tick <= 36; tick++) { assertTrue(reload.finish(330, tick, id -> 4).isEmpty()); }
        assertEquals(0, reload.savedCount());
    }

    private Tile shiftedTrackedItemAfterReplay()
    {
        when(view.isTopLevel()).thenReturn(true);
        reload.loading(confirmed, 330, 20);
        TileItem shifted = item();
        when(shifted.getVisibleTime()).thenReturn(10);
        when(shifted.getDespawnTime()).thenReturn(199);
        reload.spawned(shifted, tile, 330, 20);
        assertSame(shifted, reload.finish(330, 20).get(0).item);
        confirmed.clear();
        confirmed.put(shifted, tile);
        original = shifted;
        return tile;
    }

    private Tile reloadedTile()
    {
        WorldView nextView = mock(WorldView.class);
        when(nextView.getId()).thenReturn(7);
        Tile nextTile = mock(Tile.class);
        ItemLayer nextLayer = mock(ItemLayer.class);
        when(nextTile.getItemLayer()).thenReturn(nextLayer);
        when(nextLayer.getWorldView()).thenReturn(nextView);
        when(nextTile.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        return nextTile;
    }

    @Test public void publicItemBeforeVisibilityTimeIsNotAttributed()
    {
        reload.loading(confirmed, 330, 5);
        TileItem publicItem = item(); when(publicItem.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        reload.spawned(publicItem, tile, 330, 5);
        assertTrue(reload.finish(330, 5).isEmpty());
        assertTrue(reload.finish(330, 8).isEmpty());
    }

    @Test public void latePublicReplayRequiresPriorConfirmationAndMatchingTimers()
    {
        reload.remember(confirmed, 330, 5);
        TileItem publicItem = item(); when(publicItem.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        when(publicItem.getDespawnTime()).thenReturn(201);
        reload.spawned(publicItem, tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        when(publicItem.getDespawnTime()).thenReturn(200);
        reload.spawned(publicItem, tile, 330, 21);
        assertTrue(reload.finish(330, 21).isEmpty());
        assertSame(publicItem, reload.finish(330, 22).get(0).item);
    }

    @Test public void poisonedDuplicateDoesNotEndWaitForAnotherItem()
    {
        TileItem second = item(), third = item();
        when(second.getId()).thenReturn(101); when(third.getId()).thenReturn(102);
        confirmed.put(second, tile); confirmed.put(third, tile);
        reload.loading(confirmed, 330, 20);
        reload.spawned(item(), tile, 330, 20);
        reload.spawned(item(), tile, 330, 20); // Duplicate ID 100 poisons its key.
        TileItem secondReplay = item(); when(secondReplay.getId()).thenReturn(101);
        reload.spawned(secondReplay, tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
        TileItem thirdReplay = item(); when(thirdReplay.getId()).thenReturn(102);
        reload.spawned(thirdReplay, tile, 330, 21);
        List<ReloadedDrops.Candidate> restored = reload.finish(330, 21);
        assertEquals(2, restored.size());
        assertTrue(restored.stream().anyMatch(candidate -> candidate.item == secondReplay));
        assertTrue(restored.stream().anyMatch(candidate -> candidate.item == thirdReplay));
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

    @Test public void changedQuantityOrUnequalTimerShiftFailsClosed()
    {
        reload.loading(confirmed, 330, 20);
        TileItem changed = item(); when(changed.getQuantity()).thenReturn(2);
        reload.spawned(changed, tile, 330, 20); assertTrue(reload.finish(330, 20).isEmpty());
        reload.loading(new IdentityHashMap<>(), 330, 21);
        changed = item(); when(changed.getDespawnTime()).thenReturn(201);
        when(changed.getVisibleTime()).thenReturn(13);
        reload.spawned(changed, tile, 330, 21); assertTrue(reload.finish(330, 21).isEmpty());
    }

    @Test public void duplicateBaseKeysWithDifferentTimersFailClosed()
    {
        TileItem secondOld = item();
        when(secondOld.getVisibleTime()).thenReturn(12);
        when(secondOld.getDespawnTime()).thenReturn(201);
        confirmed.put(secondOld, tile);
        reload.loading(confirmed, 330, 20);
        reload.spawned(item(), tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());

        reload.clear(); confirmed.clear(); confirmed.put(original, tile);
        reload.loading(confirmed, 330, 20);
        TileItem firstReplay = item(), secondReplay = item();
        when(firstReplay.getVisibleTime()).thenReturn(16);
        when(firstReplay.getDespawnTime()).thenReturn(205);
        when(secondReplay.getVisibleTime()).thenReturn(17);
        when(secondReplay.getDespawnTime()).thenReturn(206);
        reload.spawned(firstReplay, tile, 330, 20);
        reload.spawned(secondReplay, tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
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

    @Test public void topLevelSceneUnloadDuringLoadingKeepsExactConfirmation()
    {
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        when(view.isTopLevel()).thenReturn(true);
        reload.loading(confirmed, 330, 20);
        reload.unloaded(view, true);
        TileItem replayed = item();
        reload.spawned(replayed, tile, 330, 20);
        assertSame(replayed, reload.finish(330, 20).get(0).item);
    }

    @Test public void ordinaryTopLevelUnloadStillDiscardsConfirmation()
    {
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        when(view.isTopLevel()).thenReturn(true);
        reload.loading(confirmed, 330, 20);
        reload.unloaded(view, false);
        reload.spawned(item(), tile, 330, 20);
        assertTrue(reload.finish(330, 20).isEmpty());
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
