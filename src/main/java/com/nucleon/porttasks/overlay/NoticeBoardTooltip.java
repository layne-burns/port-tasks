package com.nucleon.porttasks.overlay;

import com.nucleon.porttasks.CourierTaskData;
import com.nucleon.porttasks.OfferedTaskData;
import com.nucleon.porttasks.PortTasksConfig;
import com.nucleon.porttasks.PortTasksPlugin;
import com.nucleon.porttasks.enums.BountyTaskData;
import com.nucleon.porttasks.enums.TaskReward;
import com.nucleon.porttasks.routing.BoardScorer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Slf4j
public class NoticeBoardTooltip extends Overlay
{
	private final TooltipManager tooltipManager;
	private final Client client;
	private final PortTasksPlugin plugin;


	@Inject
	NoticeBoardTooltip(Client client, TooltipManager tooltipManager, PortTasksPlugin plugin)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		drawAfterInterface(InterfaceID.TOPLEVEL_DISPLAY);
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.plugin = plugin;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget widget = client.getWidget(InterfaceID.PortTaskBoard.CONTAINER);
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		// The world map obscures the notice board. Hide tooltips when world map is open
		Widget worldMap = client.getWidget(InterfaceID.Worldmap.CONTENT);
		if (worldMap != null && !worldMap.isHidden())
		{
			return null;
		}

		Widget taskInfo = client.getWidget(InterfaceID.PortTaskInfo.WINDOW);
		if (taskInfo != null && !taskInfo.isHidden())
		{
			return null;
		}

		Integer dbrow = getHoveredTask();
		if (dbrow == null)
		{
			return null;
		}
		Object task = getTask(dbrow);

		if (task instanceof CourierTaskData)
		{
			CourierTaskData data = (CourierTaskData) task;
			Color isAtCurLocation = data.getNoticeBoard() == data.getCargoLocation() ? Color.WHITE : Color.RED;
			String sourceColorTag = toColTag(isAtCurLocation);
			String endTag = "</col>";
			int distance = (int) Math.round(data.getDockMarkers().getDistance());

			double xpPerTileRatio = data.getXpPerTileRatio();
			int xpPerTilePercent = (int) Math.round(xpPerTileRatio * 100.0);
			Color xpColor = interpolateColor(plugin.getMinColor(), plugin.getMaxColor(), xpPerTileRatio);
			String xpColorTag = toColTag(xpColor);

			String tooltip = String.format(
				"Source: %s%s%s<br>" +
				"Destination: %s<br>" +
				"Experience: %s xp<br>" +
				"Distance: %d tiles<br>" +
				"XP/Tile: %s%d%%%s<br>" +
				"Amount of cargo: %d",
				sourceColorTag,
				data.getCargoLocation(),
				endTag,
				data.getDeliveryLocation(),
				courierXp(data),
				distance,
				xpColorTag,
				xpPerTilePercent,
				endTag,
				data.getCargoAmount()
			);
			tooltipManager.add(new Tooltip(tooltip + routingLines(data)));
		}
		if (task instanceof BountyTaskData)
		{
			BountyTaskData data = (BountyTaskData) task;
			String tooltip = String.format(
				"Experience: %s<br>" +
				"Items required: %d<br>" +
				"Item rarity: 1 in %d",
				TaskReward.getRewardForTask(data.getDbrow()),
				data.getItemQuantity(),
				data.getItemRarity()
			);
			tooltipManager.add(new Tooltip(tooltip));
		}
		return null;
	}

	/** Routing extension: XP learned from play, else the wiki's, else Port Tasks' own table. */
	private String courierXp(CourierTaskData data)
	{
		Integer xp = plugin.taskXp(data.getId());
		return xp != null ? String.format("%,d", xp) : TaskReward.getRewardForTask(data.getDbrow());
	}

	/** Routing extension (SPEC-routing.md §2.3): the enabled board metrics, signature drops and wanted items. */
	private String routingLines(CourierTaskData data)
	{
		PortTasksConfig config = plugin.routingConfig();
		BoardScorer.Score s = plugin.boardScore(data.getDbrow());
		if (!config.routingEnabled() || s == null)
		{
			return "";
		}
		StringBuilder sb = new StringBuilder("<br>").append(toColTag(config.routingLegColor()))
			.append("Rank #").append(s.rank).append(" by ").append(config.routingRankBy()).append("</col>");
		if (config.routingShowAdded())
		{
			sb.append(String.format("<br>Adds: %+.0f tiles, %+d stop%s", s.addedTiles, s.addedStops, Math.abs(s.addedStops) == 1 ? "" : "s"));
		}
		if (config.routingShowXpPerTile())
		{
			sb.append(String.format("<br>XP / added tile: %.2f", s.xpPerAddedTile));
		}
		if (config.routingShowValuePerTile())
		{
			sb.append(String.format("<br>Value / added tile: %.0f gp", s.valuePerAddedTile));
		}
		if (config.routingShowRouteFit())
		{
			sb.append(String.format("<br>Route fit: %.0f%% new sailing", s.routeFit * 100));
		}
		if (config.routingShowPlanRate())
		{
			sb.append(String.format("<br>Plan rate after: %.2f xp/tile", s.planRateAfter));
		}
		if (config.routingShowBag() && s.bag != null)
		{
			sb.append(String.format("<br>Bag: %s, ~%,.0f gp expected", s.bag.wikiName(), s.expectedValue));
		}
		if (config.routingShowDrops() && !s.signatureDrops.isEmpty())
		{
			sb.append("<br>Drops: ").append(String.join(", ", s.signatureDrops));
		}
		if (!s.wantedDrops.isEmpty())
		{
			sb.append("<br>").append(toColTag(config.routingWantedColor())).append("Wanted: ")
				.append(String.join(", ", s.wantedDrops)).append("</col>");
		}
		return sb.toString();
	}

	private static String toColTag(Color c)
	{
		return String.format("<col=%02x%02x%02x>", c.getRed(), c.getGreen(), c.getBlue());
	}

	private Integer getHoveredTask()
	{
		Point mouse = client.getMouseCanvasPosition();
		for (Map.Entry<Integer, OfferedTaskData> entry : plugin.getOfferedTasks().entrySet())
		{
			Integer dbrow = entry.getKey();
			OfferedTaskData data = entry.getValue();
			Widget w = data.getTaskWidget();

			Rectangle bounds = w.getBounds();
			if (bounds != null & bounds.contains(mouse.getX(), mouse.getY()))
			{
				return dbrow;
			}
		}
		return null;
	}

	private Object getTask(int dbrow)
	{
		CourierTaskData courier = CourierTaskData.getByDbrow(dbrow);
		if (courier != null)
		{
			return courier;
		}

		BountyTaskData bounty = BountyTaskData.getByDbrow(dbrow);
		if (bounty != null)
		{
			return bounty;
		}
		return null;
	}

	private Color interpolateColor (Color min, Color max, double t)
	{
		t = Math.max(0.0, Math.min(1.0, t));

		int r = (int) Math.round(min.getRed()   + (max.getRed()   - min.getRed())   * t);
		int g = (int) Math.round(min.getGreen() + (max.getGreen() - min.getGreen()) * t);
		int b = (int) Math.round(min.getBlue()  + (max.getBlue()  - min.getBlue())  * t);

		return new Color(r, g, b);
	}
}
