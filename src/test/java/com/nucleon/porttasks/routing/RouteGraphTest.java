package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.enums.PortPaths;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RouteGraphTest
{
	private static final double EPS = 1e-6;
	private static final Set<PortLocation> WEST = EnumSet.of(
		PortLocation.VOID_KNIGHTS_OUTPOST, PortLocation.DEEPFIN_POINT, PortLocation.ALDARIN, PortLocation.SUNSET_COAST,
		PortLocation.CIVITAS_ILLA_FORTIS, PortLocation.PORT_ROBERTS, PortLocation.PORT_TYRAS, PortLocation.PRIFDDINAS,
		PortLocation.LANDS_END, PortLocation.HOSIDIUS, PortLocation.PORT_PISCARILIUS, PortLocation.PISCATORIS,
		PortLocation.RELLEKKA, PortLocation.LUNAR_ISLE, PortLocation.JATIZSO, PortLocation.NEITIZNOT, PortLocation.ETCETERIA,
		PortLocation.RED_ROCK);

	private final RouteGraph graph = new RouteGraph();

	private static Iterable<PortLocation> ports()
	{
		EnumSet<PortLocation> all = EnumSet.allOf(PortLocation.class);
		all.remove(PortLocation.EMPTY);
		return all;
	}

	@Test
	public void symmetricWithZeroDiagonal()
	{
		for (PortLocation a : ports())
		{
			assertEquals(0, graph.distance(a, a), EPS);
			for (PortLocation b : ports())
			{
				assertEquals(a + "/" + b, graph.distance(a, b), graph.distance(b, a), EPS);
			}
		}
	}

	@Test
	public void neverLongerThanADrawnPath()
	{
		for (PortPaths p : PortPaths.values())
		{
			if (p.getStart() == PortLocation.EMPTY || p.getDistance() <= 0)
			{
				continue;
			}
			assertTrue(p.name(), graph.distance(p.getStart(), p.getEnd()) <= p.getDistance() + EPS);
		}
	}

	@Test
	public void triangleInequality()
	{
		for (PortLocation a : ports())
		{
			for (PortLocation b : ports())
			{
				for (PortLocation c : ports())
				{
					double ab = graph.distance(a, b);
					double bc = graph.distance(b, c);
					if (ab < Double.POSITIVE_INFINITY && bc < Double.POSITIVE_INFINITY)
					{
						assertTrue(a + "-" + b + "-" + c, graph.distance(a, c) <= ab + bc + EPS);
					}
				}
			}
		}
	}

	@Test
	public void westernPortsAllConnected()
	{
		for (PortLocation a : WEST)
		{
			for (PortLocation b : WEST)
			{
				assertTrue(a + " -> " + b, graph.distance(a, b) < Double.POSITIVE_INFINITY);
			}
		}
	}

	@Test
	public void everyPortConnected()
	{
		StringBuilder unreachable = new StringBuilder();
		for (PortLocation a : ports())
		{
			for (PortLocation b : ports())
			{
				if (graph.distance(a, b) == Double.POSITIVE_INFINITY)
				{
					unreachable.append(a).append(" -> ").append(b).append("; ");
				}
			}
		}
		assertEquals("", unreachable.toString());
	}

	@Test
	public void hopsAddUpToTheDistanceAndChainEndToEnd()
	{
		for (PortLocation a : WEST)
		{
			for (PortLocation b : WEST)
			{
				List<RouteGraph.Hop> hops = graph.hops(a, b);
				assertEquals(a == b, hops.isEmpty());
				double sum = 0;
				PortLocation at = a;
				for (RouteGraph.Hop h : hops)
				{
					PortLocation from = h.reversed ? h.path.getEnd() : h.path.getStart();
					assertEquals(a + " -> " + b, at, from);
					at = h.reversed ? h.path.getStart() : h.path.getEnd();
					sum += h.path.getDistance();
				}
				assertEquals(b, at);
				assertEquals(a + " -> " + b, graph.distance(a, b), sum, EPS);
			}
		}
	}

	@Test
	public void pointsRunFromDockToDock()
	{
		List<WorldPoint> pts = graph.points(PortLocation.LUNAR_ISLE, PortLocation.DEEPFIN_POINT);
		assertFalse(pts.isEmpty());
		assertEquals(PortLocation.LUNAR_ISLE.getNavigationLocation(), pts.get(0));
		assertEquals(PortLocation.DEEPFIN_POINT.getNavigationLocation(), pts.get(pts.size() - 1));
	}

	/** Not an assertion: prints the western distance table for eyeballing. */
	@Test
	public void printWesternTable()
	{
		StringBuilder sb = new StringBuilder("\nWestern distances (tiles):\n");
		for (PortLocation a : WEST)
		{
			sb.append(String.format("%-22s", a.getName()));
			for (PortLocation b : WEST)
			{
				sb.append(String.format("%6.0f", graph.distance(a, b)));
			}
			sb.append('\n');
		}
		System.out.println(sb);
	}
}
