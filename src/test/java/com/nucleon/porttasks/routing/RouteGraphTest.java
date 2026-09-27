package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.enums.PortPaths;
import java.util.EnumSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RouteGraphTest
{
	private static final double EPS = 1e-6;
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

}
