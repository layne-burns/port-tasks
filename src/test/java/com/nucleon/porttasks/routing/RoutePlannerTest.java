package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import static com.nucleon.porttasks.enums.PortLocation.ALDARIN;
import static com.nucleon.porttasks.enums.PortLocation.DEEPFIN_POINT;
import static com.nucleon.porttasks.enums.PortLocation.LUNAR_ISLE;
import static com.nucleon.porttasks.enums.PortLocation.PORT_ROBERTS;
import static com.nucleon.porttasks.enums.PortLocation.PORT_TYRAS;
import static com.nucleon.porttasks.enums.PortLocation.RELLEKKA;
import static com.nucleon.porttasks.enums.PortLocation.SUNSET_COAST;
import static com.nucleon.porttasks.enums.PortLocation.VOID_KNIGHTS_OUTPOST;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RoutePlannerTest
{
	private static final double EPS = 1e-6;

	/** Ports on a line: distance is |x_a - x_b|, easy to check by hand. */
	private static RoutePlanner line(Map<PortLocation, Integer> x)
	{
		return new RoutePlanner((a, b) -> Math.abs(x.get(a) - x.get(b)));
	}

	private static Map<PortLocation, Integer> positions(Object... portsAndX)
	{
		Map<PortLocation, Integer> m = new EnumMap<>(PortLocation.class);
		for (int i = 0; i < portsAndX.length; i += 2)
		{
			m.put((PortLocation) portsAndX[i], (Integer) portsAndX[i + 1]);
		}
		return m;
	}

	private static RoutePlanner.TaskState task(int id, PortLocation from, PortLocation to)
	{
		return new RoutePlanner.TaskState(id, from, to, true);
	}

	@Test
	public void singleTaskFromItsCargoPort()
	{
		RoutePlanner p = line(positions(ALDARIN, 0, DEEPFIN_POINT, 50));
		RoutePlanner.Plan plan = p.plan(ALDARIN, List.of(task(1, ALDARIN, DEEPFIN_POINT)), null, 30);
		assertEquals(50 + 30, plan.cost, EPS);
		assertEquals(2, plan.stops.size());
		assertEquals(List.of(1), plan.stops.get(0).pickups);
		assertEquals(List.of(1), plan.stops.get(1).deliveries);
	}

	@Test
	public void taskAlongTheWayIsInterleaved()
	{
		// Start 0; task 1: 10 -> 20; task 2: 5 -> 15. Best: 5, 10, 15, 20 = 20 tiles, 4 stops.
		RoutePlanner p = line(positions(ALDARIN, 0, SUNSET_COAST, 5, DEEPFIN_POINT, 10, PORT_TYRAS, 15, PORT_ROBERTS, 20));
		RoutePlanner.Plan plan = p.plan(ALDARIN, List.of(task(1, DEEPFIN_POINT, PORT_ROBERTS), task(2, SUNSET_COAST, PORT_TYRAS)), null, 30);
		assertEquals(20 + 4 * 30, plan.cost, EPS);
		assertEquals(List.of(SUNSET_COAST, DEEPFIN_POINT, PORT_TYRAS, PORT_ROBERTS),
			plan.stops.stream().map(s -> s.port).collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void deliveryBehindThePickupForcesABacktrack()
	{
		// Start 0; pick up at 20, deliver at 10: 20 + 10 tiles.
		RoutePlanner p = line(positions(ALDARIN, 0, DEEPFIN_POINT, 10, PORT_ROBERTS, 20));
		RoutePlanner.Plan plan = p.plan(ALDARIN, List.of(task(1, PORT_ROBERTS, DEEPFIN_POINT)), null, 0);
		assertEquals(30, plan.cost, EPS);
	}

	@Test
	public void cargoAlreadyAboardOnlyNeedsDelivery()
	{
		RoutePlanner p = line(positions(ALDARIN, 0, DEEPFIN_POINT, 10, PORT_ROBERTS, 20));
		RoutePlanner.TaskState carried = new RoutePlanner.TaskState(1, PORT_ROBERTS, DEEPFIN_POINT, false);
		RoutePlanner.Plan plan = p.plan(ALDARIN, List.of(carried), null, 0);
		assertEquals(10, plan.cost, EPS);
	}

	@Test
	public void sharedPortIsOneStop()
	{
		// Both tasks go 0 -> 40: one pickup stop at the start (free, no move) and one delivery stop.
		RoutePlanner p = line(positions(ALDARIN, 0, RELLEKKA, 40));
		RoutePlanner.Plan plan = p.plan(ALDARIN, List.of(task(1, ALDARIN, RELLEKKA), task(2, ALDARIN, RELLEKKA)), null, 30);
		assertEquals(40 + 30, plan.cost, EPS);
		assertEquals(2, plan.stops.size());
	}

	@Test
	public void fixedEndAddsTheFinalLeg()
	{
		RoutePlanner p = line(positions(ALDARIN, 0, DEEPFIN_POINT, 10, LUNAR_ISLE, 100));
		RoutePlanner.Plan plan = p.plan(ALDARIN, List.of(task(1, ALDARIN, DEEPFIN_POINT)), LUNAR_ISLE, 30);
		assertEquals(10 + 30 + 90, plan.cost, EPS);
		assertEquals(100, plan.sailedTiles, EPS);
	}

	@Test
	public void matchesBruteForceOnRandomInstances()
	{
		PortLocation[] pool = {ALDARIN, SUNSET_COAST, DEEPFIN_POINT, PORT_TYRAS, PORT_ROBERTS, RELLEKKA, LUNAR_ISLE, VOID_KNIGHTS_OUTPOST};
		Random rnd = new Random(42);
		for (int trial = 0; trial < 300; trial++)
		{
			Map<PortLocation, double[]> xy = new EnumMap<>(PortLocation.class);
			for (PortLocation p : pool)
			{
				xy.put(p, new double[]{rnd.nextInt(1000), rnd.nextInt(1000)});
			}
			RoutePlanner.ToDistance d = (a, b) -> Math.hypot(xy.get(a)[0] - xy.get(b)[0], xy.get(a)[1] - xy.get(b)[1]);
			int n = 1 + rnd.nextInt(5);
			List<RoutePlanner.TaskState> tasks = new ArrayList<>();
			for (int i = 0; i < n; i++)
			{
				PortLocation a = pool[rnd.nextInt(pool.length)];
				PortLocation b;
				do
				{
					b = pool[rnd.nextInt(pool.length)];
				}
				while (b == a);
				tasks.add(new RoutePlanner.TaskState(i, a, b, rnd.nextInt(4) != 0));
			}
			PortLocation start = pool[rnd.nextInt(pool.length)];
			PortLocation end = rnd.nextBoolean() ? null : pool[rnd.nextInt(pool.length)];
			double stop = rnd.nextInt(60);

			double expected = bruteForce(d, start, tasks, end, stop);
			double actual = new RoutePlanner(d::apply).plan(start, tasks, end, stop).cost;
			assertEquals("trial " + trial, expected, actual, 1e-6);
		}
	}

	/** Minimum over every precedence-respecting order of events, costing moves exactly as the planner does. */
	private static double bruteForce(RoutePlanner.ToDistance d, PortLocation start, List<RoutePlanner.TaskState> tasks,
		PortLocation end, double stop)
	{
		List<int[]> events = new ArrayList<>(); // {task, 0=pickup/1=delivery}
		boolean[] picked = new boolean[tasks.size()];
		for (int i = 0; i < tasks.size(); i++)
		{
			if (tasks.get(i).needsPickup)
			{
				events.add(new int[]{i, 0});
			}
			else
			{
				picked[i] = true;
			}
			events.add(new int[]{i, 1});
		}
		return permute(d, tasks, events, new boolean[events.size()], picked, start, 0, 0, end, stop);
	}

	private static double permute(RoutePlanner.ToDistance d, List<RoutePlanner.TaskState> tasks, List<int[]> events,
		boolean[] used, boolean[] picked, PortLocation at, double cost, int count, PortLocation end, double stop)
	{
		if (count == events.size())
		{
			return cost + (end == null ? 0 : d.apply(at, end));
		}
		double best = Double.POSITIVE_INFINITY;
		for (int e = 0; e < events.size(); e++)
		{
			int[] ev = events.get(e);
			if (used[e] || (ev[1] == 1 && !picked[ev[0]]))
			{
				continue;
			}
			RoutePlanner.TaskState t = tasks.get(ev[0]);
			PortLocation port = ev[1] == 0 ? t.cargoPort : t.destination;
			double move = port == at ? 0 : d.apply(at, port) + stop;
			used[e] = true;
			boolean wasPicked = picked[ev[0]];
			if (ev[1] == 0)
			{
				picked[ev[0]] = true;
			}
			best = Math.min(best, permute(d, tasks, events, used, picked, port, cost + move, count + 1, end, stop));
			picked[ev[0]] = wasPicked;
			used[e] = false;
		}
		return best;
	}
}
