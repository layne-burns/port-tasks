package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import static com.nucleon.porttasks.enums.PortLocation.DEEPFIN_POINT;
import static com.nucleon.porttasks.enums.PortLocation.LUNAR_ISLE;
import static com.nucleon.porttasks.enums.PortLocation.PORT_ROBERTS;
import static com.nucleon.porttasks.enums.PortLocation.RED_ROCK;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LoopSuggesterTest
{
	// Four ports on a line at 0, 100, 200 and 1000 tiles.
	private static double position(PortLocation p)
	{
		switch (p)
		{
			case DEEPFIN_POINT:
				return 0;
			case PORT_ROBERTS:
				return 100;
			case RED_ROCK:
				return 200;
			default:
				return 1000;
		}
	}

	private final LoopSuggester suggester = new LoopSuggester((a, b) -> Math.abs(position(a) - position(b)));
	private final Set<PortLocation> usable = EnumSet.of(DEEPFIN_POINT, PORT_ROBERTS, RED_ROCK, LUNAR_ISLE);

	private static void add(List<LoopSuggester.Candidate> pool, int n, PortLocation a, PortLocation b, int xp)
	{
		for (int i = 0; i < n; i++)
		{
			pool.add(new LoopSuggester.Candidate(a, a, b, xp));
		}
	}

	@Test
	public void needsThreeTasksPerPort()
	{
		List<LoopSuggester.Candidate> pool = new ArrayList<>();
		add(pool, 5, DEEPFIN_POINT, PORT_ROBERTS, 3000);
		// 5 tasks for 2 ports: below the floor of 6.
		assertTrue(suggester.suggest(pool, usable, 5).isEmpty());
		add(pool, 1, PORT_ROBERTS, DEEPFIN_POINT, 3000);
		List<LoopSuggester.Suggestion> s = suggester.suggest(pool, usable, 5);
		assertEquals(1, s.size());
		assertEquals(6, s.get(0).tasks);
		assertEquals(200, s.get(0).loopTiles, 1e-9);
		assertEquals(1000 * 18000 / 200.0, s.get(0).density(), 1e-9);
	}

	@Test
	public void tasksTouchingAnotherPortDontCount()
	{
		List<LoopSuggester.Candidate> pool = new ArrayList<>();
		add(pool, 6, DEEPFIN_POINT, LUNAR_ISLE, 3000);
		add(pool, 6, DEEPFIN_POINT, PORT_ROBERTS, 1000);
		List<LoopSuggester.Suggestion> s = suggester.suggest(pool, usable, 5);
		// Deepfin + Roberts: 6 x 1000 over 200 tiles = 30k/kt; Deepfin + Lunar: 6 x 3000 over 2000 = 9k/kt.
		assertEquals(EnumSet.of(DEEPFIN_POINT, PORT_ROBERTS), EnumSet.copyOf(s.get(0).ports));
		assertEquals(6, s.get(0).tasks);
		// Leaving Lunar Isle out of the usable ports drops its loops.
		Set<PortLocation> noLunar = EnumSet.of(DEEPFIN_POINT, PORT_ROBERTS, RED_ROCK);
		for (LoopSuggester.Suggestion x : suggester.suggest(pool, noLunar, 5))
		{
			assertTrue(!x.ports.contains(LUNAR_ISLE));
		}
	}

	@Test
	public void fourPortsTakeTheShortestCycle()
	{
		// Around a square with corners 0,1,2,3 in that order: the tour 0-1-2-3 is 4, the crossing ones longer.
		double[][] xy = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
		PortLocation[] ports = {DEEPFIN_POINT, RED_ROCK, PORT_ROBERTS, LUNAR_ISLE};
		LoopSuggester square = new LoopSuggester((a, b) ->
		{
			double[] p = xy[List.of(ports).indexOf(a)];
			double[] q = xy[List.of(ports).indexOf(b)];
			return Math.hypot(p[0] - q[0], p[1] - q[1]);
		});
		List<PortLocation> cycle = square.shortestCycle(List.of(DEEPFIN_POINT, PORT_ROBERTS, RED_ROCK, LUNAR_ISLE));
		assertEquals(4, square.cycleLength(cycle), 1e-9);
		assertEquals(DEEPFIN_POINT, cycle.get(0));
	}
}
