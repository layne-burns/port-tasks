package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.enums.PortPaths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Travel cost between any two ports (SPEC-routing.md §3, v1). Port Tasks' hand-drawn PortPaths are the edges,
 * weighted by their length in tiles and usable in both directions; all-pairs shortest paths (Floyd–Warshall,
 * about 30 ports) give d(u, v) for every pair, including pairs with no drawn path of their own.
 *
 * Legs learned from sailing (LegLearner) refine the distances: each learned pair becomes a direct edge with
 * its measured length (replacing a drawn path between the same two ports), and the all-pairs distances are
 * recomputed, so pairs that pass through it improve too. That happens only when a leg is learned or a
 * setting changes. Thread-safe: the distance table is replaced whole.
 */
public final class RouteGraph
{
	private final PortLocation[] ports;
	/** drawn[i][j]: length of the shortest drawn path directly between i and j, or +infinity if none. */
	private final double[][] drawn;
	/** All-pairs distances over the drawn paths, refined by learned legs. */
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
		drawn = new double[n][n];
		for (int i = 0; i < n; i++)
		{
			for (int j = 0; j < n; j++)
			{
				drawn[i][j] = i == j ? 0 : Double.POSITIVE_INFINITY;
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
			if (a != b && p.getDistance() < drawn[a][b])
			{
				drawn[a][b] = drawn[b][a] = p.getDistance();
			}
		}
		setLearned(Collections.emptyList());
	}

	/**
	 * Plans with these learned legs from now on (an empty list goes back to the drawn paths alone): each is a
	 * direct edge of its measured length, replacing a drawn path between the same ports.
	 */
	public void setLearned(List<LegLearner.Learned> learned)
	{
		int n = ports.length;
		double[][] d = new double[n][];
		for (int i = 0; i < n; i++)
		{
			d[i] = drawn[i].clone();
		}
		for (LegLearner.Learned l : learned)
		{
			int a = index(l.a);
			int b = index(l.b);
			d[a][b] = d[b][a] = l.tiles;
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
