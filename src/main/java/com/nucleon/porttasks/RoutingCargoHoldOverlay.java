package com.nucleon.porttasks;

import com.nucleon.porttasks.enums.PortLocation;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;

/**
 * Routing extension: in the boat's cargo hold, while docked, the crates to take out at this port are tinted
 * (default green, like inventory tags) and labelled "TAKE", and courier crates for other ports are dimmed,
 * so the right ones are obvious to click. Works alongside Port Tasks' own per-task outlines.
 */
class RoutingCargoHoldOverlay extends WidgetItemOverlay
{
	private static final Color DIM = new Color(0, 0, 0, 140);

	private static final int TINT_ALPHA = 110;

	private final PortTasksPlugin plugin;
	private final PortTasksConfig config;
	private final ItemManager itemManager;

	@Inject
	private RoutingCargoHoldOverlay(PortTasksPlugin plugin, PortTasksConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInterfaces(InterfaceID.SAILING_BOAT_CARGOHOLD);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.routingEnabled() || !config.routingHighlightHold())
		{
			return;
		}
		PortLocation port = plugin.routingService.dockedPort();
		if (port == null)
		{
			return;
		}
		boolean courierCrate = false;
		boolean forHere = false;
		for (CourierTask t : plugin.courierTasks)
		{
			CourierTaskData d = t.getData();
			if (d.cargo != itemId || t.getDelivered() >= d.cargoAmount)
			{
				continue;
			}
			courierCrate = true;
			forHere |= d.getDeliveryLocation() == port;
		}
		if (!courierCrate)
		{
			return;
		}
		Rectangle r = widgetItem.getCanvasBounds();
		if (!forHere)
		{
			graphics.setColor(DIM);
			graphics.fillRect(r.x, r.y, r.width, r.height);
			return;
		}
		Color c = config.routingTakeColor();
		Color tint = new Color(c.getRed(), c.getGreen(), c.getBlue(), TINT_ALPHA);
		graphics.drawImage(ImageUtil.fillImage(itemManager.getImage(itemId, widgetItem.getQuantity(), false), tint), r.x, r.y, null);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		graphics.setColor(Color.BLACK);
		graphics.drawString("TAKE", r.x + 1, r.y + r.height);
		graphics.setColor(c);
		graphics.drawString("TAKE", r.x, r.y + r.height - 1);
	}
}
