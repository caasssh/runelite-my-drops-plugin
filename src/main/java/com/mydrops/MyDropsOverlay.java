package com.mydrops;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

final class MyDropsOverlay extends Overlay
{
    private final Client client;
    private final MyDropsPlugin plugin;
    private final MyDropsConfig config;
    private final ModelOutlineRenderer outlines;
    private final Map<Tile, Integer> rows = new HashMap<>();

    @Inject MyDropsOverlay(Client client, MyDropsPlugin plugin, MyDropsConfig config, ModelOutlineRenderer outlines)
    {
        this.client = client; this.plugin = plugin; this.config = config; this.outlines = outlines;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (client.getLocalPlayer() == null) { return null; }
        rows.clear();
        boolean outline = config.outline(), fill = config.tileFill(), text = config.text();
        if (!outline && !fill && !text) { return null; }
        Color lineColor = config.outlineColor(), fillColor = config.fillColor();
        Color textColor = config.textColor(), background = config.backgroundColor();
        Color groupColor = config.groupColor();
        boolean groupEnabled = config.groupItems();
        Color groupFill = new Color(groupColor.getRed(), groupColor.getGreen(), groupColor.getBlue(), groupColor.getAlpha() * fillColor.getAlpha() / 255);
        FontMetrics metrics = graphics.getFontMetrics();
        boolean resourcesOnly = config.resourcesOnly();
        int mask = (config.food() ? ResourceFilter.FOOD : 0) | (config.potions() ? ResourceFilter.POTIONS : 0)
            | (config.ammunition() ? ResourceFilter.AMMO : 0) | (config.runes() ? ResourceFilter.RUNES : 0);
        int width = config.outlineWidth();
        Color shadow = new Color(0, 0, 0, textColor.getAlpha());
        Color groupShadow = new Color(0, 0, 0, groupColor.getAlpha());
        // Own drops render first, so their tile fill wins on a mixed tile.
        for (int pass = 0; pass < 2; pass++)
        {
            boolean group = pass == 1;
            if (group && (!groupEnabled || groupColor.getAlpha() == 0)) { continue; }
            Color itemLine = group ? groupColor : lineColor, itemFill = group ? groupFill : fillColor;
            Color itemText = group ? groupColor : textColor, itemShadow = group ? groupShadow : shadow;
            Map<TileItem, Tile> items = group ? plugin.groupItems : plugin.tracker.tracked();
            for (Map.Entry<TileItem, Tile> entry : items.entrySet())
            {
                TileItem item = entry.getKey();
                if (group && (item.getOwnership() != TileItem.OWNERSHIP_GROUP || plugin.tracker.tracked().containsKey(item))) { continue; }
                Tile tile = entry.getValue();
                MyDropsPlugin.Label label = plugin.labels.get(item);
                if (label == null || !plugin.filter.matches(label.filterName, label.categories, resourcesOnly, mask)) { continue; }
                ItemLayer layer = tile.getItemLayer();
                if (layer == null || layer.getWorldView().getPlane() != tile.getPlane() || layer.getCanvasLocation() == null) { continue; }
                int row = rows.getOrDefault(tile, 0);
                rows.put(tile, row + 1);
                if (fill && row == 0 && itemFill.getAlpha() > 0)
                {
                    Polygon polygon = layer.getCanvasTilePoly();
                    if (polygon != null) { graphics.setColor(itemFill); graphics.fill(polygon); }
                }
                if (outline && itemLine.getAlpha() > 0) { outlines.drawOutline(layer, item, width, itemLine, 0); }
                if (text)
                {
                    Point point = layer.getCanvasTextLocation(graphics, label.text, 20);
                    if (point == null) { continue; }
                    int x = point.getX(), y = point.getY() - row * (metrics.getHeight() + 4);
                    graphics.setColor(background);
                    graphics.fillRect(x - 3, y - metrics.getAscent() - 2, metrics.stringWidth(label.text) + 6, metrics.getHeight() + 4);
                    graphics.setColor(itemShadow);
                    graphics.drawString(label.text, x + 1, y + 1);
                    graphics.setColor(itemText);
                    graphics.drawString(label.text, x, y);
                }
            }
        }
        return null;
    }
}
