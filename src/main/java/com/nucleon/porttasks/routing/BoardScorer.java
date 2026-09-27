package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.CourierTask;
import com.nucleon.porttasks.CourierTaskData;
import com.nucleon.porttasks.PortTasksConfig;
import com.nucleon.porttasks.enums.PortLocation;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores the tasks offered on a notice board against the tasks already held (SPEC-routing.md §2.2):
 * for each offered task, the best plan with it minus the best plan without it is its added cost, and the
 * metrics follow from that. One offered task at a time (combinations are out of scope). Client thread only
 * (item values come from item definitions).
 */
public final class BoardScorer
{
	/** How offered tasks are ranked (config). */
	public enum RankBy
	{
		XP_PER_ADDED_TILE("XP per added tile"),
		VALUE_PER_ADDED_TILE("Value per added tile"),
		ROUTE_FIT("Route fit"),
		PLAN_RATE_AFTER("Plan rate after");

		private final String label;

		RankBy(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** One offered task's numbers. Immutable. */
	public static final class Score
	{
		public final int dbrow;
		public final String name;
		public final PortLocation destination;
		public final Integer xp;
		public final BagSize bag;
		public final double expectedValue;
		public final double addedCost;
		public final double addedTiles;
		public final int addedStops;
		/** Added cost / cost of doing the task on its own; 0 = rides entirely on the current route. */
		public final double routeFit;
		/** Sailing distance from the task's pickup to its delivery port. */
		public final double ownTiles;
		/** Added tiles / (2 x own tiles): 0 = free ride, 1 = as bad as sailing out and back just for it. */
		public final double detour;
		/** Board tint for {@link #detour}: pink for a free ride, then green through yellow to red. */
		public final Color detourColor;
		public final double xpPerAddedTile;
		public final double valuePerAddedTile;
		public final double planRateAfter;
		public final List<String> signatureDrops;
		public final List<String> wantedDrops;
		/** 1 = best under the ranking metric. */
		public int rank;

		Score(int dbrow, String name, PortLocation destination, Integer xp, BagSize bag, double expectedValue,
			double addedCost, double addedTiles, int addedStops, double routeFit, double xpPerAddedTile,
			double valuePerAddedTile, double planRateAfter, List<String> signatureDrops, List<String> wantedDrops,
			double ownTiles)
		{
			this.dbrow = dbrow;
			this.name = name;
			this.destination = destination;
			this.xp = xp;
			this.bag = bag;
			this.expectedValue = expectedValue;
			this.addedCost = addedCost;
			this.addedTiles = addedTiles;
			this.addedStops = addedStops;
			this.routeFit = routeFit;
			this.xpPerAddedTile = xpPerAddedTile;
			this.valuePerAddedTile = valuePerAddedTile;
			this.planRateAfter = planRateAfter;
			this.signatureDrops = signatureDrops;
			this.wantedDrops = wantedDrops;
			this.ownTiles = ownTiles;
			this.detour = ownTiles > 0 ? addedTiles / (2 * ownTiles) : Double.POSITIVE_INFINITY;
			this.detourColor = detourColor(addedTiles, ownTiles);
		}
	}

	private final RouteGraph graph;
	private final RoutePlanner planner;
	private final CourierWikiData wiki;
	private final XpLearner xp;
	private final RewardValuer valuer;
	private final WantedItems wanted;
	private final PortTasksConfig config;

	public BoardScorer(RouteGraph graph, CourierWikiData wiki, XpLearner xp, RewardValuer valuer, WantedItems wanted,
		PortTasksConfig config)
	{
		this.graph = graph;
		this.planner = new RoutePlanner(graph::distance);
		this.wiki = wiki;
		this.xp = xp;
		this.valuer = valuer;
		this.wanted = wanted;
		this.config = config;
	}

	/**
	 * Scores every offered courier task not already held, ranked by the configured metric. Tasks above
	 * {@code sailingLevel} (0 = unknown: no check) or filtered out by bag size are left out: they can't or
	 * shouldn't be taken.
	 */
	public List<Score> score(List<CourierTask> held, PortLocation start, Collection<CourierTaskData> offered, int sailingLevel)
	{
		PortLocation end = config.routingEnd().port();
		double stopCost = config.routingStopCost();

		List<RoutePlanner.TaskState> base = new ArrayList<>();
		Set<Integer> heldIds = new LinkedHashSet<>();
		int baseXp = 0;
		for (CourierTask t : held)
		{
			CourierTaskData d = t.getData();
			heldIds.add(d.getId());
			if (t.getDelivered() >= d.cargoAmount)
			{
				continue;
			}
			base.add(new RoutePlanner.TaskState(d.getId(), d.getCargoLocation(), d.getDeliveryLocation(), t.getCargoTaken() < d.cargoAmount));
			Integer x = xp.xp(d.getId());
			baseXp += x == null ? 0 : x;
		}
		RoutePlanner.Plan basePlan = base.isEmpty() ? null : planner.plan(start, base, end, stopCost);
		double baseCost = basePlan == null ? 0 : basePlan.cost;
		double baseTiles = basePlan == null ? 0 : basePlan.sailedTiles;
		int baseStops = basePlan == null ? 0 : basePlan.stops.size();

		List<Score> scores = new ArrayList<>();
		for (CourierTaskData d : offered)
		{
			if (heldIds.contains(d.getId()) || !passesBagFilter(d) || sailingLevel > 0 && d.getLevelRequired() > sailingLevel)
			{
				continue;
			}
			RoutePlanner.TaskState extra = new RoutePlanner.TaskState(d.getId(), d.getCargoLocation(), d.getDeliveryLocation(), true);
			List<RoutePlanner.TaskState> with = new ArrayList<>(base);
			with.add(extra);
			RoutePlanner.Plan plan = planner.plan(start, with, end, stopCost);
			RoutePlanner.Plan alone = planner.plan(start, Collections.singletonList(extra), end, stopCost);

			double added = plan.cost - baseCost;
			double denominator = Math.max(added, 1);
			Integer taskXp = xp.xp(d.getId());
			String destination = d.getDeliveryLocation().getName();
			BagSize bag = taskXp == null ? null : BagSize.forXp(taskXp);
			double value = taskXp == null ? 0 : valuer.expectedTaskValue(taskXp, destination);

			List<String> signature = new ArrayList<>();
			if (bag != null)
			{
				for (CourierWikiData.Drop drop : wiki.signatureDrops(bag, destination))
				{
					signature.add(drop.name + (drop.min == drop.max ? " x" + drop.min : " x" + drop.min + "-" + drop.max));
				}
			}
			List<String> wantedHere = bag == null ? Collections.emptyList() : wanted.matches(wiki, bag, destination);

			scores.add(new Score(d.getDbrow(), d.taskName, d.getDeliveryLocation(), taskXp, bag, value,
				added, plan.sailedTiles - baseTiles, plan.stops.size() - baseStops,
				alone.cost > 0 ? added / alone.cost : 0,
				taskXp == null ? 0 : taskXp / denominator,
				value / denominator,
				(baseXp + (taskXp == null ? 0 : taskXp)) / Math.max(plan.cost, 1),
				signature, wantedHere, graph.distance(d.getCargoLocation(), d.getDeliveryLocation())));
		}

		scores.sort(comparator(config.routingRankBy()));
		for (int i = 0; i < scores.size(); i++)
		{
			scores.get(i).rank = i + 1;
		}
		return scores;
	}

	/** Added tiles below this count as a free ride (plan lengths are sums of real-valued path lengths). */
	private static final double FREE_RIDE_TILES = 0.5;
	private static final Color FREE_RIDE = new Color(255, 105, 180);

	/**
	 * Colour for how much a task adds to the trip: pink if nothing, else hue from green (adds nothing) to red
	 * (adds twice the task's own pickup-to-delivery distance or more, i.e. an out-and-back trip just for it),
	 * passing through yellow halfway. Unknown distances count as red.
	 */
	static Color detourColor(double addedTiles, double ownTiles)
	{
		if (addedTiles < FREE_RIDE_TILES)
		{
			return FREE_RIDE;
		}
		double t = ownTiles > 0 && Double.isFinite(ownTiles) && Double.isFinite(addedTiles)
			? Math.min(1, addedTiles / (2 * ownTiles)) : 1;
		return Color.getHSBColor((float) ((1 - t) / 3), 0.9f, 1f);
	}

	/**
	 * False if this task's bag size is switched off in the bag filter. A task whose XP isn't known passes,
	 * since its bag size can't be told.
	 */
	public boolean passesBagFilter(CourierTaskData d)
	{
		Integer taskXp = xp.xp(d.getId());
		return taskXp == null || bagEnabled(config, BagSize.forXp(taskXp));
	}

	/** Whether the bag filter lets tasks with this bag size through. */
	public static boolean bagEnabled(PortTasksConfig config, BagSize size)
	{
		switch (size)
		{
			case TINY:
				return config.routingBagTiny();
			case SMALL:
				return config.routingBagSmall();
			case MEDIUM:
				return config.routingBagMedium();
			case LARGE:
				return config.routingBagLarge();
			case HUGE:
			default:
				return config.routingBagHuge();
		}
	}

	private static Comparator<Score> comparator(RankBy by)
	{
		switch (by)
		{
			case VALUE_PER_ADDED_TILE:
				return Comparator.comparingDouble((Score s) -> s.valuePerAddedTile).reversed();
			case ROUTE_FIT:
				return Comparator.comparingDouble((Score s) -> s.routeFit);
			case PLAN_RATE_AFTER:
				return Comparator.comparingDouble((Score s) -> s.planRateAfter).reversed();
			case XP_PER_ADDED_TILE:
			default:
				return Comparator.comparingDouble((Score s) -> s.xpPerAddedTile).reversed();
		}
	}
}
