package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import com.nucleon.porttasks.enums.PortLocation;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LegLearningTest
{
	private static final double EPS = 1e-9;
	private static final PortLocation SARIM = PortLocation.PORT_SARIM;
	private static final PortLocation PANDEMONIUM = PortLocation.PANDEMONIUM;

	/** The learner's "save" for tests that don't check saving. */
	private static void ignore(String json)
	{
	}

	private static LegTracker.Leg leg(PortLocation from, PortLocation to, double tiles, int ticks)
	{
		return new LegTracker.Leg(from, to, tiles, ticks);
	}

	// --- LegTracker

	@Test
	public void trackerSumsStraightLineMoves()
	{
		LegTracker t = new LegTracker();
		t.start(SARIM, 100);
		t.sample(new WorldPoint(0, 0, 0));
		t.sample(new WorldPoint(3, 4, 0));
		t.sample(new WorldPoint(3, 10, 0));
		assertEquals(11, t.tiles(), EPS);
		LegTracker.Leg l = t.finish(PANDEMONIUM, 130);
		assertEquals(11, l.tiles, EPS);
		assertEquals(30, l.ticks);
		assertFalse(t.active());
	}

	@Test
	public void trackerDropsTeleportsLeavingTheBoatAndRoundTrips()
	{
		LegTracker t = new LegTracker();
		t.start(SARIM, 0);
		t.sample(new WorldPoint(0, 0, 0));
		t.sample(new WorldPoint(0, (int) LegTracker.MAX_TILES_PER_TICK + 1, 0));
		assertFalse("a jump ends the leg", t.active());

		t.start(SARIM, 0);
		t.sample(null);
		assertFalse("leaving the boat ends the leg", t.active());

		t.start(SARIM, 0);
		assertNull("back at the same port", t.finish(SARIM, 10));
		t.start(SARIM, 0);
		assertNull("a mooring point", t.finish(null, 10));
	}

	// --- LegLearner

	@Test
	public void minimumAndMedianEitherDirection()
	{
		LegLearner l = new LegLearner(null, new Gson(), LegLearningTest::ignore);
		l.record(leg(SARIM, PANDEMONIUM, 150, 250), 160);
		l.record(leg(PANDEMONIUM, SARIM, 140, 230), 160);
		l.record(leg(SARIM, PANDEMONIUM, 170, 280), 160);
		assertEquals(3, l.samples(PANDEMONIUM, SARIM));
		assertEquals(140, only(l.estimates(LegLearner.Estimate.MINIMUM)).tiles, EPS);
		assertEquals(150, only(l.estimates(LegLearner.Estimate.MEDIAN)).tiles, EPS);
		assertEquals(250, only(l.estimates(LegLearner.Estimate.MEDIAN)).ticks, EPS);
	}

	@Test
	public void medianOfAnEvenCountIsTheMiddlePair()
	{
		assertEquals(15, LegLearner.estimate(new double[]{20, 10, 30, 12, 18, 5}, LegLearner.Estimate.MEDIAN), EPS);
	}

	@Test
	public void detoursAreIgnoredAndOnlyTheLastFewKept()
	{
		LegLearner l = new LegLearner(null, new Gson(), LegLearningTest::ignore);
		assertFalse(l.record(leg(SARIM, PANDEMONIUM, 321, 500), 160));
		assertTrue(l.record(leg(SARIM, PANDEMONIUM, 320, 500), 160));
		assertTrue("no estimate yet: anything goes", l.record(leg(SARIM, PortLocation.ENTRANA, 5000, 900), Double.POSITIVE_INFINITY));
		for (int i = 0; i < LegLearner.KEEP + 2; i++)
		{
			l.record(leg(SARIM, PANDEMONIUM, 100 + i, 200), 160);
		}
		assertEquals(LegLearner.KEEP, l.samples(SARIM, PANDEMONIUM));
		// The 320 and the first two of the loop have been pushed out.
		for (LegLearner.Learned e : l.estimates(LegLearner.Estimate.MINIMUM))
		{
			if (e.b == PANDEMONIUM || e.a == PANDEMONIUM)
			{
				assertEquals(102, e.tiles, EPS);
			}
		}
	}

	@Test
	public void survivesARoundTripThroughTheProfile()
	{
		String[] saved = new String[1];
		LegLearner l = new LegLearner(null, new Gson(), json -> saved[0] = json);
		l.record(leg(SARIM, PANDEMONIUM, 150, 250), 160);
		LegLearner back = new LegLearner(saved[0], new Gson(), LegLearningTest::ignore);
		assertEquals(150, only(back.estimates(LegLearner.Estimate.MINIMUM)).tiles, EPS);
		back.clear();
		assertTrue(back.estimates(LegLearner.Estimate.MINIMUM).isEmpty());
	}

	private static LegLearner.Learned only(List<LegLearner.Learned> list)
	{
		assertEquals(1, list.size());
		return list.get(0);
	}

	// --- RouteGraph with learned legs

	@Test
	public void aLearnedLegReplacesTheDrawnDistance()
	{
		RouteGraph g = new RouteGraph();
		double drawn = g.distance(SARIM, PANDEMONIUM);
		assertTrue(Double.isFinite(drawn));

		LegLearner l = new LegLearner(null, new Gson(), LegLearningTest::ignore);
		l.record(leg(SARIM, PANDEMONIUM, drawn + 37, 300), drawn);
		g.setLearned(l.estimates(LegLearner.Estimate.MINIMUM));
		assertEquals(drawn + 37, g.distance(SARIM, PANDEMONIUM), EPS);
		assertEquals(drawn + 37, g.distance(PANDEMONIUM, SARIM), EPS);

		g.setLearned(Collections.emptyList());
		assertEquals(drawn, g.distance(SARIM, PANDEMONIUM), EPS);
	}

	@Test
	public void aShorterLearnedLegShortensRoutesThroughIt()
	{
		RouteGraph g = new RouteGraph();
		PortLocation far = PortLocation.ENTRANA;
		double viaBefore = g.distance(SARIM, PANDEMONIUM) + g.distance(PANDEMONIUM, far);
		LegLearner l = new LegLearner(null, new Gson(), LegLearningTest::ignore);
		l.record(leg(SARIM, PANDEMONIUM, 1, 10), Double.POSITIVE_INFINITY);
		g.setLearned(l.estimates(LegLearner.Estimate.MINIMUM));
		assertTrue(g.distance(SARIM, far) <= 1 + g.distance(PANDEMONIUM, far) + EPS);
		assertTrue(g.distance(SARIM, far) <= viaBefore + EPS);
	}
}
