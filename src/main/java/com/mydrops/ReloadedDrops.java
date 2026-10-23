package com.mydrops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.ItemLayer;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/** Rebinds confirmed drops to scene-reloaded items only when their full lifetime matches. */
final class ReloadedDrops
{
    private final Map<Key, Boolean> saved = new HashMap<>();
    private final Map<Key, Candidate> replayed = new HashMap<>();
    private final Set<Key> ambiguous = new HashSet<>();
    private int world = -1;
    private boolean replaying;

    void loading(Map<TileItem, Tile> confirmed, int currentWorld, int tick)
    {
        if (world != currentWorld) { clear(); world = currentWorld; }
        prune(tick);
        Set<Key> seen = new HashSet<>();
        for (Map.Entry<TileItem, Tile> entry : confirmed.entrySet())
        {
            Key key = Key.of(entry.getKey(), entry.getValue(), currentWorld, tick, entry.getKey().getQuantity());
            if (key == null) { continue; }
            if (!seen.add(key))
            {
                if (saved.containsKey(key)) { saved.put(key, false); }
            }
            else if (saved.size() < DropTracker.MAX_TRACKED) { saved.putIfAbsent(key, true); }
        }
        replayed.clear();
        ambiguous.clear();
        replaying = true;
    }

    void spawned(TileItem item, Tile tile, int currentWorld, int tick)
    {
        if (!replaying || world != currentWorld) { return; }
        Key key = Key.of(item, tile, currentWorld, tick, item.getQuantity());
        if (key == null || !Boolean.TRUE.equals(saved.get(key))) { return; }
        if (replayed.putIfAbsent(key, new Candidate(item, tile)) != null)
        {
            ambiguous.add(key);
            saved.put(key, false);
        }
    }

    List<Candidate> finish(int currentWorld, int tick)
    {
        if (!replaying) { return Collections.emptyList(); }
        prune(tick);
        List<Candidate> matches = new ArrayList<>();
        if (world == currentWorld)
        {
            for (Map.Entry<Key, Candidate> entry : replayed.entrySet())
            {
                if (!ambiguous.contains(entry.getKey()) && Boolean.TRUE.equals(saved.get(entry.getKey())))
                {
                    Candidate candidate = entry.getValue();
                    Key current = Key.of(candidate.item, candidate.tile, currentWorld, tick, candidate.item.getQuantity());
                    if (entry.getKey().equals(current)) { matches.add(candidate); }
                    else { saved.remove(entry.getKey()); }
                }
            }
        }
        replayed.clear();
        ambiguous.clear();
        replaying = false;
        return matches;
    }

    void quantityChanged(TileItem item, Tile tile, int oldQuantity, int currentWorld, int tick)
    {
        Key old = Key.of(item, tile, currentWorld, tick, oldQuantity);
        if (old != null && (!replaying || (replayed.containsKey(old) && replayed.get(old).item == item)))
        {
            saved.remove(old); replayed.remove(old);
        }
    }

    void despawned(TileItem item, Tile tile, int currentWorld, int tick)
    {
        Key key = Key.of(item, tile, currentWorld, tick, item.getQuantity());
        if (key != null && (!replaying || (replayed.containsKey(key) && replayed.get(key).item == item)))
        {
            saved.remove(key); replayed.remove(key);
        }
        replayed.values().removeIf(candidate -> candidate.item == item);
    }

    void unloaded(WorldView view)
    {
        saved.keySet().removeIf(key -> key.view == view.getId());
        ambiguous.removeIf(key -> key.view == view.getId());
        replayed.values().removeIf(candidate -> candidate.tile.getItemLayer() == null
            || candidate.tile.getItemLayer().getWorldView() == null
            || candidate.tile.getItemLayer().getWorldView().getId() == view.getId());
    }

    void clear()
    {
        saved.clear(); replayed.clear(); ambiguous.clear(); replaying = false; world = -1;
    }

    int savedCount() { return saved.size(); }

    private void prune(int tick) { saved.keySet().removeIf(key -> key.despawn <= tick); }

    static final class Candidate
    {
        final TileItem item;
        final Tile tile;
        Candidate(TileItem item, Tile tile) { this.item = item; this.tile = tile; }
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
            ItemLayer layer = tile.getItemLayer();
            if (item.getOwnership() != TileItem.OWNERSHIP_SELF || quantity <= 0 || item.getVisibleTime() <= 0
                || item.getDespawnTime() <= tick || layer == null || layer.getWorldView() == null
                || tile.getWorldLocation() == null) { return null; }
            return new Key(world, layer.getWorldView().getId(), item.getId(), quantity,
                item.getVisibleTime(), item.getDespawnTime(), tile.getWorldLocation());
        }

        @Override public boolean equals(Object other)
        {
            if (!(other instanceof Key)) { return false; }
            Key key = (Key) other;
            return world == key.world && view == key.view && id == key.id && quantity == key.quantity
                && visible == key.visible && despawn == key.despawn && point.equals(key.point);
        }

        @Override public int hashCode()
        {
            return Objects.hash(world, view, id, quantity, visible, despawn, point);
        }
    }
}
