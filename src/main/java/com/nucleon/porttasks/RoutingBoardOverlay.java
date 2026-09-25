package com.nucleon.porttasks;

import com.nucleon.porttasks.routing.BoardScorer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Routing extension (SPEC-routing.md §2.3): on an open notice board, each offered courier task gets its
 * rank under the chosen metric; the best is outlined in the route colour and tasks that can give a wanted
 * item in the wanted colour.
 */
class RoutingBoardOverlay extends Overlay
{
	private final Client client;
	private final PortTasksPlugin plugin;
	private final PortTasksConfig config;

	@Inject
	private RoutingBoardOverlay(Client client, PortTasksPlugin plugin, PortTasksConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.routingEnabled() || !config.routingBoardBadges())
		{
			return null;
		}
		Widget board = client.getWidget(InterfaceID.PortTaskBoard.CONTAINER);
		if (board == null || board.isHidden())
		{
			return null;
		}
		graphics.setFont(FontManager.getRunescapeSmallFont());
		for (Map.Entry<Integer, OfferedTaskData> e : plugin.getOfferedTasks().entrySet())
		{
			BoardScorer.Score s = plugin.boardScore(e.getKey());
			Widget w = e.getValue().getTaskWidget();
			Rectangle r = w == null ? null : w.getBounds();
			if (s == null || r == null || w.isHidden())
			{
				continue;
			}
			if (s.rank == 1)
			{
				outline(graphics, r, config.routingLegColor(), 0);
			}
			if (!s.wantedDrops.isEmpty())
			{
				outline(graphics, r, config.routingWantedColor(), s.rank == 1 ? 3 : 0);
			}
			OverlayUtil.renderTextLocation(graphics, new Point(r.x + 3, r.y + 12), "#" + s.rank,
				s.rank == 1 ? config.routingLegColor() : Color.WHITE);
		}
		return null;
	}

	private static void outline(Graphics2D g, Rectangle r, Color c, int inset)
	{
		g.setColor(c);
		g.setStroke(new BasicStroke(2));
		g.drawRect(r.x + inset, r.y + inset, r.width - 2 * inset, r.height - 2 * inset);
	}
}
