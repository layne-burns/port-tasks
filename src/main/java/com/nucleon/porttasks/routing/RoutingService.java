package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.CourierTask;
import com.nucleon.porttasks.PortTasksConfig;
import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Keeps the optimal plan for the active courier tasks and the next leg to draw (SPEC-routing.md §2.1, §2.3).
 * Re-planned from the boat's port whenever tasks, cargo or the boat's port change. While the boat is at sea
 * (no known port) the last plan is kept, so the leg being sailed stays on screen. Client thread only.
 *
 * The plan uses our port graph (it needs a cost for every pair of ports), but the leg itself can be drawn by
 * the Shortest Path plugin, which paths at sea: we send it the next stop's dock as its target over its
 * PluginMessage API ("shortestpath" / "path", "clear") and it draws the route from wherever the player is.
 */
@Slf4j
public final class RoutingService
{
	/** The first-pass region (SPEC-routing.md §3.1). */
	public static final Set<PortLocation> WEST = Collections.unmodifiableSet(EnumSet.of(
		PortLocation.VOID_KNIGHTS_OUTPOST, PortLocation.DEEPFIN_POINT, PortLocation.ALDARIN, PortLocation.SUNSET_COAST,
		PortLocation.CIVITAS_ILLA_FORTIS, PortLocation.PORT_ROBERTS, PortLocation.PORT_TYRAS, PortLocation.PRIFDDINAS,
		PortLocation.LANDS_END, PortLocation.HOSIDIUS, PortLocation.PORT_PISCARILIUS, PortLocation.PISCATORIS,
		PortLocation.RELLEKKA, PortLocation.LUNAR_ISLE, PortLocation.JATIZSO, PortLocation.NEITIZNOT, PortLocation.ETCETERIA,
		PortLocation.RED_ROCK));

	private static final String SHORTEST_PATH = "shortestpath";

	private final PortTasksConfig config;
	private final BoatLocator boats;
	private final EventBus eventBus;
	/** The leg last sent to Shortest Path (start and target ports), or nulls if we haven't set one. */
	private PortLocation shortestPathStart;
	private PortLocation shortestPathTarget;
	private final RouteGraph graph = new RouteGraph();
	private final RoutePlanner planner = new RoutePlanner(graph::distance);

	private RoutePlanner.Plan plan;
	private PortLocation planStart;
	private List<WorldPoint> nextLeg = Collections.emptyList();
	private final List<String> outOfRegion = new ArrayList<>();

	public RoutingService(PortTasksConfig config, BoatLocator boats, EventBus eventBus)
	{
		this.config = config;
		this.boats = boats;
		this.eventBus = eventBus;
	}

	/**
	 * True when routing is on and there is a plan; the per-task paths are then hidden. There may be no leg to
	 * draw (all remaining work is at the boat's port), which must not bring the per-task paths back.
	 */
	public boolean isActive()
	{
		return config.routingEnabled() && plan != null;
	}

	/** True when our overlays should draw the leg themselves (there is one, and Shortest Path isn't drawing it). */
	public boolean drawsOwnLeg()
	{
		return isActive() && !nextLeg.isEmpty() && !config.routingUseShortestPath();
	}

	public List<WorldPoint> nextLeg()
	{
		return nextLeg;
	}

	public RoutePlanner.Plan plan()
	{
		return plan;
	}

	/** Port where the boat is docked, or null (at sea / unknown). */
	public PortLocation boatPort()
	{
		return boats.boatPort();
	}

	public RouteGraph graph()
	{
		return graph;
	}

	public void replan(List<CourierTask> tasks)
	{
		if (!config.routingEnabled())
		{
			clear();
			return;
		}
		PortLocation start = boats.boatPort();
		if (start == null)
		{
			log.debug("[routing] boat not at a known port; keeping the current plan");
			return;
		}

		List<RoutePlanner.TaskState> states = new ArrayList<>();
		outOfRegion.clear();
		for (CourierTask t : tasks)
		{
			int crates = t.getData().cargoAmount;
			if (t.getDelivered() >= crates)
			{
				continue;
			}
			PortLocation from = t.getData().getCargoLocation();
			PortLocation to = t.getData().getDeliveryLocation();
			if (config.routingWestOnly() && (!WEST.contains(from) || !WEST.contains(to)))
			{
				outOfRegion.add(t.getData().taskName);
				continue;
			}
			states.add(new RoutePlanner.TaskState(t.getData().getId(), from, to, t.getCargoTaken() < crates));
		}
		if (states.isEmpty())
		{
			clear();
			return;
		}

		plan = planner.plan(start, states, config.routingEnd().port(), config.routingStopCost());
		planStart = start;
		PortLocation target = null;
		for (RoutePlanner.Stop s : plan.stops)
		{
			if (s.port != start)
			{
				target = s.port;
				break;
			}
		}
		if (target == null && config.routingEnd().port() != null && config.routingEnd().port() != start)
		{
			target = config.routingEnd().port();
		}
		nextLeg = target == null ? Collections.emptyList() : graph.points(start, target);
		updateShortestPath(start, target);
		log.info("[routing] from {}: {}{}", start.getName(), plan,
			outOfRegion.isEmpty() ? "" : " | out of region: " + outOfRegion);
	}

	public void clear()
	{
		plan = null;
		planStart = null;
		nextLeg = Collections.emptyList();
		updateShortestPath(null, null);
	}

	/**
	 * Forget what Shortest Path was told, so the next plan re-sends it. Called on logout and world hop,
	 * where Shortest Path may have dropped its path.
	 */
	public void resetShortestPath()
	{
		shortestPathStart = null;
		shortestPathTarget = null;
	}

	/**
	 * Points Shortest Path at the next stop's dock, or clears the target we set. Only sends on change.
	 *
	 * The start is given explicitly (the boat's dock) rather than left to Shortest Path, which would use the
	 * player's position: that fails right after login (no player yet, and the request is silently dropped) and
	 * on a boat (the player is in the boat's own coordinates).
	 */
	private void updateShortestPath(PortLocation start, PortLocation target)
	{
		if (!config.routingUseShortestPath() || !config.routingEnabled() || target == null)
		{
			start = null;
			target = null;
		}
		if (target == shortestPathTarget && start == shortestPathStart)
		{
			return;
		}
		if (target == null)
		{
			eventBus.post(new PluginMessage(SHORTEST_PATH, "clear"));
		}
		else
		{
			Map<String, Object> data = new HashMap<>();
			data.put("start", start.getNavigationLocation());
			data.put("target", target.getNavigationLocation());
			eventBus.post(new PluginMessage(SHORTEST_PATH, "path", data));
		}
		log.info("[routing] Shortest Path leg: {} -> {}", start, target);
		shortestPathStart = start;
		shortestPathTarget = target;
	}
}
