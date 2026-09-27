package com.nucleon.porttasks;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;

/**
 * Routing extension: in the boat's cargo hold, the crates to take out are tinted (default green, like
 * inventory tags) and courier crates for other ports are dimmed, so the right ones are obvious to click.
 * Docked: crates for this port, labelled "TAKE". At sea: crates for the next stop, labelled "NEXT" (see
 * PortView for which). Tinted images are made once per crate and colour, as RuneLite's inventory tags do.
 */
class RoutingCargoHoldOverlay extends WidgetItemOverlay
{
	private static final Color DIM = new Color(0, 0, 0, 140);
	private static final int TINT_ALPHA = 110;

	private final PortTasksPlugin plugin;
	private final ItemManager itemManager;
	/** (item id, quantity) -> tinted image, for {@link #tintColour}. */
	private final Map<Long, Image> tinted = new HashMap<>();
	private Color tintColour;

	@Inject
	private RoutingCargoHoldOverlay(PortTasksPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		showOnInterfaces(InterfaceID.SAILING_BOAT_CARGOHOLD);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		PortView view = plugin.view();
		PortView.CrateMark mark = view.crateMarks.get(itemId);
		if (mark == null)
		{
			return;
		}
		Rectangle r = widgetItem.getCanvasBounds();
		if (mark.label == null)
		{
			graphics.setColor(DIM);
			graphics.fillRect(r.x, r.y, r.width, r.height);
			return;
		}
		Color c = view.takeColour;
		graphics.drawImage(tinted(itemId, widgetItem.getQuantity(), c), r.x, r.y, null);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		graphics.setColor(Color.BLACK);
		graphics.drawString(mark.label, r.x + 1, r.y + r.height);
		graphics.setColor(c);
		graphics.drawString(mark.label, r.x, r.y + r.height - 1);
	}

	private Image tinted(int itemId, int quantity, Color colour)
	{
		if (!colour.equals(tintColour))
		{
			tinted.clear();
			tintColour = colour;
		}
		return tinted.computeIfAbsent(((long) itemId << 32) | quantity, k -> ImageUtil.fillImage(
			itemManager.getImage(itemId, quantity, false),
			new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), TINT_ALPHA)));
	}
}
