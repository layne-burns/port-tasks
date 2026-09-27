package com.nucleon.porttasks;

import com.nucleon.porttasks.routing.BagSize;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Routing extension: a small movable panel with three sections, each with its own setting:
 *  - the next stop of the plan and what to do there, plus the stop after it (work left at the boat's own
 *    port comes first as "Here");
 *  - the leg under way: tiles sailed and time, against the planning estimate; once docked, the last leg;
 *  - the port bags received this session, by type and size.
 * The route lines are worked out in PortView when the plan or tasks change; rendering only reads them.
 */
class RoutingNextStopOverlay extends OverlayPanel
{
	private final PortTasksPlugin plugin;
	private final PortTasksConfig config;
	private final ItemManager itemManager;

	@Inject
	private RoutingNextStopOverlay(PortTasksPlugin plugin, PortTasksConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean route = renderRoute();
		boolean bags = renderBags();
		return route || bags ? super.render(graphics) : null;
	}

	private boolean renderBags()
	{
		if (!config.routingBagCounts() || plugin.bagCounter.total("coin") + plugin.bagCounter.total("reward") == 0)
		{
			return false;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Bags this session").color(config.routingLegColor()).build());
		for (String type : new String[]{"coin", "reward"})
		{
			StringBuilder sizes = new StringBuilder();
			for (BagSize size : BagSize.values())
			{
				int n = plugin.bagCounter.received(type, size);
				if (n > 0)
				{
					sizes.append(sizes.length() == 0 ? "" : ", ").append(n).append(' ').append(size.wikiName());
				}
			}
			// Total right-aligned so the counts line up by units; the size breakdown goes on its own line.
			panelComponent.getChildren().add(LineComponent.builder()
				.left("coin".equals(type) ? "Coin bags" : "Reward bags")
				.right(Integer.toString(plugin.bagCounter.total(type)))
				.build());
			if (sizes.length() > 0)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("  " + sizes)
					.leftColor(Color.GRAY)
					.build());
			}
		}
		return true;
	}

	private boolean renderRoute()
	{
		List<PortView.Line> lines = plugin.view().routeLines;
		if (lines.isEmpty())
		{
			return false;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Courier route").color(config.routingLegColor()).build());
		for (PortView.Line line : lines)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(line.text).leftColor(line.colour).build());
		}
		return true;
	}
}
