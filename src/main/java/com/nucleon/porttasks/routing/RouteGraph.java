package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.enums.PortPaths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Travel cost between any two ports (SPEC-routing.md §3, v1). Port Tasks' hand-drawn PortPaths are the edges,
 * weighted by their length in tiles and usable in both directions; all-pairs shortest paths (Floyd–Warshall,
 * about 30 ports) give d(u, v) for every pair, including pairs with no drawn path of their own. Computed once;
 * read-only afterwards, so safe to share between threads.
 */
public final class RouteGraph
{
	private final PortLocation[] ports;
	/** PortLocation ordinal -> index into the tables (-1 for EMPTY). */
	private final int[] indexByOrdinal = new int[PortLocation.values().length];
	/** All-pairs distances over the drawn paths. */
	private final double[][] cost;

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
		Arrays.fill(indexByOrdinal, -1);
		for (int i = 0; i < ports.length; i++)
		{
			indexByOrdinal[ports[i].ordinal()] = i;
		}
		int n = ports.length;
		double[][] d = new double[n][n];
		for (int i = 0; i < n; i++)
		{
			for (int j = 0; j < n; j++)
			{
				d[i][j] = i == j ? 0 : Double.POSITIVE_INFINITY;
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
			if (a != b && p.getDistance() < d[a][b])
			{
				d[a][b] = d[b][a] = p.getDistance();
			}
		}
		// Floyd–Warshall: allow each port k in turn as a stop-over.
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
					d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
				}
			}
		}
		cost = d;
	}

	/** Sailing distance in tiles for planning, or +infinity if nothing connects them. */
	public double distance(PortLocation from, PortLocation to)
	{
		return cost[index(from)][index(to)];
	}

	private int index(PortLocation p)
	{
		int i = indexByOrdinal[p.ordinal()];
		if (i < 0)
		{
			throw new IllegalArgumentException("unknown port " + p);
		}
		return i;
	}
}
