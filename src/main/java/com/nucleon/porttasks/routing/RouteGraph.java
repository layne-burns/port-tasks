package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.enums.PortPaths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Travel cost between any two ports (SPEC-routing.md §3, v1). Port Tasks' hand-drawn PortPaths are the edges,
 * weighted by their length in tiles and usable in both directions; all-pairs shortest paths (Floyd–Warshall,
 * about 30 ports) give d(u, v) for every pair, including pairs with no drawn path of their own, and remember
 * the chain of paths so a leg can be drawn.
 *
 * Legs learned from sailing (LegLearner) refine the planning distances: each learned pair becomes a direct
 * edge with its measured length (replacing a drawn path between the same two ports), and the all-pairs
 * distances are recomputed, so pairs that pass through it improve too. That happens only when a leg is
 * learned or a setting changes. Drawing still follows the drawn paths. Thread-safe: the distance table is
 * replaced whole.
 */
public final class RouteGraph
{
	/** One drawn path used by a leg, and whether it is travelled end to start. */
	public static final class Hop
	{
		public final PortPaths path;
		public final boolean reversed;

		Hop(PortPaths path, boolean reversed)
		{
			this.path = path;
			this.reversed = reversed;
		}
	}

	private final PortLocation[] ports;
	/** Shortest distances over the drawn paths alone, and their routes (next, edge) for drawing. */
	private final double[][] dist;
	/** next[i][j]: the port after i on the shortest route from i to j, or -1 if unreachable. */
	private final int[][] next;
	/** edge[i][j]: the drawn path used for the direct hop i -> j (shortest one if several). */
	private final Hop[][] edge;
	/** Planning distances: dist, refined by learned legs. */
	private volatile double[][] cost;

	public RouteGraph()
	{
		List<PortLocation> list = new ArrayList<>();
		for (PortLocation p : PortLocation.values())
		{
			if (p != PortLocation.EMPTY)
			{
				list.add(p);
			}
		}
		ports = list.toArray(new PortLocation[0]);
		int n = ports.length;
		dist = new double[n][n];
		next = new int[n][n];
		edge = new Hop[n][n];
		for (int i = 0; i < n; i++)
		{
			for (int j = 0; j < n; j++)
			{
				dist[i][j] = i == j ? 0 : Double.POSITIVE_INFINITY;
				next[i][j] = i == j ? i : -1;
			}
		}

		for (PortPaths p : PortPaths.values())
		{
			if (p.getStart() == PortLocation.EMPTY || p.getEnd() == PortLocation.EMPTY || p.getDistance() <= 0)
			{
				continue;
			}
			int a = index(p.getStart());
			int b = index(p.getEnd());
			if (a == b || p.getDistance() >= dist[a][b])
			{
				continue;
			}
			dist[a][b] = dist[b][a] = p.getDistance();
			next[a][b] = b;
			next[b][a] = a;
			edge[a][b] = new Hop(p, false);
			edge[b][a] = new Hop(p, true);
		}

		floydWarshall(dist, next);
		cost = dist;
	}

	/** All-pairs shortest paths in place; {@code next} (may be null) follows the first hop of each. */
	private static void floydWarshall(double[][] d, int[][] next)
	{
		int n = d.length;
		for (int k = 0; k < n; k++)
		{
			for (int i = 0; i < n; i++)
			{
				if (d[i][k] == Double.POSITIVE_INFINITY)
				{
					continue;
				}
				for (int j = 0; j < n; j++)
				{
					double viaK = d[i][k] + d[k][j];
					if (viaK < d[i][j])
					{
						d[i][j] = viaK;
						if (next != null)
						{
							next[i][j] = next[i][k];
						}
					}
				}
			}
		}
	}

	/**
	 * Plans with these learned legs from now on (an empty list goes back to the drawn paths alone): each is a
	 * direct edge of its measured length, replacing a drawn path between the same ports.
	 */
	public void setLearned(List<LegLearner.Learned> learned)
	{
		int n = ports.length;
		double[][] d = new double[n][n];
		for (int i = 0; i < n; i++)
		{
			for (int j = 0; j < n; j++)
			{
				d[i][j] = i == j ? 0 : edge[i][j] != null ? edge[i][j].path.getDistance() : Double.POSITIVE_INFINITY;
			}
		}
		for (LegLearner.Learned l : learned)
		{
			int a = index(l.a);
			int b = index(l.b);
			d[a][b] = d[b][a] = l.tiles;
		}
		floydWarshall(d, null);
		cost = d;
	}

	/** Sailing distance in tiles for planning, or +infinity if nothing connects them. */
	public double distance(PortLocation from, PortLocation to)
	{
		return cost[index(from)][index(to)];
	}

	/** The drawn paths that make up the shortest route, in travel order; empty if same port or unreachable. */
	public List<Hop> hops(PortLocation from, PortLocation to)
	{
		int i = index(from);
		int j = index(to);
		if (i == j || next[i][j] < 0)
		{
			return Collections.emptyList();
		}
		List<Hop> hops = new ArrayList<>();
		while (i != j)
		{
			int k = next[i][j];
			hops.add(edge[i][k]);
			i = k;
		}
		return hops;
	}

	/** The shortest route as world points, for drawing the next leg. */
	public List<WorldPoint> points(PortLocation from, PortLocation to)
	{
		List<WorldPoint> points = new ArrayList<>();
		for (Hop h : hops(from, to))
		{
			List<WorldPoint> leg = new ArrayList<>(h.path.getFullPath());
			if (h.reversed)
			{
				Collections.reverse(leg);
			}
			if (!points.isEmpty() && !leg.isEmpty() && points.get(points.size() - 1).equals(leg.get(0)))
			{
				leg.remove(0);
			}
			points.addAll(leg);
		}
		return points;
	}

	private int index(PortLocation p)
	{
		for (int i = 0; i < ports.length; i++)
		{
			if (ports[i] == p)
			{
				return i;
			}
		}
		throw new IllegalArgumentException("unknown port " + p);
	}
}
