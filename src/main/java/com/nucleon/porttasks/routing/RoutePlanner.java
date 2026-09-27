package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exact stop-order optimiser for courier tasks (SPEC-routing.md §2.1).
 *
 * Each unfinished task contributes a pickup event at its cargo port (if crates remain there) and a delivery
 * event at its destination; a delivery is only possible after its pickup. Arriving at a port does every event
 * possible there (never worse than postponing it), so a plan is just an order of ports. Cost = tiles sailed +
 * stopCost per port visited (+ the leg to a fixed end port, if one is set). Solved exactly by dynamic
 * programming over (current port, events done): with at most 5 tasks there are at most 10 events, and only
 * 3^5 = 243 combinations of them can occur, so a plan takes tens of microseconds.
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
	/** Scratch space reused between plans (best cost and next port per state); see Search. */
	private double[] bestBuf = new double[0];
	private int[] choiceBuf = new int[0];

	public RoutePlanner(ToDistance distance)
	{
		this.distance = distance;
	}

	/**
	 * The cheapest plan from {@code start} that finishes every task. Not thread-safe (it reuses scratch
	 * arrays); each thread needs its own planner.
	 *
	 * @param end      port the boat should finish at, or null for an open route
	 * @param stopCost cost of one port visit, in tile-equivalents
	 */
	public Plan plan(PortLocation start, List<TaskState> tasks, PortLocation end, double stopCost)
	{
		return new Search(start, tasks, end, stopCost).run();
	}

	/** The most tasks one plan can hold (the game's five task slots). */
	public static final int MAX_TASKS = 5;

	/** Masks of t tasks where no delivery bit is set without its pickup bit, largest first; by t. */
	private static final int[][] VALID_MASKS = new int[MAX_TASKS + 1][];

	static
	{
		for (int t = 0; t < VALID_MASKS.length; t++)
		{
			List<Integer> masks = new ArrayList<>();
			for (int m = (1 << (2 * t)) - 1; m >= 0; m--)
			{
				// Delivery bits (odd) shifted onto their pickup bits must all be set pickups.
				int deliveries = (m >> 1) & 0x155;
				int pickups = m & 0x155;
				if ((deliveries & ~pickups) == 0)
				{
					masks.add(m);
				}
			}
			VALID_MASKS[t] = masks.stream().mapToInt(Integer::intValue).toArray();
		}
	}

	/**
	 * One optimisation; events are numbered 2i (pickup of task i) and 2i+1 (delivery of task i), so a task is
	 * in one of three states and only 3^t of the 4^t masks occur. The search fills best(port, done) for every
	 * valid mask from all-done downwards (arriving somewhere only ever adds bits, so the state it leads to is
	 * already known).
	 */
	private final class Search
	{
		private final PortLocation start;
		private final List<TaskState> tasks;
		private final PortLocation end;
		private final double stopCost;
		private final List<PortLocation> ports;
		/** Per port: pickup bits of tasks loading there, and pickup bits of tasks delivering there. */
		private final int[] pickupsAt;
		private final int[] deliverableAt;
		private final double[][] dist;
		private final int masks;

		Search(PortLocation start, List<TaskState> tasks, PortLocation end, double stopCost)
		{
			if (tasks.size() > MAX_TASKS)
			{
				throw new IllegalArgumentException("at most " + MAX_TASKS + " tasks");
			}
			this.start = start;
			this.tasks = tasks;
			this.end = end;
			this.stopCost = stopCost;
			Set<PortLocation> p = new LinkedHashSet<>();
			p.add(start);
			for (TaskState t : tasks)
			{
				p.add(t.cargoPort);
				p.add(t.destination);
			}
			this.ports = new ArrayList<>(p);
			int n = ports.size();
			pickupsAt = new int[n];
			deliverableAt = new int[n];
			for (int i = 0; i < tasks.size(); i++)
			{
				pickupsAt[ports.indexOf(tasks.get(i).cargoPort)] |= 1 << (2 * i);
				deliverableAt[ports.indexOf(tasks.get(i).destination)] |= 1 << (2 * i);
			}
			dist = new double[n][n];
			for (int a = 0; a < n; a++)
			{
				for (int b = 0; b < n; b++)
				{
					dist[a][b] = a == b ? 0 : distance.apply(ports.get(a), ports.get(b));
				}
			}
			masks = 1 << (2 * tasks.size());
			if (bestBuf.length < n * masks)
			{
				bestBuf = new double[n * masks];
				choiceBuf = new int[n * masks];
			}
		}

		/** Events done after arriving at port index {@code p}: its pickups, then deliveries whose pickup is done. */
		private int arrive(int p, int done)
		{
			int after = done | pickupsAt[p];
			return after | (after & deliverableAt[p]) << 1;
		}

		Plan run()
		{
			int n = ports.size();
			int allDone = masks - 1;
			int done0 = 0;
			for (int i = 0; i < tasks.size(); i++)
			{
				if (!tasks.get(i).needsPickup)
				{
					done0 |= 1 << (2 * i);
				}
			}

			for (int done : VALID_MASKS[tasks.size()])
			{
				if ((done & done0) != done0)
				{
					continue; // never reached: those pickups are already done
				}
				for (int at = 0; at < n; at++)
				{
					double best;
					int choice = -1;
					if (done == allDone)
					{
						best = end == null ? 0 : distance.apply(ports.get(at), end);
					}
					else
					{
						best = Double.POSITIVE_INFINITY;
						for (int p = 0; p < n; p++)
						{
							int after = p == at ? done : arrive(p, done);
							if (after == done)
							{
								continue; // nothing to do there yet
							}
							double c = dist[at][p] + stopCost + bestBuf[p * masks + after];
							if (c < best)
							{
								best = c;
								choice = p;
							}
						}
					}
					bestBuf[at * masks + done] = best;
					choiceBuf[at * masks + done] = choice;
				}
			}

			// Whatever can be done at the start port is done before leaving.
			int done = arrive(0, done0);
			double cost = bestBuf[done];
			List<Stop> stops = new ArrayList<>();
			Stop first = stopAt(ports.get(0), done0, done);
			if (first != null)
			{
				stops.add(first);
			}
			double sailed = 0;
			int at = 0;
			while (done != allDone)
			{
				int next = choiceBuf[at * masks + done];
				int after = arrive(next, done);
				stops.add(stopAt(ports.get(next), done, after));
				sailed += dist[at][next];
				at = next;
				done = after;
			}
			if (end != null)
			{
				sailed += distance.apply(ports.get(at), end);
			}
			return new Plan(Collections.unmodifiableList(stops), cost, sailed);
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
	}
}
