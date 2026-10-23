package com.mydrops;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MyDropsOverlayTest
{
    private Client client;
    private MyDropsPlugin plugin;
    private MyDropsConfig config;
    private ModelOutlineRenderer outlines;
    private ItemLayer layer;
    private TileItem item;
    private Tile tile;
    private BufferedImage image;
    private Graphics2D graphics;

    @Before public void setup()
    {
        client = mock(Client.class); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        plugin = new MyDropsPlugin(); config = mock(MyDropsConfig.class); outlines = mock(ModelOutlineRenderer.class);
        when(config.outlineColor()).thenReturn(Color.CYAN); when(config.fillColor()).thenReturn(new Color(0, 255, 255, 45));
        when(config.textColor()).thenReturn(Color.CYAN); when(config.backgroundColor()).thenReturn(new Color(0, 0, 0, 150));
        when(config.groupColor()).thenReturn(Color.MAGENTA);
        layer = mock(ItemLayer.class); WorldView view = mock(WorldView.class); when(layer.getWorldView()).thenReturn(view);
        when(layer.getCanvasLocation()).thenReturn(new Point(50, 50));
        when(layer.getCanvasTilePoly()).thenReturn(new Polygon(new int[]{10, 90, 90, 10}, new int[]{10, 10, 90, 90}, 4));
        tile = mock(Tile.class); when(tile.getItemLayer()).thenReturn(layer); item = mock(TileItem.class);
        plugin.tracker.tracked().put(item, tile); MyDropsPlugin.Label label = new MyDropsPlugin.Label("Shark", "Shark", ResourceFilter.FOOD); label.quantity(1); plugin.labels.put(item, label);
        image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB); graphics = image.createGraphics();
    }
    private void render() { new MyDropsOverlay(client, plugin, config, outlines).render(graphics); graphics.dispose(); }
    @Test public void usesIndividualItemOutlineAndNoTextByDefault()
    {
        when(config.outline()).thenReturn(true); when(config.outlineWidth()).thenReturn(3); render();
        verify(outlines).drawOutline(layer, item, 3, Color.CYAN, 0); verify(layer, never()).getCanvasTextLocation(any(), anyString(), anyInt());
    }
    @Test public void fillOpacityIsPreservedAndSameTileIsFilledOnce()
    {
        when(config.tileFill()).thenReturn(true); TileItem second = mock(TileItem.class);
        plugin.tracker.tracked().put(second, tile); plugin.labels.put(second, plugin.labels.get(item)); render();
        assertEquals(45, new Color(image.getRGB(50, 50), true).getAlpha()); verify(layer, times(1)).getCanvasTilePoly();
    }
    @Test public void transparentOutlineSkipsModelWork()
    {
        when(config.outline()).thenReturn(true); when(config.outlineColor()).thenReturn(new Color(0, 255, 255, 0)); render(); verifyNoInteractions(outlines);
    }
    @Test public void disabledFilterSkipsModelWork()
    {
        when(config.outline()).thenReturn(true); when(config.resourcesOnly()).thenReturn(true); render(); verifyNoInteractions(outlines);
    }
    @Test public void invisibleTextAndBackgroundStayInvisible()
    {
        when(config.text()).thenReturn(true); when(config.textColor()).thenReturn(new Color(0, 255, 255, 0)); when(config.backgroundColor()).thenReturn(new Color(0, 0, 0, 0));
        when(layer.getCanvasTextLocation(any(), anyString(), eq(20))).thenReturn(new Point(20, 50)); render();
        for (int y = 0; y < 100; y++) { for (int x = 0; x < 100; x++) { assertEquals(0, image.getRGB(x, y)); } }
    }
    private void groupOnly()
    {
        plugin.tracker.tracked().clear(); plugin.groupItems.put(item, tile);
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP);
        when(config.groupItems()).thenReturn(true);
    }
    @Test public void groupUsesSeparateColorWithoutOwnDropIntent()
    {
        groupOnly(); when(config.outline()).thenReturn(true); when(config.outlineWidth()).thenReturn(2); render();
        verify(outlines).drawOutline(layer, item, 2, Color.MAGENTA, 0);
    }
    @Test public void groupToggleOffSkipsRendering()
    {
        groupOnly(); when(config.groupItems()).thenReturn(false); when(config.outline()).thenReturn(true); render(); verifyNoInteractions(outlines);
    }
    @Test public void groupOwnershipIsRechecked()
    {
        groupOnly(); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER); when(config.outline()).thenReturn(true); render(); verifyNoInteractions(outlines);
    }
    @Test public void publicItemNoLongerUsesGroupColor()
    {
        groupOnly(); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE); when(config.outline()).thenReturn(true); render(); verifyNoInteractions(outlines);
    }
    @Test public void selfOwnershipDoesNotImplyDeliberateDrop()
    {
        groupOnly(); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF); when(config.outline()).thenReturn(true); render(); verifyNoInteractions(outlines);
    }
    @Test public void groupUsesExistingResourceFilters()
    {
        groupOnly(); when(config.resourcesOnly()).thenReturn(true); when(config.outline()).thenReturn(true); render(); verifyNoInteractions(outlines);
    }
    @Test public void groupAlphaCombinesWithFillOpacity()
    {
        groupOnly(); when(config.groupColor()).thenReturn(new Color(255, 0, 255, 128)); when(config.tileFill()).thenReturn(true); render();
        Color pixel = new Color(image.getRGB(50, 50), true); assertEquals(22, pixel.getAlpha()); assertEquals(255, pixel.getRed()); assertEquals(0, pixel.getGreen());
    }
    @Test public void zeroGroupOpacityHidesBackgroundToo()
    {
        groupOnly(); when(config.groupColor()).thenReturn(new Color(255, 0, 255, 0)); when(config.text()).thenReturn(true); when(config.tileFill()).thenReturn(true);
        when(layer.getCanvasTextLocation(any(), anyString(), eq(20))).thenReturn(new Point(20, 50)); render();
        assertEquals(0, image.getRGB(50, 50)); verify(layer, never()).getCanvasTextLocation(any(), anyString(), anyInt());
    }
    @Test public void ownItemPrecedenceAvoidsDuplicateOutline()
    {
        when(config.groupItems()).thenReturn(true); when(config.outline()).thenReturn(true); when(config.outlineWidth()).thenReturn(2);
        plugin.groupItems.put(item, tile); when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP); render();
        verify(outlines).drawOutline(layer, item, 2, Color.CYAN, 0); verifyNoMoreInteractions(outlines);
    }
    @Test public void ownTileFillWinsMixedPile()
    {
        when(config.groupItems()).thenReturn(true); when(config.tileFill()).thenReturn(true); TileItem other = mock(TileItem.class);
        when(other.getOwnership()).thenReturn(TileItem.OWNERSHIP_GROUP); plugin.groupItems.put(other, tile); plugin.labels.put(other, plugin.labels.get(item)); render();
        Color pixel = new Color(image.getRGB(50, 50), true); assertEquals(45, pixel.getAlpha()); assertEquals(0, pixel.getRed()); assertEquals(255, pixel.getGreen()); verify(layer, times(1)).getCanvasTilePoly();
    }
    @Test public void groupTextUsesGroupColor()
    {
        groupOnly(); when(config.text()).thenReturn(true); when(layer.getCanvasTextLocation(any(), anyString(), eq(20))).thenReturn(new Point(20, 50)); render();
        boolean found = false;
        for (int y = 0; y < 100; y++) { for (int x = 0; x < 100; x++) { found |= image.getRGB(x, y) == Color.MAGENTA.getRGB(); } }
        assertTrue("Expected group-colored item name", found);
    }
}
