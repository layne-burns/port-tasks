package com.nucleon.porttasks;

import com.nucleon.porttasks.enums.PortLocation;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Routing extension: while the boat is docked at a port where a planned pickup isn't fully loaded, shows
 * "Grab N more crates of X" above the player. Drawn by this overlay rather than set as the game's overhead
 * text, so it doesn't fight chat bubbles or other plugins.
 */
class RoutingCargoReminderOverlay extends Overlay
{
	private static final Color TEXT = new Color(255, 200, 0);

	private final Client client;
	private final PortTasksPlugin plugin;
	private final PortTasksConfig config;
	private final ItemManager itemManager;

	@Inject
	private RoutingCargoReminderOverlay(Client client, PortTasksPlugin plugin, PortTasksConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.routingEnabled() || !config.routingCargoReminder())
		{
			return null;
		}
		PortLocation port = plugin.routingService.boatPort();
		Player player = client.getLocalPlayer();
		if (port == null || player == null)
		{
			return null;
		}
		String text = null;
		for (CourierTask t : plugin.courierTasks)
		{
			CourierTaskData d = t.getData();
			int left = d.cargoAmount - t.getCargoTaken();
			if (d.getCargoLocation() != port || left <= 0)
			{
				continue;
			}
			String line = "Grab " + left + " more " + (left == 1 ? "crate" : "crates") + " of " + cargoName(d);
			text = text == null ? line : text + " / " + line;
		}
		if (text == null)
		{
			return null;
		}
		Point loc = player.getCanvasTextLocation(graphics, text, player.getLogicalHeight() + 40);
		if (loc != null)
		{
			OverlayUtil.renderTextLocation(graphics, loc, text, TEXT);
		}
		return null;
	}

	private String cargoName(CourierTaskData d)
	{
		String name = itemManager.getItemComposition(d.cargo).getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return d.taskName;
		}
		return name.startsWith("Crate of ") ? name.substring("Crate of ".length()) : name;
	}
}
