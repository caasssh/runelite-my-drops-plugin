package com.mydrops;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Illustrative scene: actual label/tile renderer, mocked projection and item silhouettes. */
public final class AppearancePreview
{
    public static void main(String[] args) throws Exception
    {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        render(output.resolve("appearance-100.png"), 1);
        render(output.resolve("appearance-150.png"), 1.5);
    }

    private static void render(Path path, double scale) throws Exception
    {
        BufferedImage image = new BufferedImage((int) (1115 * scale), (int) (370 * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(scale, scale);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(28, 29, 32));
        graphics.fillRect(0, 0, 1115, 370);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        graphics.setColor(Color.WHITE);
        graphics.drawString("My Drops - appearance preview", 20, 30);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.setColor(new Color(190, 190, 190));
        graphics.drawString("Illustrative scene. Item shapes are placeholders; labels and tile fills use the plugin renderer.", 20, 53);
        panel(graphics, 20, "Default: item outline", 0);
        panel(graphics, 295, "Names and background", 1);
        panel(graphics, 570, "GIM group items (includes loot)", 2);
        panel(graphics, 845, "Own drop + GIM group item", 3);
        graphics.setColor(new Color(190, 190, 190));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.drawString("Cyan: your deliberate drops. Magenta: GIM group-owned items, including loot. No dropper names or Party setup.", 20, 327);
        graphics.drawString("Each color includes opacity. Optional item names can duplicate the core Ground Items labels. Mixed tiles use your own fill color.", 20, 348);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void panel(Graphics2D canvas, int x, String title, int mode)
    {
        Graphics2D graphics = (Graphics2D) canvas.create(x, 75, 250, 225);
        graphics.setColor(new Color(54, 59, 48));
        graphics.fillRect(0, 0, 250, 225);
        graphics.setColor(new Color(65, 70, 57));
        for (int y = 35; y < 225; y += 26) { graphics.drawLine(0, y, 250, y - 22); }
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.drawString(title, 10, 21);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        WorldView view = mock(WorldView.class);
        when(view.getPlane()).thenReturn(0);
        Tile tile = mock(Tile.class);
        when(tile.getPlane()).thenReturn(0);
        ItemLayer layer = mock(ItemLayer.class);
        when(layer.getWorldView()).thenReturn(view);
        when(tile.getItemLayer()).thenReturn(layer);
        when(layer.getCanvasLocation()).thenReturn(new Point(125, 153));
        when(layer.getCanvasTilePoly()).thenReturn(new Polygon(new int[]{125, 210, 125, 40}, new int[]{116, 153, 190, 153}, 4));
        when(layer.getCanvasTextLocation(any(Graphics2D.class), anyString(), eq(20)))
            .thenAnswer(call -> new Point(125 - graphics.getFontMetrics().stringWidth(call.getArgument(1)) / 2, 112));

        MyDropsConfig config = new MyDropsConfig()
        {
            @Override public boolean outline() { return mode != 1; }
            @Override public boolean text() { return mode != 0; }
            @Override public boolean tileFill() { return mode >= 2; }
            @Override public boolean groupItems() { return mode >= 2; }
        };
        MyDropsPlugin plugin = new MyDropsPlugin();
        plugin.filter.setExtras("");
        TileItem food = item(plugin, tile, "Shark", ResourceFilter.FOOD, 1, mode == 2);
        TileItem ammo = item(plugin, tile, "Dragon dart(p++)", ResourceFilter.AMMO, 200, mode >= 2);
        Polygon foodShape = new Polygon(new int[]{80, 108, 121, 111, 88, 70}, new int[]{148, 134, 147, 158, 161, 157}, 6);
        Polygon ammoShape = new Polygon(new int[]{145, 164, 170, 151}, new int[]{159, 140, 145, 164}, 4);
        graphics.setColor(new Color(160, 159, 130));
        graphics.fill(foodShape);
        graphics.setColor(new Color(145, 80, 65));
        graphics.fill(ammoShape);
        ModelOutlineRenderer outlines = mock(ModelOutlineRenderer.class);
        doAnswer(call ->
        {
            graphics.setColor(call.getArgument(3));
            graphics.setStroke(new BasicStroke(call.getArgument(2, Integer.class).floatValue()));
            graphics.draw(call.getArgument(1) == food ? foodShape : ammoShape);
            return null;
        }).when(outlines).drawOutline(eq(layer), any(TileItem.class), anyInt(), any(Color.class), anyInt());
        new MyDropsOverlay(client, plugin, config, outlines).render(graphics);
        graphics.setColor(new Color(220, 220, 210));
        String caption = mode == 2 ? "Group-owned resources / loot" : mode == 3 ? "Cyan own fill wins on mixed tile" : "Two of your dropped items";
        graphics.drawString(caption, (250 - graphics.getFontMetrics().stringWidth(caption)) / 2, 210);
        graphics.dispose();
    }

    private static TileItem item(MyDropsPlugin plugin, Tile tile, String name, int categories, int quantity, boolean group)
    {
        TileItem item = mock(TileItem.class);
        when(item.getOwnership()).thenReturn(group ? TileItem.OWNERSHIP_GROUP : TileItem.OWNERSHIP_SELF);
        if (group) { plugin.groupItems.put(item, tile); }
        else { plugin.tracker.tracked().put(item, tile); }
        MyDropsPlugin.Label label = new MyDropsPlugin.Label(name, name, categories);
        label.quantity(quantity);
        plugin.labels.put(item, label);
        return item;
    }
}
