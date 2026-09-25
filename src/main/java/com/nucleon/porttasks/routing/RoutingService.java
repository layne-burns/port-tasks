package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.CourierTask;
import com.nucleon.porttasks.PortTasksConfig;
import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Keeps the optimal plan for the active courier tasks and the next leg to draw (SPEC-routing.md §2.1, §2.3).
 * Re-planned from the boat's port whenever tasks, cargo or the boat's port change. While the boat is at sea
 * (no known port) the last plan is kept, so the leg being sailed stays on screen. Client thread only.
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

	private final PortTasksConfig config;
	private final BoatLocator boats;
	private final RouteGraph graph = new RouteGraph();
	private final RoutePlanner planner = new RoutePlanner(graph::distance);

	private RoutePlanner.Plan plan;
	private PortLocation planStart;
	private List<WorldPoint> nextLeg = Collections.emptyList();
	private final List<String> outOfRegion = new ArrayList<>();

	public RoutingService(PortTasksConfig config, BoatLocator boats)
	{
		this.config = config;
		this.boats = boats;
	}

	/** True when routing is on and there is a leg to draw; overlays then draw only {@link #nextLeg()}. */
	public boolean isActive()
	{
		return config.routingEnabled() && !nextLeg.isEmpty();
	}

	public List<WorldPoint> nextLeg()
	{
		return nextLeg;
	}

	public RoutePlanner.Plan plan()
	{
		return plan;
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
		log.info("[routing] from {}: {}{}", start.getName(), plan,
			outOfRegion.isEmpty() ? "" : " | out of region: " + outOfRegion);
	}

	public void clear()
	{
		plan = null;
		planStart = null;
		nextLeg = Collections.emptyList();
	}
}
