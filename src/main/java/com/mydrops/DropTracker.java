package com.mydrops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/** Correlates a local Drop intent, inventory loss and a confirmed ground change. */
final class DropTracker
{
    static final int WINDOW = 3, MAX_PENDING = 28, MAX_TRACKED = 256;
    private final Map<Integer, Intent> pending = new HashMap<>();
    private final Map<TileItem, Tile> tracked = new IdentityHashMap<>();

    void intent(int id, int count, int quantity, int tick, WorldView view, WorldPoint location)
    {
        if (count <= 0 || (pending.size() >= MAX_PENDING && !pending.containsKey(id))) { return; }
        Intent intent = pending.get(id);
        if (intent == null || tick > intent.expires || intent.view != view || !intent.location.equals(location))
        {
            intent = new Intent(count, view, location);
            intent.expires = tick + WINDOW;
            pending.put(id, intent);
        }
        intent.requested = Math.min((long) intent.before, intent.requested + quantity);
    }

    void ground(TileItem item, Tile tile, int added, int tick, WorldPoint playerLocation)
    {
        Intent intent = pending.get(item.getId());
        if (intent == null || tick > intent.expires || added <= 0
            || item.getOwnership() != TileItem.OWNERSHIP_SELF || tile.getItemLayer() == null
            || tile.getItemLayer().getWorldView() != intent.view
            || (!tile.getWorldLocation().equals(intent.location) && !tile.getWorldLocation().equals(playerLocation)))
        {
            return;
        }
        if (intent.candidates.size() < MAX_PENDING)
        {
            intent.candidates.add(new Candidate(item, tile, added));
        }
        else { pending.remove(item.getId()); }
    }

    void confirm(int tick, ToIntFunction<Integer> inventoryCount)
    {
        Iterator<Map.Entry<Integer, Intent>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<Integer, Intent> entry = iterator.next();
            Intent intent = entry.getValue();
            // Expired intents cannot be revived by an unrelated later inventory reduction.
            if (tick > intent.expires) { iterator.remove(); continue; }
            int lost = Math.max(0, intent.before - inventoryCount.applyAsInt(entry.getKey()));
            long observed = 0;
            for (Candidate candidate : intent.candidates) { observed += candidate.added; }
            // Fail closed when simultaneous loot could be mistaken for the dropped item.
            if (lost <= 0) { continue; }
            if (observed == lost && lost <= intent.requested)
            {
                for (Candidate candidate : intent.candidates)
                {
                    if (tracked.size() < MAX_TRACKED || tracked.containsKey(candidate.item))
                    {
                        tracked.put(candidate.item, candidate.tile);
                    }
                }
            }
            // A successful or ambiguous inventory change finishes this short-lived correlation.
            iterator.remove();
        }
    }

    Map<TileItem, Tile> tracked() { return tracked; }
    void cancel(int id) { pending.remove(id); }

    void remove(TileItem item)
    {
        tracked.remove(item);
        for (Intent intent : pending.values()) { intent.candidates.removeIf(c -> c.item == item); }
    }

    void unload(WorldView view)
    {
        tracked.values().removeIf(tile -> tile.getItemLayer() == null || tile.getItemLayer().getWorldView() == view);
        pending.values().removeIf(intent -> intent.view == view);
    }

    void clear() { pending.clear(); tracked.clear(); }

    private static final class Intent
    {
        int before, expires;
        long requested;
        final WorldView view;
        final WorldPoint location;
        final List<Candidate> candidates = new ArrayList<>();
        Intent(int before, WorldView view, WorldPoint location)
        {
            this.before = before; this.view = view; this.location = location;
        }
    }

    private static final class Candidate
    {
        final TileItem item;
        final Tile tile;
        final int added;
        Candidate(TileItem item, Tile tile, int added) { this.item = item; this.tile = tile; this.added = added; }
    }
}
