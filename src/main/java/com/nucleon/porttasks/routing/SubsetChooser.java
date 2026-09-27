package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses which offered tasks to take: the set S of at most {@code slots} offered tasks that maximises the
 * rate of the whole plan,
 *
 *   rate(S) = (reward of the held tasks + reward of S) / cost of the best route doing all of them,
 *
 * where cost is tiles sailed plus the stop cost per port (RoutePlanner). The empty set is a candidate, so
 * "take nothing" is the answer when nothing on the board improves the rate.
 *
 * Why this ratio: sailing on without teleports is a sequence of cycles (take tasks, do them, take more), and
 * the long-run rate is reward per cycle over tiles per cycle. Choosing the cycle with the best ratio is
 * Dinkelbach's criterion: equivalent to taking a set exactly when it pays more per tile than your going
 * rate. Unlike the marginal ratio (added reward / added tiles), it doesn't favour a tiny free ride over a
 * large set that is better overall.
 *
 * Every set of up to {@code slots} tasks is tried (sum of C(n, i), i <= slots; about 1,600 for 12 tasks and 5
 * slots, at tens of microseconds each). If that exceeds {@link #MAX_SETS}, only the strongest candidates on
 * their own are kept, and the result says it isn't exact. Pure and thread-safe per instance: it may run off
 * the client thread, with inputs gathered on it.
 */
public final class SubsetChooser
{
	/** Enough for 15 offered tasks with 5 free slots; beyond that the weakest candidates are dropped. */
	static final int MAX_SETS = 5_000;

	/** One offered task: its routing state and reward (XP or expected coins). Immutable. */
	public static final class Candidate
	{
		public final int dbrow;
		public final RoutePlanner.TaskState task;
		public final double reward;

		public Candidate(int dbrow, RoutePlanner.TaskState task, double reward)
		{
			this.dbrow = dbrow;
			this.task = task;
			this.reward = reward;
		}
	}

	/** The chosen set. Immutable. */
	public static final class Result
	{
		/** The offered tasks to take (dbrows); empty means take nothing. */
		public final List<Integer> dbrows;
		/** Reward per tile of the plan with them. */
		public final double rate;
		/** Reward per tile of the held tasks alone (0 if none). */
		public final double heldRate;
		public final int slots;
		/** Sets tried. */
		public final int evaluated;
		/** False if some weak candidates were left out to keep the search small. */
		public final boolean exact;

		Result(List<Integer> dbrows, double rate, double heldRate, int slots, int evaluated, boolean exact)
		{
			this.dbrows = dbrows;
			this.rate = rate;
			this.heldRate = heldRate;
			this.slots = slots;
			this.evaluated = evaluated;
			this.exact = exact;
		}
	}

	private final RoutePlanner planner;

	public SubsetChooser(RoutePlanner.ToDistance distance)
	{
		this.planner = new RoutePlanner(distance);
	}

	/**
	 * @param held       the unfinished held tasks
	 * @param heldReward their total reward
	 * @param slots      free task slots
	 * @param end        port to finish at, or null
	 */
	public Result choose(PortLocation start, List<RoutePlanner.TaskState> held, double heldReward,
		List<Candidate> candidates, int slots, PortLocation end, double stopCost)
	{
		double heldRate = held.isEmpty() ? 0 : heldReward / Math.max(planner.plan(start, held, end, stopCost).cost, 1);
		int k = Math.min(slots, RoutePlanner.MAX_TASKS - held.size());
		List<Candidate> pool = new ArrayList<>(candidates);
		boolean exact = true;
		if (k > 0 && setCount(pool.size(), k) > MAX_SETS)
		{
			// Keep the candidates that are best on their own.
			List<double[]> alone = new ArrayList<>();
			for (int i = 0; i < pool.size(); i++)
			{
				alone.add(new double[]{rate(start, held, heldReward, Collections.singletonList(pool.get(i)), end, stopCost), i});
			}
			alone.sort(Comparator.comparingDouble((double[] a) -> a[0]).reversed());
			List<Candidate> kept = new ArrayList<>();
			for (double[] a : alone)
			{
				if (setCount(kept.size() + 1, k) > MAX_SETS)
				{
					break;
				}
				kept.add(pool.get((int) a[1]));
			}
			pool = kept;
			exact = false;
		}

		Search search = new Search(start, held, heldReward, pool, end, stopCost);
		search.best = heldRate;
		search.bestSet = Collections.emptyList();
		if (k > 0)
		{
			search.extend(0, new ArrayList<>(), k);
		}
		return new Result(Collections.unmodifiableList(search.bestSet), search.best, heldRate, slots, search.evaluated + 1, exact);
	}

	private double rate(PortLocation start, List<RoutePlanner.TaskState> held, double heldReward, List<Candidate> chosen,
		PortLocation end, double stopCost)
	{
		List<RoutePlanner.TaskState> all = new ArrayList<>(held);
		double reward = heldReward;
		for (Candidate c : chosen)
		{
			all.add(c.task);
			reward += c.reward;
		}
		return reward / Math.max(planner.plan(start, all, end, stopCost).cost, 1);
	}

	/** Depth-first over sets of candidates in index order, so each set is tried once. */
	private final class Search
	{
		final PortLocation start;
		final List<RoutePlanner.TaskState> held;
		final double heldReward;
		final List<Candidate> pool;
		final PortLocation end;
		final double stopCost;
		double best;
		List<Integer> bestSet;
		double bestReward;
		int evaluated;

		Search(PortLocation start, List<RoutePlanner.TaskState> held, double heldReward, List<Candidate> pool,
			PortLocation end, double stopCost)
		{
			this.start = start;
			this.held = held;
			this.heldReward = heldReward;
			this.pool = pool;
			this.end = end;
			this.stopCost = stopCost;
		}

		void extend(int from, List<Candidate> chosen, int room)
		{
			for (int i = from; i < pool.size(); i++)
			{
				chosen.add(pool.get(i));
				double r = rate(start, held, heldReward, chosen, end, stopCost);
				double reward = chosen.stream().mapToDouble(c -> c.reward).sum();
				evaluated++;
				// Ties go to the set that earns more in total.
				if (r > best + 1e-9 || Math.abs(r - best) <= 1e-9 && reward > bestReward)
				{
					best = r;
					bestReward = reward;
					List<Integer> set = new ArrayList<>();
					for (Candidate c : chosen)
					{
						set.add(c.dbrow);
					}
					bestSet = set;
				}
				if (room > 1)
				{
					extend(i + 1, chosen, room - 1);
				}
				chosen.remove(chosen.size() - 1);
			}
		}
	}

	/** Number of sets of at most k of n items (including the empty set). */
	static long setCount(int n, int k)
	{
		long total = 0;
		long c = 1;
		for (int i = 0; i <= Math.min(k, n); i++)
		{
			total += c;
			c = c * (n - i) / (i + 1);
		}
		return total;
	}
}
