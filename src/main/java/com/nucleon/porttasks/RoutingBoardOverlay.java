package com.nucleon.porttasks;

import com.nucleon.porttasks.routing.BoardScorer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
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
 * item in the wanted colour. Each is also tinted by how much sailing it adds (BoardScorer.detourColor);
 * everything drawn here is worked out when the board is scored, not per frame.
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
		boolean badges = config.routingBoardBadges();
		boolean tint = config.routingDetourTint();
		if (!badges && !tint)
		{
			return null;
		}
		Widget board = client.getWidget(InterfaceID.PortTaskBoard.CONTAINER);
		if (board == null || board.isHidden())
		{
			return null;
		}
		// The board stays open under the task's accept window and the world map; the original overlays hide
		// there too (TaskHighlight, NoticeBoardTooltip).
		Widget taskInfo = client.getWidget(InterfaceID.PortTaskInfo.WINDOW);
		Widget worldMap = client.getWidget(InterfaceID.Worldmap.CONTENT);
		if (taskInfo != null && !taskInfo.isHidden() || worldMap != null && !worldMap.isHidden())
		{
			return null;
		}
		graphics.setFont(FontManager.getRunescapeSmallFont());
		AlphaComposite tintAlpha = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, config.routingDetourOpacity() / 100f);
		for (Map.Entry<Integer, OfferedTaskData> e : plugin.getOfferedTasks().entrySet())
		{
			BoardScorer.Score s = plugin.boardScore(e.getKey());
			Widget w = e.getValue().getTaskWidget();
			Rectangle r = w == null ? null : w.getBounds();
			if (r == null || w.isHidden())
			{
				continue;
			}
			if (s == null)
			{
				// Not a scored courier task: a bounty the hunt is after gets outlined.
				if (badges && plugin.hunted(e.getKey()))
				{
					outline(graphics, r, config.routingWantedColor(), 0);
					OverlayUtil.renderTextLocation(graphics, new Point(r.x + 3, r.y + 12), "Hunt", config.routingWantedColor());
				}
				continue;
			}
			if (tint)
			{
				Composite opaque = graphics.getComposite();
				graphics.setComposite(tintAlpha);
				graphics.setColor(s.detourColor);
				graphics.fill(r);
				graphics.setComposite(opaque);
			}
			if (!badges)
			{
				continue;
			}
			// The best set's tasks are outlined (or, with that off, the single best task).
			boolean picked = config.routingBestSet() ? plugin.inBestSet(e.getKey()) : s.rank == 1;
			if (picked)
			{
				outline(graphics, r, config.routingLegColor(), 0);
			}
			if (!s.wantedDrops.isEmpty())
			{
				outline(graphics, r, config.routingWantedColor(), picked ? 3 : 0);
			}
			// With a loop set, tasks that leave it are ranked after the rest and badged in grey.
			OverlayUtil.renderTextLocation(graphics, new Point(r.x + 3, r.y + 12), "#" + s.rank + (s.offLoop ? " off loop" : ""),
				picked ? config.routingLegColor() : s.offLoop ? Color.GRAY : Color.WHITE);
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
