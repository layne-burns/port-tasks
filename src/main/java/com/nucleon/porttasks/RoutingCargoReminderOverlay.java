package com.nucleon.porttasks;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Routing extension: text above the player at a port: a wrong-port or wrong-crate warning, else "Grab N more
 * crates of X" where a planned pickup isn't fully loaded. Worked out in PortView when it changes; drawn here
 * rather than set as the game's overhead text, so it doesn't fight chat bubbles or other plugins.
 */
class RoutingCargoReminderOverlay extends Overlay
{
	private final Client client;
	private final PortTasksPlugin plugin;

	@Inject
	private RoutingCargoReminderOverlay(Client client, PortTasksPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		PortView view = plugin.view();
		Player player = client.getLocalPlayer();
		if (view.overheadText == null || player == null)
		{
			return null;
		}
		Point loc = player.getCanvasTextLocation(graphics, view.overheadText, player.getLogicalHeight() + 40);
		if (loc != null)
		{
			OverlayUtil.renderTextLocation(graphics, loc, view.overheadText, view.overheadColour);
		}
		return null;
	}
}
