package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

/**
 * Suggests loops (SPEC-routing.md §2.4.1): sets of 2 to 4 ports whose notice-board pools hold many
 * worthwhile tasks that never leave the set. Boards show a random draw from a fixed pool per port, so the
 * pool is what a loop can count on. Pure; runs off the client thread.
 *
 * For a port set L, the tasks in it are the pool tasks whose board, pickup and delivery are all in L, and
 *   loop tiles(L) = the shortest cycle through L (twice the distance for two ports),
 *   density(L)    = Σ XP of the tasks in L / loop tiles(L).
 * Loops are ranked by density, and only those with at least {@link #TASKS_PER_PORT} tasks per port qualify,
 * so a board is unlikely to come up empty: without the floor, two close ports sharing a couple of tasks win.
 */
public final class LoopSuggester
{
	/** A pool task that may be taken: allowed bag size, level, ports. */
	public static final class Candidate
	{
		final PortLocation board;
		final PortLocation pickup;
		final PortLocation delivery;
		final int xp;

		public Candidate(PortLocation board, PortLocation pickup, PortLocation delivery, int xp)
		{
			this.board = board;
			this.pickup = pickup;
			this.delivery = delivery;
			this.xp = xp;
		}
	}

	/** One suggested loop. Immutable. */
	public static final class Suggestion
	{
		/** In the order of the shortest cycle through them. */
		public final List<PortLocation> ports;
		public final int tasks;
		public final int xp;
		public final double loopTiles;

		Suggestion(List<PortLocation> ports, int tasks, int xp, double loopTiles)
		{
			this.ports = Collections.unmodifiableList(ports);
			this.tasks = tasks;
			this.xp = xp;
			this.loopTiles = loopTiles;
		}

		/** Pool XP per 1,000 tiles of loop. */
		public double density()
		{
			return 1000 * xp / loopTiles;
		}
	}

	public static final int TASKS_PER_PORT = 3;
	private static final int MAX_PORTS = 4;

	private final ToDoubleBiFunction<PortLocation, PortLocation> distance;

	public LoopSuggester(ToDoubleBiFunction<PortLocation, PortLocation> distance)
	{
		this.distance = distance;
	}

	/** The best {@code limit} loops over the given ports, best first. */
	public List<Suggestion> suggest(Collection<Candidate> pool, Set<PortLocation> usable, int limit)
	{
		List<PortLocation> ports = new ArrayList<>(usable);
		ports.remove(PortLocation.EMPTY);
		int n = ports.size();
		// Each task as a bit mask over the usable ports; tasks touching an unusable port can't be in any loop.
		List<long[]> tasks = new ArrayList<>();
		for (Candidate c : pool)
		{
			int b = ports.indexOf(c.board);
			int p = ports.indexOf(c.pickup);
			int d = ports.indexOf(c.delivery);
			if (b >= 0 && p >= 0 && d >= 0)
			{
				tasks.add(new long[]{(1L << b) | (1L << p) | (1L << d), c.xp});
			}
		}

		List<Suggestion> out = new ArrayList<>();
		int[] pick = new int[MAX_PORTS];
		for (int k = 2; k <= Math.min(MAX_PORTS, n); k++)
		{
			combinations(ports, tasks, pick, 0, 0, k, out);
		}
		out.sort(Comparator.comparingDouble(Suggestion::density).reversed());
		return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
	}

	private void combinations(List<PortLocation> ports, List<long[]> tasks, int[] pick, int depth, int from, int k,
		List<Suggestion> out)
	{
		if (depth == k)
		{
			evaluate(ports, tasks, pick, k, out);
			return;
		}
		for (int i = from; i < ports.size(); i++)
		{
			pick[depth] = i;
			combinations(ports, tasks, pick, depth + 1, i + 1, k, out);
		}
	}

	private void evaluate(List<PortLocation> ports, List<long[]> tasks, int[] pick, int k, List<Suggestion> out)
	{
		long mask = 0;
		for (int i = 0; i < k; i++)
		{
			mask |= 1L << pick[i];
		}
		int count = 0;
		int xp = 0;
		for (long[] t : tasks)
		{
			if ((t[0] & ~mask) == 0)
			{
				count++;
				xp += (int) t[1];
			}
		}
		if (count < TASKS_PER_PORT * k)
		{
			return;
		}
		List<PortLocation> set = new ArrayList<>();
		for (int i = 0; i < k; i++)
		{
			set.add(ports.get(pick[i]));
		}
		List<PortLocation> cycle = shortestCycle(set);
		double tiles = cycleLength(cycle);
		if (Double.isFinite(tiles) && tiles > 0)
		{
			out.add(new Suggestion(cycle, count, xp, tiles));
		}
	}

	/** The order of these (at most 4) ports with the shortest closed tour; the first port stays first. */
	List<PortLocation> shortestCycle(List<PortLocation> set)
	{
		if (set.size() <= 3)
		{
			return set; // every cycle through 2 or 3 points has the same length
		}
		List<PortLocation> best = null;
		double bestLength = Double.POSITIVE_INFINITY;
		PortLocation a = set.get(0);
		for (int[] order : new int[][]{{1, 2, 3}, {1, 3, 2}, {2, 1, 3}})
		{
			List<PortLocation> c = List.of(a, set.get(order[0]), set.get(order[1]), set.get(order[2]));
			double length = cycleLength(c);
			if (length < bestLength)
			{
				bestLength = length;
				best = c;
			}
		}
		return best;
	}

	double cycleLength(List<PortLocation> cycle)
	{
		double total = 0;
		for (int i = 0; i < cycle.size(); i++)
		{
			total += distance.applyAsDouble(cycle.get(i), cycle.get((i + 1) % cycle.size()));
		}
		return total;
	}

	/** Every port but the placeholder: the starting point for the usable set. */
	public static Set<PortLocation> allPorts()
	{
		Set<PortLocation> s = EnumSet.allOf(PortLocation.class);
		s.remove(PortLocation.EMPTY);
		return s;
	}
}
