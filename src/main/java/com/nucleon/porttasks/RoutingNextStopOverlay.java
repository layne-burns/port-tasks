package com.nucleon.porttasks;

import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.routing.BagSize;
import com.nucleon.porttasks.routing.RoutePlanner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Routing extension: a small movable panel with two sections, each with its own setting:
 *  - the next stop of the plan and what to do there, plus the stop after it (work left at the boat's own
 *    port comes first as "Here");
 *  - the port bags received this session, by type and size.
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
		RoutePlanner.Plan plan = plugin.routingService.plan();
		if (!config.routingEnabled() || !config.routingNextStopPanel() || plan == null || plan.stops.isEmpty())
		{
			return false;
		}
		PortLocation boatPort = plugin.routingService.dockedPort();

		panelComponent.getChildren().add(TitleComponent.builder().text("Courier route").color(config.routingLegColor()).build());
		// Work left at the boat's own port comes first as "Here"; then the next stop and the one after.
		boolean startsHere = plan.stops.get(0).port == boatPort;
		String[] labels = startsHere ? new String[]{"Here", "Next", "Then"} : new String[]{"Next", "Then"};
		int shown = 0;
		while (shown < labels.length && shown < plan.stops.size())
		{
			RoutePlanner.Stop stop = plan.stops.get(shown);
			panelComponent.getChildren().add(LineComponent.builder()
				.left(labels[shown] + ": " + stop.port.getName())
				.leftColor(shown == 0 ? Color.YELLOW : Color.WHITE)
				.build());
			for (String action : actions(stop))
			{
				panelComponent.getChildren().add(LineComponent.builder().left("  " + action).leftColor(Color.LIGHT_GRAY).build());
			}
			shown++;
		}
		int remaining = plan.stops.size() - shown;
		if (remaining > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("+" + remaining + " more stop" + (remaining == 1 ? "" : "s"))
				.leftColor(Color.GRAY)
				.build());
		}
		return true;
	}

	private List<String> actions(RoutePlanner.Stop stop)
	{
		List<String> lines = new ArrayList<>();
		for (int id : stop.pickups)
		{
			CourierTask t = task(id);
			if (t != null)
			{
				int left = t.getData().cargoAmount - t.getCargoTaken();
				lines.add("Pick up " + left + " " + cargoName(t));
			}
		}
		for (int id : stop.deliveries)
		{
			CourierTask t = task(id);
			if (t != null)
			{
				int left = t.getData().cargoAmount - t.getDelivered();
				lines.add("Deliver " + left + " " + cargoName(t));
			}
		}
		return lines;
	}

	private CourierTask task(int taskId)
	{
		for (CourierTask t : plugin.courierTasks)
		{
			if (t.getData().getId() == taskId)
			{
				return t;
			}
		}
		return null;
	}

	/** "Crate of lead" -> "lead"; falls back to the task name if the item is unknown. */
	String cargoName(CourierTask t)
	{
		String name = itemManager.getItemComposition(t.getData().cargo).getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return t.getData().taskName;
		}
		return name.startsWith("Crate of ") ? name.substring("Crate of ".length()) : name;
	}
}
