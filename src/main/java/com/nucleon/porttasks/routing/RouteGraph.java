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
 * the chain of paths so a leg can be drawn. Built once; immutable and thread-safe after construction.
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
	private final double[][] dist;
	/** next[i][j]: the port after i on the shortest route from i to j, or -1 if unreachable. */
	private final int[][] next;
	/** edge[i][j]: the drawn path used for the direct hop i -> j (shortest one if several). */
	private final Hop[][] edge;

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

		for (int k = 0; k < n; k++)
		{
			for (int i = 0; i < n; i++)
			{
				if (dist[i][k] == Double.POSITIVE_INFINITY)
				{
					continue;
				}
				for (int j = 0; j < n; j++)
				{
					double viaK = dist[i][k] + dist[k][j];
					if (viaK < dist[i][j])
					{
						dist[i][j] = viaK;
						next[i][j] = next[i][k];
					}
				}
			}
		}
	}

	/** Sailing distance in tiles, or +infinity if no chain of drawn paths connects them. */
	public double distance(PortLocation from, PortLocation to)
	{
		return dist[index(from)][index(to)];
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
