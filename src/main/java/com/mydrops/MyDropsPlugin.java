package com.mydrops;

import com.google.inject.Provides;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(name = "My Drops", description = "Color your deliberate drops and optional GIM group items", tags = {"ground", "supplies", "food", "potions", "ammunition", "gim"})
public class MyDropsPlugin extends Plugin
{
    @Inject Client client;
    @Inject ItemManager itemManager;
    @Inject MyDropsConfig config;
    @Inject OverlayManager overlays;
    @Inject MyDropsOverlay overlay;

    final DropTracker tracker = new DropTracker();
    final ReloadedDrops reload = new ReloadedDrops();
    final ResourceFilter filter = new ResourceFilter();
    final Map<TileItem, Label> labels = new IdentityHashMap<>();
    final Map<TileItem, Tile> groupItems = new LinkedHashMap<>();

    @Provides MyDropsConfig provideConfig(ConfigManager manager) { return manager.getConfig(MyDropsConfig.class); }

    @Override protected void startUp()
    {
        reset();
        filter.setExtras(config.extraItems());
        overlays.add(overlay);
    }

    @Override protected void shutDown() { overlays.remove(overlay); reset(); }

    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (MyDropsConfig.GROUP.equals(event.getGroup()) && "extraItems".equals(event.getKey()))
        {
            filter.setExtras(config.extraItems());
        }
    }

    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event)
    {
        Player player = client.getLocalPlayer();
        if (player == null || event.isConsumed() || !event.isItemOp()
            || event.getParam1() >>> 16 != InterfaceID.INVENTORY) { return; }
        if (!"Drop".equalsIgnoreCase(Text.removeTags(event.getMenuOption())))
        {
            tracker.cancel(event.getItemId());
            return;
        }
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        Item slot = inventory == null ? null : inventory.getItem(event.getParam0());
        if (slot == null || slot.getId() != event.getItemId()) { return; }
        tracker.intent(slot.getId(), inventory.count(slot.getId()), slot.getQuantity(), client.getTickCount(), player.getWorldView(), player.getWorldLocation());
    }

    @Subscribe public void onItemSpawned(ItemSpawned event)
    {
        ground(event.getItem(), event.getTile(), event.getItem().getQuantity());
        reload.spawned(event.getItem(), event.getTile(), client.getWorld(), client.getTickCount());
    }

    @Subscribe public void onItemQuantityChanged(ItemQuantityChanged event)
    {
        reload.quantityChanged(event.getItem(), event.getTile(), event.getOldQuantity(), client.getWorld(), client.getTickCount());
        ground(event.getItem(), event.getTile(), event.getNewQuantity() - event.getOldQuantity());
        Label label = labels.get(event.getItem());
        if (label != null) { label.quantity(event.getNewQuantity()); }
    }

    private void ground(TileItem item, Tile tile, int added)
    {
        if (item.getOwnership() == TileItem.OWNERSHIP_GROUP && item.getQuantity() > 0)
        {
            if (!groupItems.containsKey(item) && groupItems.size() >= DropTracker.MAX_TRACKED)
            {
                TileItem oldest = groupItems.keySet().iterator().next();
                groupItems.remove(oldest);
                if (!tracker.tracked().containsKey(oldest)) { labels.remove(oldest); }
            }
            groupItems.put(item, tile);
            cacheLabel(item);
        }
        Player player = client.getLocalPlayer();
        if (player != null) { tracker.ground(item, tile, added, client.getTickCount(), player.getWorldLocation()); }
    }

    @Subscribe public void onItemDespawned(ItemDespawned event)
    {
        reload.despawned(event.getItem(), event.getTile(), client.getWorld(), client.getTickCount());
        tracker.remove(event.getItem());
        groupItems.remove(event.getItem());
        labels.remove(event.getItem());
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) { return; }
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            for (ReloadedDrops.Candidate candidate : reload.finish(client.getWorld(), client.getTickCount()))
            {
                tracker.restore(candidate.item, candidate.tile);
            }
        }
        tracker.confirm(client.getTickCount(), inventory::count);
        for (TileItem item : tracker.tracked().keySet())
        {
            cacheLabel(item);
        }
    }

    private void cacheLabel(TileItem item)
    {
        if (labels.containsKey(item)) { return; }
        ItemComposition definition = itemManager.getItemComposition(item.getId());
        ItemComposition base = definition.getNote() == -1 ? definition : itemManager.getItemComposition(definition.getLinkedNoteId());
        Label label = new Label(definition.getName(), base.getName(), ResourceFilter.classify(base));
        label.quantity(item.getQuantity());
        labels.put(item, label);
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOADING)
        {
            reload.loading(tracker.tracked(), client.getWorld(), client.getTickCount());
            clearMarks();
        }
        else if (event.getGameState() != GameState.LOGGED_IN) { reset(); }
    }

    @Subscribe public void onWorldViewUnloaded(WorldViewUnloaded event)
    {
        tracker.unload(event.getWorldView());
        reload.unloaded(event.getWorldView());
        groupItems.values().removeIf(tile -> tile.getItemLayer() == null || tile.getItemLayer().getWorldView() == event.getWorldView());
        labels.keySet().removeIf(item -> !tracker.tracked().containsKey(item) && !groupItems.containsKey(item));
    }

    private void clearMarks() { tracker.clear(); groupItems.clear(); labels.clear(); }
    private void reset() { clearMarks(); reload.clear(); }

    static final class Label
    {
        final String name, filterName;
        final int categories;
        String text;
        Label(String name, String filterName, int categories) { this.name = name; this.filterName = filterName.toLowerCase(Locale.ROOT); this.categories = categories; }
        void quantity(int amount) { text = amount > 1 ? name + " (" + amount + ")" : name; }
    }
}
