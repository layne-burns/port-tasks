package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.CourierTask;
import com.nucleon.porttasks.PortTasksConfig;
import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Keeps the optimal plan for the active courier tasks (SPEC-routing.md §2.1, §2.3). Re-planned from the boat's
 * port whenever tasks, cargo or the boat's port change. While the boat is at sea (no known port) the last
 * plan is kept, so the leg being sailed stays on screen. Client thread only.
 *
 * The plan uses our port graph (it needs a cost for every pair of ports); the leg to the next stop is drawn
 * by the Shortest Path plugin: we send it the next stop's dock as its target over its PluginMessage API
 * ("shortestpath" / "path", "clear") and it draws the route. It doesn't really model sailing (its sea tiles
 * are just open tiles, joined to the land), so each leg is sent with transports switched off and with a sea
 * tile for docks whose own tile is blocked.
 */
@Slf4j
public final class RoutingService
{
	private static final String SHORTEST_PATH = "shortestpath";

	/**
	 * Docks whose navigation tile is blocked in Shortest Path's collision map, mapped to the nearest open sea
	 * tile. At a blocked tile Shortest Path can't finish at sea and goes for the nearest tile it can reach,
	 * which at the Summer Shore meant a bank trip for a dramen staff and fairy ring CJQ onto the island.
	 * Found by flood-filling its collision map (upstream, 24 September 2026); every other port's tile is open.
	 */
	private static final Map<PortLocation, WorldPoint> SHORTEST_PATH_DOCK = Collections.singletonMap(
		PortLocation.SUMMER_SHORE, new WorldPoint(3174, 2365, 0));

	/**
	 * Shortest Path settings for a sailing leg: no transports, teleports or bank detours, only the sea. Its
	 * collision map joins the sea to the land, so otherwise a teleport near the destination can beat sailing.
	 * Shortest Path drops these on "clear".
	 */
	private static final Map<String, Object> SHORTEST_PATH_SAILING;

	static
	{
		Map<String, Object> m = new HashMap<>();
		for (String key : new String[]{"useAgilityShortcuts", "useGrappleShortcuts", "useBoats", "useCanoes",
			"useCharterShips", "useShips", "useFairyRings", "useGnomeGliders", "useHotAirBalloons", "useMagicCarpets",
			"useMagicMushtrees", "useMinecarts", "useQuetzals", "useSpiritTrees",
			"useTeleportationLevers", "useTeleportationPortals", "useTeleportationSpells", "useTeleportationSpellsHome",
			"useTeleportationMinigames", "useWildernessObelisks", "useSeasonalTransports", "includeBankPath", "usePoh"})
		{
			m.put(key, false);
		}
		// Not a boolean in Shortest Path: it takes the setting's label.
		m.put("useTeleportationItems", "None");
		SHORTEST_PATH_SAILING = Collections.unmodifiableMap(m);
	}

	private final PortTasksConfig config;
	private final BoatLocator boats;
	private final EventBus eventBus;
	/** The sailing leg wanted (start and target ports), or nulls for none. */
	private PortLocation shortestPathStart;
	private PortLocation shortestPathTarget;
	/**
	 * Loop mode's gather target: a notice board to reach on foot, with the player's own Shortest Path settings
	 * (teleports and all). While set it wins over the sailing leg. Null for none.
	 */
	private WorldPoint landTarget;
	/**
	 * The bounty hunt's sea target (a monster's spawn area or safespot) and where the boat was when it was
	 * chosen: sailed to with the sailing settings, and while set it wins over the courier leg. Null for none.
	 */
	private WorldPoint seaTarget;
	private WorldPoint seaStart;
	/**
	 * What Shortest Path was last told ("clear", a sailing leg or a land target), or null to resend. Starts as
	 * "clear" so a path the player set themselves isn't cleared before we have one of our own.
	 */
	private String sent = "clear";
	private final RouteGraph graph = new RouteGraph();
	private final RoutePlanner planner = new RoutePlanner(graph::distance);

	private RoutePlanner.Plan plan;
	private PortLocation planStart;

	public RoutingService(PortTasksConfig config, BoatLocator boats, EventBus eventBus)
	{
		this.config = config;
		this.boats = boats;
		this.eventBus = eventBus;
	}

	public RoutePlanner.Plan plan()
	{
		return plan;
	}

	/** The first port in the plan other than where it started (the next place to sail to), or null. */
	public PortLocation nextStop()
	{
		if (plan == null)
		{
			return null;
		}
		for (RoutePlanner.Stop s : plan.stops)
		{
			if (s.port != planStart)
			{
				return s.port;
			}
		}
		return null;
	}

	/** True if the plan delivers something at this port. */
	public boolean hasDeliveriesAt(PortLocation port)
	{
		if (plan != null)
		{
			for (RoutePlanner.Stop s : plan.stops)
			{
				if (s.port == port && !s.deliveries.isEmpty())
				{
					return true;
				}
			}
		}
		return false;
	}

	/** True if the plan has a pickup or delivery at this port. */
	public boolean hasWorkAt(PortLocation port)
	{
		if (plan != null)
		{
			for (RoutePlanner.Stop s : plan.stops)
			{
				if (s.port == port)
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Port where the boat was last docked (kept while at sea): the start for planning. Null if unknown. */
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
		PortLocation start = boats.boatPort();
		if (start == null)
		{
			log.debug("[routing] boat not at a known port; keeping the current plan");
			return;
		}

		List<RoutePlanner.TaskState> states = new ArrayList<>();
		for (CourierTask t : tasks)
		{
			int crates = t.getData().cargoAmount;
			if (t.getDelivered() >= crates)
			{
				continue;
			}
			states.add(new RoutePlanner.TaskState(t.getData().getId(), t.getData().getCargoLocation(),
				t.getData().getDeliveryLocation(), t.getCargoTaken() < crates));
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
		updateShortestPath(start, target);
		log.debug("[routing] from {}: {}", start.getName(), plan);
	}

	public void clear()
	{
		plan = null;
		planStart = null;
		updateShortestPath(null, null);
	}

	/**
	 * Forget what Shortest Path was told, so the next plan re-sends it. Called on logout and world hop,
	 * where Shortest Path may have dropped its path.
	 */
	public void resetShortestPath()
	{
		if (!"clear".equals(sent))
		{
			sent = null;
		}
	}

	/** Bounty hunt at sea: a point to sail to from {@code start} (the boat), or null to go back to the courier leg. */
	public void setSeaTarget(WorldPoint start, WorldPoint target)
	{
		if (target == null || !target.equals(seaTarget))
		{
			seaStart = start;
		}
		seaTarget = target;
		publish();
	}

	/** Loop mode: a notice board to walk or teleport to, or null to go back to the sailing leg. */
	public void setLandTarget(WorldPoint target)
	{
		landTarget = target;
		publish();
	}

	/** Records the sailing leg wanted (the next stop's dock, or none) and tells Shortest Path if it changed. */
	private void updateShortestPath(PortLocation start, PortLocation target)
	{
		shortestPathStart = target == null ? null : start;
		shortestPathTarget = target;
		publish();
	}

	/**
	 * Tells Shortest Path the land target if there is one, else the sailing leg, else clears what we set. Only
	 * sends on change.
	 *
	 * A sailing leg's start is given explicitly (the boat's dock) rather than left to Shortest Path, which would
	 * use the player's position: that fails right after login (no player yet, and the request is silently
	 * dropped) and on a boat (the player is in the boat's own coordinates). A land target has no start: it is
	 * walked from wherever the player is. Before it, "clear" drops a sailing leg's settings override, so the
	 * player's own teleports are used.
	 */
	private void publish()
	{
		String want = landTarget != null ? "land " + landTarget
			: seaTarget != null ? "sea " + seaTarget
			: shortestPathTarget != null ? "sail " + shortestPathStart + " > " + shortestPathTarget : "clear";
		if (want.equals(sent))
		{
			return;
		}
		if (landTarget != null)
		{
			eventBus.post(new PluginMessage(SHORTEST_PATH, "clear"));
			Map<String, Object> data = new HashMap<>();
			data.put("target", landTarget);
			eventBus.post(new PluginMessage(SHORTEST_PATH, "path", data));
		}
		else if (seaTarget != null)
		{
			// Sailed like a courier leg: sea only, from where the boat was (the player is in the boat's coordinates).
			Map<String, Object> data = new HashMap<>();
			if (seaStart != null)
			{
				data.put("start", seaStart);
			}
			data.put("target", seaTarget);
			data.put("config", SHORTEST_PATH_SAILING);
			eventBus.post(new PluginMessage(SHORTEST_PATH, "path", data));
		}
		else if (shortestPathTarget != null)
		{
			Map<String, Object> data = new HashMap<>();
			data.put("start", shortestPathDock(shortestPathStart));
			data.put("target", shortestPathDock(shortestPathTarget));
			data.put("config", SHORTEST_PATH_SAILING);
			eventBus.post(new PluginMessage(SHORTEST_PATH, "path", data));
		}
		else
		{
			eventBus.post(new PluginMessage(SHORTEST_PATH, "clear"));
		}
		log.debug("[routing] Shortest Path: {}", want);
		sent = want;
	}

	private static WorldPoint shortestPathDock(PortLocation port)
	{
		return SHORTEST_PATH_DOCK.getOrDefault(port, port.getNavigationLocation());
	}
}
