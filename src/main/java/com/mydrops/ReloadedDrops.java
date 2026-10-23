package com.mydrops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.function.Predicate;
import net.runelite.api.ItemLayer;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/** Rebinds unique confirmed drops when scene reloads shift both item timers equally. */
final class ReloadedDrops
{
    private final Map<Key, Boolean> saved = new HashMap<>();
    private final Map<Key, Candidate> replayed = new HashMap<>();
    private final Map<TileItem, Key> boundNonSelf = new IdentityHashMap<>();
    private final Map<TileItem, Bound> boundTracked = new IdentityHashMap<>();
    private final Map<Key, Handoff> handoffs = new HashMap<>();
    private final Set<Key> transitioned = new HashSet<>();
    private final Set<Key> ambiguous = new HashSet<>();
    private int world = -1;
    private int nextExpiry = Integer.MAX_VALUE;
    private int settleTicks;
    private boolean replaying, late;

    void loading(Map<TileItem, Tile> confirmed, int currentWorld, int tick)
    {
        loading(confirmed, currentWorld, tick, id -> Integer.MIN_VALUE);
    }

    void loading(Map<TileItem, Tile> confirmed, int currentWorld, int tick,
        ToIntFunction<Integer> inventoryCount)
    {
        for (Map.Entry<Key, Handoff> entry : new ArrayList<>(handoffs.entrySet()))
        {
            Handoff handoff = entry.getValue();
            if ((handoff.sceneReload && (!handoff.requiresSceneReload || handoff.candidate != null))
                || handoff.ambiguous
                || (handoff.candidate != null && !handoff.requiresSceneReload)
                || (handoff.candidate != null && handoff.transitionedWitness
                    && handoff.candidate.item.getDespawnTime() > handoff.original.despawn
                    && tick > handoff.candidateTick + 1)
                || !handoff.topLevel || handoff.original.world != currentWorld
                || tick < handoff.tick || tick >= handoff.original.despawn
                || (!handoff.requiresSceneReload && tick > handoff.tick + 1))
            {
                handoffs.remove(entry.getKey());
                removeSaved(entry.getKey());
                continue;
            }
            if (handoff.candidate != null)
            {
                if (!handoff.preloadUnique)
                {
                    handoffs.remove(entry.getKey());
                    removeSaved(entry.getKey());
                    continue;
                }
                handoff.preloadVisible = handoff.candidate.item.getVisibleTime();
                handoff.preloadDespawn = handoff.candidate.item.getDespawnTime();
                handoff.candidate = null;
            }
            // An explicit item removal just before a top-level scene reload
            // can be the ownership transition. Keep one scalar witness only.
            handoff.sceneReload = true;
            handoff.oldItem = null;
            handoff.view = null;
        }
        for (TileItem item : confirmed.keySet())
        {
            Bound bound = boundTracked.get(item);
            if (bound == null || handoffs.containsKey(bound.original)
                || !Boolean.TRUE.equals(saved.get(bound.original))
                || !bound.uniquePile
                || (transitioned.contains(bound.original)
                    && (item.getOwnership() != TileItem.OWNERSHIP_OTHER
                        || !bound.original.equals(boundNonSelf.get(item))))
                || !bound.view.isTopLevel() || bound.instanceChunk == null
                || bound.original.world != currentWorld
                || tick < bound.tick || tick - bound.tick > 1
                || bound.despawn <= tick || bound.despawn > bound.original.despawn
                || (long) bound.visible - bound.original.visible
                    != (long) bound.despawn - bound.original.despawn) { continue; }
            int count = inventoryCount.applyAsInt(bound.original.id);
            if (count == Integer.MIN_VALUE) { continue; }
            Handoff witness = new Handoff(bound.original, item, bound.view, tick, count,
                bound.visible, bound.despawn, true);
            witness.sceneReload = true;
            witness.inferred = true;
            witness.transitionedWitness = transitioned.contains(bound.original);
            if (bound != null) { witness.instanceChunk = bound.instanceChunk; }
            handoffs.put(bound.original, witness);
            nextExpiry = Math.min(nextExpiry, bound.despawn);
        }
        remember(confirmed, currentWorld, tick);
        boundNonSelf.clear();
        boundTracked.clear();
        replayed.clear();
        ambiguous.clear();
        settleTicks = 0;
        late = false;
        replaying = true;
    }

    void remember(Map<TileItem, Tile> confirmed, int currentWorld, int tick)
    {
        remember(confirmed, currentWorld, tick, view -> true);
    }

    void remember(Map<TileItem, Tile> confirmed, int currentWorld, int tick, Predicate<WorldView> activeView)
    {
        if (world != currentWorld) { clear(); world = currentWorld; }
        expire(tick);
        if (confirmed.isEmpty()) { boundTracked.clear(); return; }
        boundTracked.keySet().removeIf(item -> !confirmed.containsKey(item));
        Set<Key> seen = null;
        for (Map.Entry<TileItem, Tile> entry : confirmed.entrySet())
        {
            TileItem item = entry.getKey();
            Key key = Key.replay(item, entry.getValue(), currentWorld, tick, item.getQuantity());
            if (key == null) { continue; }
            if (item.getOwnership() == TileItem.OWNERSHIP_SELF)
            {
                if (seen == null) { seen = new HashSet<>(); }
                if (!seen.add(key))
                {
                    if (saved.containsKey(key)) { saved.put(key, false); }
                }
                else if (saved.size() < DropTracker.MAX_TRACKED)
                {
                    saved.putIfAbsent(key, true);
                    nextExpiry = Math.min(nextExpiry, key.despawn);
                }
            }
            Bound prior = boundTracked.get(item);
            Key original = prior != null && prior.original.equals(key)
                && Boolean.TRUE.equals(saved.get(prior.original)) ? prior.original : savedKey(key);
            if (original != null) { bind(item, entry.getValue(), original, tick, activeView); }
        }
    }

    void spawned(TileItem item, Tile tile, int currentWorld, int tick)
    {
        if (world != currentWorld) { return; }
        recordHandoffSpawn(item, tile, currentWorld, tick);
        if (!replaying)
        {
            // OTHER is eligible only through a just-observed handoff, never
            // through a later same-place spawn.
            if (item.getOwnership() == TileItem.OWNERSHIP_OTHER) { return; }
            if (saved.isEmpty()) { return; }
            expire(tick);
            Key incoming = Key.replay(item, tile, currentWorld, tick, item.getQuantity());
            Key original = incoming == null ? null : savedKey(incoming);
            // Outside a scene replay window, an equal lifetime alone could
            // describe a different drop on the same tile. Only a one-tick
            // scene rebase is allowed to start a late settle.
            if (original == null || !original.sameTimerShift(incoming)
                || Math.abs((long) incoming.visible - original.visible) > 1
                || !original.allows(item, tick, transitioned.contains(original))) { return; }
            replayed.clear(); ambiguous.clear();
            settleTicks = 0;
            replaying = late = true;
        }
        Key key = Key.replay(item, tile, currentWorld, tick, item.getQuantity());
        if (key == null) { return; }
        Key original = savedKey(key);
        if (original == null || !original.sameTimerShift(key)
            || !original.allows(item, tick, transitioned.contains(original)))
        {
            return;
        }
        if (replayed.putIfAbsent(key, new Candidate(item, tile)) != null)
        {
            ambiguous.add(key);
            saved.put(key, false);
        }
    }

    List<Candidate> finish(int currentWorld, int tick)
    {
        return finish(currentWorld, tick, id -> Integer.MIN_VALUE);
    }

    List<Candidate> finish(int currentWorld, int tick, ToIntFunction<Integer> inventoryCount)
    {
        return finish(currentWorld, tick, inventoryCount, view -> true);
    }

    List<Candidate> finish(int currentWorld, int tick, ToIntFunction<Integer> inventoryCount,
        Predicate<WorldView> activeView)
    {
        expire(tick);
        List<Candidate> matches = settleHandoffs(currentWorld, tick, inventoryCount, activeView);
        if (!replaying) { return matches; }
        if (matches.isEmpty()) { matches = new ArrayList<>(); }
        // A scene can report LOGGED_IN before all of its item piles replay.
        // Wait briefly when some confirmed items have not reappeared yet.
        int expected = 0, found = 0;
        for (Map.Entry<Key, Boolean> entry : saved.entrySet())
        {
            if (!entry.getValue()) { continue; }
            expected++;
            if (replayed.containsKey(entry.getKey())) { found++; }
        }
        if ((late && settleTicks++ == 0) || (!late && found < expected && settleTicks++ < 3))
        {
            return matches;
        }
        if (world == currentWorld)
        {
            for (Map.Entry<Key, Candidate> entry : replayed.entrySet())
            {
                if (!ambiguous.contains(entry.getKey()) && Boolean.TRUE.equals(saved.get(entry.getKey())))
                {
                    Candidate candidate = entry.getValue();
                    Key current = Key.replay(candidate.item, candidate.tile, currentWorld, tick, candidate.item.getQuantity());
                    Key original = savedKey(entry.getKey());
                    if (current != null && entry.getKey().equals(current) && original != null
                        && original.sameTimerShift(current)
                        && original.allows(candidate.item, tick, transitioned.contains(original))
                        && candidate.tile.getItemLayer() != null
                        && candidate.tile.getItemLayer().getWorldView() != null
                        && activeView.test(candidate.tile.getItemLayer().getWorldView())
                        && (candidate.item.getOwnership() != TileItem.OWNERSHIP_OTHER
                            || uniqueGroundItem(candidate)))
                    {
                        matches.add(candidate);
                        bind(candidate.item, candidate.tile, original, tick, activeView);
                        Handoff witness = handoffs.get(original);
                        if (witness != null && witness.inferred && witness.candidate != null
                            && witness.candidate.item == candidate.item)
                        {
                            handoffs.remove(original);
                        }
                        if (candidate.item.getOwnership() != TileItem.OWNERSHIP_SELF)
                        {
                            boundNonSelf.put(candidate.item, original);
                        }
                    }
                    else
                    {
                        // An invalid candidate may be a different ground item.
                        // Drop its confirmation instead of attributing a later spawn.
                        removeSaved(entry.getKey());
                    }
                }
            }
        }
        replayed.clear();
        ambiguous.clear();
        replaying = false;
        late = false;
        return matches;
    }

    private void recordHandoffSpawn(TileItem item, Tile tile, int currentWorld, int tick)
    {
        if (handoffs.isEmpty()) { return; }
        ItemLayer layer = tile.getItemLayer();
        if (layer == null || layer.getWorldView() == null || tile.getWorldLocation() == null) { return; }
        List<Key> cancelForSelfReplay = new ArrayList<>();
        for (Map.Entry<Key, Handoff> entry : handoffs.entrySet())
        {
            Handoff handoff = entry.getValue();
            Key original = handoff.original;
            if (currentWorld != original.world || item.getId() != original.id
                || !sameHandoffView(handoff, layer.getWorldView())
                || !original.point.equals(tile.getWorldLocation())
                || (!handoff.sceneReload && tick > handoff.tick + 1
                    && (!handoff.requiresSceneReload || item.getOwnership() == TileItem.OWNERSHIP_OTHER))
                || tick >= original.despawn
                || (handoff.requiresSceneReload && handoff.sceneReload && !replaying
                    && item.getOwnership() == TileItem.OWNERSHIP_OTHER)) { continue; }
            if (item.getOwnership() == TileItem.OWNERSHIP_SELF
                || item.getOwnership() == TileItem.OWNERSHIP_NONE)
            {
                if (handoff.candidate == null) { cancelForSelfReplay.add(entry.getKey()); }
                else { handoff.ambiguous = true; }
                continue;
            }
            if (handoff.candidate != null || item.getOwnership() != TileItem.OWNERSHIP_OTHER
                || item.getQuantity() != original.quantity || !handoff.matchesTimers(item)
                || (handoff.transitionedWitness && !Objects.equals(handoff.instanceChunk,
                    instanceChunk(tile, layer.getWorldView()))))
            {
                handoff.ambiguous = true;
            }
            else
            {
                handoff.candidate = new Candidate(item, tile);
                handoff.candidateTick = tick;
                if (handoff.requiresSceneReload && !handoff.sceneReload)
                {
                    handoff.preloadUnique = uniqueGroundItem(handoff.candidate);
                    if (!handoff.preloadUnique) { handoff.ambiguous = true; }
                }
            }
        }
        for (Key key : cancelForSelfReplay)
        {
            handoffs.remove(key);
        }
    }

    private List<Candidate> settleHandoffs(int currentWorld, int tick,
        ToIntFunction<Integer> inventoryCount, Predicate<WorldView> activeView)
    {
        if (handoffs.isEmpty()) { return java.util.Collections.emptyList(); }
        List<Candidate> matches = new ArrayList<>();
        for (Map.Entry<Key, Handoff> entry : new ArrayList<>(handoffs.entrySet()))
        {
            Handoff handoff = entry.getValue();
            if (inventoryCount.applyAsInt(handoff.original.id) > handoff.inventoryCount)
            {
                handoffs.remove(entry.getKey());
                removeSaved(entry.getKey());
                continue;
            }
            if (!handoff.sceneReload && handoff.candidate != null && handoff.transitionedWitness
                && handoff.candidate.item.getDespawnTime() > handoff.original.despawn
                && tick > handoff.candidateTick + 1)
            {
                handoffs.remove(entry.getKey());
                removeSaved(entry.getKey());
                continue;
            }
            if (tick <= handoff.tick && !handoff.sceneReload) { continue; }
            if (handoff.requiresSceneReload && !handoff.sceneReload) { continue; }
            if (handoff.sceneReload && replaying && settleTicks < 3) { continue; }
            if (handoff.requiresSceneReload && handoff.candidate == null && !handoff.ambiguous) { continue; }
            if (handoff.candidate == null && (handoff.sceneReload ? replaying && settleTicks < 3
                : tick <= handoff.tick + 1)) { continue; }
            handoffs.remove(entry.getKey());
            Candidate candidate = handoff.candidate;
            Key current = candidate == null ? null : Key.replay(candidate.item, candidate.tile,
                currentWorld, tick, candidate.item.getQuantity());
            if ((handoff.sceneReload ? replaying && settleTicks <= 3 : tick <= handoff.tick + 1)
                && !handoff.ambiguous && (!handoff.requiresSceneReload || handoff.sceneReload)
                && current != null && entry.getKey().equals(current)
                && candidate.item.getOwnership() == TileItem.OWNERSHIP_OTHER
                && tick >= handoff.original.visible
                && handoff.matchesTimers(candidate.item)
                && candidate.item.getDespawnTime() <= Math.min(handoff.original.despawn, handoff.oldDespawn)
                && candidate.tile.getItemLayer() != null
                && sameHandoffView(handoff, candidate.tile.getItemLayer().getWorldView())
                && (!(handoff.inferred || handoff.transitionedWitness) || Objects.equals(handoff.instanceChunk,
                    instanceChunk(candidate.tile, candidate.tile.getItemLayer().getWorldView())))
                && activeView.test(candidate.tile.getItemLayer().getWorldView())
                && inventoryCount.applyAsInt(current.id) <= handoff.inventoryCount
                && uniqueGroundItem(candidate))
            {
                removeSaved(entry.getKey());
                saved.put(current, true);
                nextExpiry = Math.min(nextExpiry, current.despawn);
                transitioned.add(current);
                boundNonSelf.put(candidate.item, current);
                matches.add(candidate);
                replayed.remove(entry.getKey());
            }
            else
            {
                removeSaved(entry.getKey());
            }
        }
        return matches;
    }

    private static boolean uniqueGroundItem(Candidate candidate)
    {
        return uniqueGroundItem(candidate.item, candidate.tile);
    }

    private static boolean uniqueGroundItem(TileItem candidate, Tile tile)
    {
        List<TileItem> items = tile.getGroundItems();
        if (items == null) { return false; }
        int sameId = 0;
        for (TileItem item : items)
        {
            if (item.getId() == candidate.getId())
            {
                if (item != candidate || ++sameId > 1) { return false; }
            }
        }
        return sameId == 1;
    }

    private static Integer instanceChunk(Tile tile, WorldView view)
    {
        if (!view.isInstance()) { return Integer.MIN_VALUE; }
        Point scene = tile.getSceneLocation();
        int[][][] chunks = view.getInstanceTemplateChunks();
        if (scene == null || chunks == null || tile.getPlane() < 0 || tile.getPlane() >= chunks.length
            || chunks[tile.getPlane()] == null) { return null; }
        int x = scene.getX() / 8, y = scene.getY() / 8;
        if (x < 0 || x >= chunks[tile.getPlane()].length
            || chunks[tile.getPlane()][x] == null || y < 0
            || y >= chunks[tile.getPlane()][x].length) { return null; }
        int chunk = chunks[tile.getPlane()][x][y];
        return chunk == -1 ? null : chunk;
    }

    private static boolean sameHandoffView(Handoff handoff, WorldView view)
    {
        return view != null && (handoff.sceneReload || handoff.requiresSceneReload
            ? view.getId() == handoff.original.view : view == handoff.view);
    }

    private void removeSaved(Key key)
    {
        saved.remove(key);
        transitioned.remove(key);
        boundTracked.values().removeIf(bound -> bound.original.equals(key));
    }

    private void bind(TileItem item, Tile tile, Key original, int tick, Predicate<WorldView> activeView)
    {
        ItemLayer layer = tile.getItemLayer();
        WorldView view = layer == null ? null : layer.getWorldView();
        if (view == null || !activeView.test(view) || item.getDespawnTime() <= tick) { return; }
        Bound prior = boundTracked.get(item);
        if (prior == null || prior.view != view || !prior.original.equals(original))
        {
            prior = new Bound(original, view);
            boundTracked.put(item, prior);
        }
        prior.tick = tick;
        prior.visible = item.getVisibleTime();
        prior.despawn = item.getDespawnTime();
        prior.uniquePile = uniqueGroundItem(item, tile);
        prior.instanceChunk = instanceChunk(tile, view);
    }

    void quantityChanged(TileItem item, Tile tile, int oldQuantity, int currentWorld, int tick)
    {
        ItemLayer layer = tile.getItemLayer();
        if (layer != null && layer.getWorldView() != null && tile.getWorldLocation() != null)
        {
            for (Map.Entry<Key, Handoff> entry : new ArrayList<>(handoffs.entrySet()))
            {
                Key original = entry.getKey();
                if (entry.getValue().inferred && original.world == currentWorld
                    && original.view == layer.getWorldView().getId()
                    && original.id == item.getId() && original.point.equals(tile.getWorldLocation()))
                {
                    handoffs.remove(original);
                    removeSaved(original);
                }
            }
        }
        Bound bound = boundTracked.remove(item);
        if (bound != null && saved.containsKey(bound.original)) { removeSaved(bound.original); }
        handoffs.values().removeIf(handoff -> {
            if (handoff.oldItem != item && (handoff.candidate == null || handoff.candidate.item != item)) { return false; }
            removeSaved(handoff.original);
            return true;
        });
        Key nonSelfKey = boundNonSelf.remove(item);
        if (nonSelfKey != null && saved.containsKey(nonSelfKey))
        {
            removeSaved(nonSelfKey);
        }
        Key old = Key.of(item, tile, currentWorld, tick, oldQuantity);
        if (old != null && (!replaying || (replayed.containsKey(old) && replayed.get(old).item == item)))
        {
            if (saved.containsKey(old)) { removeSaved(old); }
            replayed.remove(old);
        }
    }

    void despawned(TileItem item, Tile tile, int currentWorld, int tick)
    {
        despawned(item, tile, currentWorld, tick, false, Integer.MIN_VALUE);
    }

    void despawned(TileItem item, Tile tile, int currentWorld, int tick, boolean confirmed, int inventoryCount)
    {
        despawned(item, tile, currentWorld, tick, confirmed, confirmed, confirmed, inventoryCount);
    }

    void despawned(TileItem item, Tile tile, int currentWorld, int tick,
        boolean confirmed, boolean handoffEligible, int inventoryCount)
    {
        despawned(item, tile, currentWorld, tick, confirmed, handoffEligible, handoffEligible, inventoryCount);
    }

    void despawned(TileItem item, Tile tile, int currentWorld, int tick,
        boolean confirmed, boolean loggedIn, boolean activeView, int inventoryCount)
    {
        handoffs.values().removeIf(handoff -> {
            if (handoff.candidate == null || handoff.candidate.item != item) { return false; }
            removeSaved(handoff.original);
            return true;
        });
        Bound bound = boundTracked.remove(item);
        Key nonSelfKey = boundNonSelf.remove(item);
        Key eventKey = Key.replay(item, tile, currentWorld, tick, item.getQuantity());
        Key original = bound != null ? bound.original
            : nonSelfKey != null ? nonSelfKey : eventKey == null ? null : savedKey(eventKey);
        int oldVisible = bound == null ? item.getVisibleTime() : bound.visible;
        int oldDespawn = bound == null ? item.getDespawnTime() : bound.despawn;
        WorldView oldView = bound != null ? bound.view
            : tile.getItemLayer() == null ? null : tile.getItemLayer().getWorldView();
        boolean boundFresh = bound != null && tick >= bound.tick && tick - bound.tick <= 1;
        boolean oldPileUnique = activeView ? oldPileUnambiguous(item, tile)
            : boundFresh && bound.uniquePile;
        boolean pairShifted = original != null
            && (long) oldVisible - original.visible == (long) oldDespawn - original.despawn;
        boolean sceneOnly = !activeView;
        boolean continuedOther = original != null && transitioned.contains(original)
            && sceneOnly && item.getOwnership() == TileItem.OWNERSHIP_OTHER
            && nonSelfKey != null && nonSelfKey.equals(original) && boundFresh
            && bound.instanceChunk != null;
        boolean handoff = confirmed && loggedIn && !replaying && original != null
            && Boolean.TRUE.equals(saved.get(original))
            && (!transitioned.contains(original) || continuedOther)
            && item.getId() == original.id && item.getQuantity() == original.quantity
            && currentWorld == original.world && oldView != null && oldView.getId() == original.view
            && oldDespawn > tick && oldDespawn <= original.despawn
            && tick >= original.visible && pairShifted && oldPileUnique
            && (!sceneOnly || (boundFresh && oldView.isTopLevel()))
            && inventoryCount != Integer.MIN_VALUE;
        if (handoff)
        {
            Handoff witness = new Handoff(original, item, oldView, tick, inventoryCount,
                oldVisible, oldDespawn, sceneOnly);
            witness.transitionedWitness = continuedOther;
            if (bound != null) { witness.instanceChunk = bound.instanceChunk; }
            handoffs.put(original, witness);
            nextExpiry = Math.min(nextExpiry, oldDespawn);
        }
        else if (nonSelfKey != null && saved.containsKey(nonSelfKey))
        {
            removeSaved(nonSelfKey);
        }
        else if (confirmed && !replaying && original != null
            && (activeView || item.getOwnership() == TileItem.OWNERSHIP_OTHER)
            && item.getOwnership() != TileItem.OWNERSHIP_NONE) { removeSaved(original); }
        Key key = Key.of(item, tile, currentWorld, tick, item.getQuantity());
        if (!handoff && key != null && (activeView && !replaying
            || (replayed.containsKey(key) && replayed.get(key).item == item)))
        {
            if (saved.containsKey(key)) { removeSaved(key); }
            replayed.remove(key);
        }
        replayed.values().removeIf(candidate -> candidate.item == item);
    }

    private static boolean oldPileUnambiguous(TileItem oldItem, Tile tile)
    {
        List<TileItem> items = tile.getGroundItems();
        if (items == null) { return false; }
        for (TileItem item : items)
        {
            if (item.getId() == oldItem.getId() && item != oldItem) { return false; }
        }
        return true;
    }

    void unloaded(WorldView view) { unloaded(view, false); }

    void unloaded(WorldView view, boolean sceneReload)
    {
        handoffs.entrySet().removeIf(entry -> {
            Handoff handoff = entry.getValue();
            if (handoff.original.view != view.getId()) { return false; }
            if (sceneReload && view.isTopLevel() && handoff.sceneReload && handoff.candidate == null)
            {
                return false;
            }
            removeSaved(entry.getKey());
            return true;
        });
        // A top-level scene rebuild can unload its view before replaying the
        // same ground items. Keep only scalar confirmations for that rebuild.
        if (!sceneReload || !replaying || !view.isTopLevel())
        {
            saved.keySet().removeIf(key -> key.view == view.getId());
            transitioned.removeIf(key -> key.view == view.getId());
        }
        boundNonSelf.entrySet().removeIf(entry -> entry.getValue().view == view.getId());
        boundTracked.entrySet().removeIf(entry -> entry.getValue().original.view == view.getId());
        ambiguous.removeIf(key -> key.view == view.getId());
        replayed.values().removeIf(candidate -> candidate.tile.getItemLayer() == null
            || candidate.tile.getItemLayer().getWorldView() == null
            || candidate.tile.getItemLayer().getWorldView().getId() == view.getId());
    }

    void clear()
    {
        saved.clear(); replayed.clear(); ambiguous.clear(); boundNonSelf.clear(); boundTracked.clear(); handoffs.clear(); transitioned.clear(); replaying = late = false;
        world = -1; nextExpiry = Integer.MAX_VALUE;
    }

    int savedCount() { return saved.size(); }
    boolean isReplaying() { return replaying; }

    private Key savedKey(Key replay)
    {
        // Equality omits timers, while the stored key retains the original
        // deadline and timer pair across repeated loading lines.
        for (Map.Entry<Key, Boolean> entry : saved.entrySet())
        {
            if (entry.getKey().equals(replay) && entry.getValue()) { return entry.getKey(); }
        }
        return null;
    }

    void expire(int tick)
    {
        if (tick < nextExpiry) { return; }
        saved.keySet().removeIf(key -> key.despawn <= tick);
        transitioned.removeIf(key -> key.despawn <= tick);
        handoffs.entrySet().removeIf(entry -> {
            if (Math.min(entry.getKey().despawn, entry.getValue().oldDespawn) > tick) { return false; }
            removeSaved(entry.getKey());
            return true;
        });
        boundNonSelf.values().removeIf(key -> key.despawn <= tick);
        boundTracked.values().removeIf(bound -> bound.original.despawn <= tick);
        nextExpiry = Integer.MAX_VALUE;
        for (Key key : saved.keySet()) { nextExpiry = Math.min(nextExpiry, key.despawn); }
        for (Handoff handoff : handoffs.values()) { nextExpiry = Math.min(nextExpiry, handoff.oldDespawn); }
    }

    static final class Candidate
    {
        final TileItem item;
        final Tile tile;
        Candidate(TileItem item, Tile tile) { this.item = item; this.tile = tile; }
    }

    private static final class Bound
    {
        final Key original;
        final WorldView view;
        int tick, visible, despawn;
        boolean uniquePile;
        Integer instanceChunk;

        Bound(Key original, WorldView view)
        {
            this.original = original;
            this.view = view;
        }
    }

    private static final class Handoff
    {
        final Key original;
        TileItem oldItem;
        WorldView view;
        final int tick, inventoryCount;
        final int oldVisible, oldDespawn;
        final boolean requiresSceneReload, topLevel;
        int preloadVisible = -1, preloadDespawn = -1, candidateTick = -1;
        Integer instanceChunk;
        Candidate candidate;
        boolean ambiguous, sceneReload, preloadUnique, inferred, transitionedWitness;

        Handoff(Key original, TileItem oldItem, WorldView view, int tick, int inventoryCount,
            int oldVisible, int oldDespawn, boolean requiresSceneReload)
        {
            this.original = original;
            this.oldItem = oldItem;
            this.view = view;
            this.tick = tick;
            this.inventoryCount = inventoryCount;
            this.oldVisible = oldVisible;
            this.oldDespawn = oldDespawn;
            this.requiresSceneReload = requiresSceneReload;
            this.topLevel = view.isTopLevel();
            if (requiresSceneReload) { this.oldItem = null; this.view = null; }
        }

        boolean matchesTimers(TileItem item)
        {
            // A previously accepted OTHER item can briefly report a one-tick
            // later expiry before a load. The replay must return within the
            // original deadline; this preload observation cannot extend it.
            boolean transitionedPreload = transitionedWitness && requiresSceneReload && !sceneReload
                && oldDespawn == original.despawn
                && (long) item.getDespawnTime() - oldDespawn == 1;
            if (item.getDespawnTime() > original.despawn && !transitionedPreload) { return false; }
            if (inferred)
            {
                long expiryShift = (long) item.getDespawnTime() - oldDespawn;
                return (expiryShift == 0 || (!transitionedWitness && expiryShift == -1))
                    && item.getVisibleTime() >= oldVisible;
            }
            if (sceneReload && preloadDespawn > 0)
            {
                long visibleShift = (long) item.getVisibleTime() - preloadVisible;
                long despawnShift = (long) item.getDespawnTime() - preloadDespawn;
                return visibleShift == despawnShift && Math.abs(visibleShift) <= 1;
            }
            if (requiresSceneReload && !sceneReload)
            {
                return Math.abs((long) item.getDespawnTime() - oldDespawn) <= 1
                    && item.getVisibleTime() >= oldVisible;
            }
            return (requiresSceneReload
                ? Math.abs((long) item.getDespawnTime() - oldDespawn) <= 1
                : item.getDespawnTime() == oldDespawn)
                && item.getVisibleTime() >= oldVisible;
        }
    }

    private static final class Key
    {
        final int world, view, id, quantity, visible, despawn;
        final WorldPoint point;

        private Key(int world, int view, int id, int quantity, int visible, int despawn, WorldPoint point)
        {
            this.world = world; this.view = view; this.id = id; this.quantity = quantity;
            this.visible = visible; this.despawn = despawn; this.point = point;
        }

        static Key of(TileItem item, Tile tile, int world, int tick, int quantity)
        {
            return of(item, tile, world, tick, quantity, false);
        }

        static Key replay(TileItem item, Tile tile, int world, int tick, int quantity)
        {
            return of(item, tile, world, tick, quantity, true);
        }

        private static Key of(TileItem item, Tile tile, int world, int tick, int quantity, boolean publicCandidate)
        {
            ItemLayer layer = tile.getItemLayer();
            int ownership = item.getOwnership();
            if (ownership != TileItem.OWNERSHIP_SELF
                && (!publicCandidate || (ownership != TileItem.OWNERSHIP_NONE
                    && ownership != TileItem.OWNERSHIP_OTHER))) { return null; }
            if (quantity <= 0 || item.getVisibleTime() <= 0
                || item.getDespawnTime() <= tick || layer == null || layer.getWorldView() == null
                || tile.getWorldLocation() == null) { return null; }
            return new Key(world, layer.getWorldView().getId(), item.getId(), quantity,
                item.getVisibleTime(), item.getDespawnTime(), tile.getWorldLocation());
        }

        boolean sameTimerShift(Key other)
        {
            long shift = (long) other.despawn - despawn;
            return shift >= -1 && shift <= 0 && (long) other.visible - visible == shift;
        }

        boolean allows(TileItem item, int tick, boolean transitioned)
        {
            // A confirmed own drop may be reported as unowned once public.
            // Never use public ownership alone as evidence of a new drop.
            return item.getOwnership() == TileItem.OWNERSHIP_SELF
                || (item.getOwnership() == TileItem.OWNERSHIP_NONE && tick >= visible)
                || (item.getOwnership() == TileItem.OWNERSHIP_OTHER && transitioned);
        }

        @Override public boolean equals(Object other)
        {
            if (!(other instanceof Key)) { return false; }
            Key key = (Key) other;
            return world == key.world && view == key.view && id == key.id && quantity == key.quantity
                && point.equals(key.point);
        }

        @Override public int hashCode()
        {
            return Objects.hash(world, view, id, quantity, point);
        }
    }
}
