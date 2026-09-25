package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact stop-order optimiser for courier tasks (SPEC-routing.md §2.1).
 *
 * Each unfinished task contributes a pickup event at its cargo port (if crates remain there) and a delivery
 * event at its destination; a delivery is only possible after its pickup. Arriving at a port does every event
 * possible there (never worse than postponing it), so a plan is just an order of ports. Cost = tiles sailed +
 * stopCost per port visited (+ the leg to a fixed end port, if one is set). Solved exactly by memoised search
 * over (current port, events done): with at most 5 tasks there are at most 10 events.
 *
 * Capacity is not modelled (hold of 160 never binds, per the user).
 */
public final class RoutePlanner
{
	/** Sailing cost between two ports, in tiles. */
	@FunctionalInterface
	public interface ToDistance
	{
		double apply(PortLocation from, PortLocation to);
	}

	/** A task's state, as far as routing is concerned. */
	public static final class TaskState
	{
		public final int taskId;
		public final PortLocation cargoPort;
		public final PortLocation destination;
		/** True if crates still have to be collected from the cargo port. */
		public final boolean needsPickup;

		public TaskState(int taskId, PortLocation cargoPort, PortLocation destination, boolean needsPickup)
		{
			this.taskId = taskId;
			this.cargoPort = cargoPort;
			this.destination = destination;
			this.needsPickup = needsPickup;
		}
	}

	/** One port visit in a plan and what happens there. */
	public static final class Stop
	{
		public final PortLocation port;
		public final List<Integer> pickups;
		public final List<Integer> deliveries;

		Stop(PortLocation port, List<Integer> pickups, List<Integer> deliveries)
		{
			this.port = port;
			this.pickups = pickups;
			this.deliveries = deliveries;
		}

		@Override
		public String toString()
		{
			return port.getName() + (pickups.isEmpty() ? "" : " pick up " + pickups)
				+ (deliveries.isEmpty() ? "" : " deliver " + deliveries);
		}
	}

	public static final class Plan
	{
		public final List<Stop> stops;
		/** Tiles sailed plus stop costs (plus the leg to the end port). */
		public final double cost;
		public final double sailedTiles;

		Plan(List<Stop> stops, double cost, double sailedTiles)
		{
			this.stops = stops;
			this.cost = cost;
			this.sailedTiles = sailedTiles;
		}

		@Override
		public String toString()
		{
			return String.format("%.0f (%.0f tiles) %s", cost, sailedTiles, stops);
		}
	}

	private final ToDistance distance;

	public RoutePlanner(ToDistance distance)
	{
		this.distance = distance;
	}

	/**
	 * The cheapest plan from {@code start} that finishes every task.
	 *
	 * @param end      port the boat should finish at, or null for an open route
	 * @param stopCost cost of one port visit, in tile-equivalents
	 */
	public Plan plan(PortLocation start, List<TaskState> tasks, PortLocation end, double stopCost)
	{
		return new Search(start, tasks, end, stopCost).run();
	}

	/** One optimisation; events are numbered 2i (pickup of task i) and 2i+1 (delivery of task i). */
	private final class Search
	{
		private final PortLocation start;
		private final List<TaskState> tasks;
		private final PortLocation end;
		private final double stopCost;
		private final int allDone;
		private final List<PortLocation> ports;
		private final Map<Long, Double> memo = new HashMap<>();
		private final Map<Long, Integer> choice = new HashMap<>();

		Search(PortLocation start, List<TaskState> tasks, PortLocation end, double stopCost)
		{
			this.start = start;
			this.tasks = tasks;
			this.end = end;
			this.stopCost = stopCost;
			this.allDone = (1 << (2 * tasks.size())) - 1;
			Set<PortLocation> p = new LinkedHashSet<>();
			p.add(start);
			for (TaskState t : tasks)
			{
				p.add(t.cargoPort);
				p.add(t.destination);
			}
			this.ports = new ArrayList<>(p);
		}

		Plan run()
		{
			int done0 = 0;
			for (int i = 0; i < tasks.size(); i++)
			{
				if (!tasks.get(i).needsPickup)
				{
					done0 |= 1 << (2 * i);
				}
			}
			// Whatever can be done at the start port is done before leaving.
			int done = arrive(start, done0);
			double cost = best(0, done);

			List<Stop> stops = new ArrayList<>();
			Stop first = stopAt(start, done0, done);
			if (first != null)
			{
				stops.add(first);
			}
			double sailed = 0;
			int at = 0;
			while (done != allDone)
			{
				int nextPort = choice.get(key(at, done));
				int after = arrive(ports.get(nextPort), done);
				stops.add(stopAt(ports.get(nextPort), done, after));
				sailed += distance.apply(ports.get(at), ports.get(nextPort));
				at = nextPort;
				done = after;
			}
			if (end != null)
			{
				sailed += distance.apply(ports.get(at), end);
			}
			return new Plan(Collections.unmodifiableList(stops), cost, sailed);
		}

		/** Minimum remaining cost from port index {@code at} with events {@code done} completed. */
		private double best(int at, int done)
		{
			if (done == allDone)
			{
				return end == null ? 0 : distance.apply(ports.get(at), end);
			}
			long k = key(at, done);
			Double cached = memo.get(k);
			if (cached != null)
			{
				return cached;
			}
			double bestCost = Double.POSITIVE_INFINITY;
			int bestPort = -1;
			for (int p = 0; p < ports.size(); p++)
			{
				if (p == at)
				{
					continue;
				}
				int after = arrive(ports.get(p), done);
				if (after == done)
				{
					continue; // nothing to do there yet
				}
				double c = distance.apply(ports.get(at), ports.get(p)) + stopCost + best(p, after);
				if (c < bestCost)
				{
					bestCost = c;
					bestPort = p;
				}
			}
			memo.put(k, bestCost);
			choice.put(k, bestPort);
			return bestCost;
		}

		/** Events done after arriving at {@code port}: all its pickups, then deliveries whose pickup is done. */
		private int arrive(PortLocation port, int done)
		{
			for (int i = 0; i < tasks.size(); i++)
			{
				if (tasks.get(i).cargoPort == port)
				{
					done |= 1 << (2 * i);
				}
			}
			for (int i = 0; i < tasks.size(); i++)
			{
				if (tasks.get(i).destination == port && (done & (1 << (2 * i))) != 0)
				{
					done |= 1 << (2 * i + 1);
				}
			}
			return done;
		}

		private Stop stopAt(PortLocation port, int before, int after)
		{
			List<Integer> pickups = new ArrayList<>();
			List<Integer> deliveries = new ArrayList<>();
			for (int i = 0; i < tasks.size(); i++)
			{
				if ((before & (1 << (2 * i))) == 0 && (after & (1 << (2 * i))) != 0)
				{
					pickups.add(tasks.get(i).taskId);
				}
				if ((before & (1 << (2 * i + 1))) == 0 && (after & (1 << (2 * i + 1))) != 0)
				{
					deliveries.add(tasks.get(i).taskId);
				}
			}
			return pickups.isEmpty() && deliveries.isEmpty() ? null : new Stop(port, pickups, deliveries);
		}

		private long key(int at, int done)
		{
			return ((long) at << 32) | done;
		}
	}
}
