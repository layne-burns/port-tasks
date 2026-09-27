package com.nucleon.porttasks.routing;

import java.awt.Color;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DetourColorTest
{
	private static float hue(Color c)
	{
		return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[0];
	}

	@Test
	public void freeRideIsPink()
	{
		assertEquals(new Color(255, 105, 180), BoardScorer.detourColor(0, 200));
		assertEquals(new Color(255, 105, 180), BoardScorer.detourColor(0.2, 200));
	}

	@Test
	public void outAndBackIsRed()
	{
		// The task alone is 200 tiles; adding 400 means sailing there and back just for it.
		assertEquals(0, hue(BoardScorer.detourColor(400, 200)), 0.01);
		assertEquals(0, hue(BoardScorer.detourColor(900, 200)), 0.01);
	}

	@Test
	public void halfwayIsYellowAndSmallDetoursAreGreen()
	{
		assertEquals(1 / 6f, hue(BoardScorer.detourColor(200, 200)), 0.01);
		assertTrue(hue(BoardScorer.detourColor(10, 200)) > 0.3f);
	}

	@Test
	public void unknownDistanceIsRed()
	{
		assertEquals(0, hue(BoardScorer.detourColor(50, Double.POSITIVE_INFINITY)), 0.01);
		assertEquals(0, hue(BoardScorer.detourColor(Double.POSITIVE_INFINITY, 200)), 0.01);
	}
}
