package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class NoticeBoardTilesTest
{
	/** Every port with a board has a land tile for it, close to the port's dock. */
	@Test
	public void everyBoardHasATileNearItsDock()
	{
		for (PortLocation p : PortLocation.values())
		{
			if (p == PortLocation.EMPTY || p.getNoticeboardObject() == -1)
			{
				continue;
			}
			WorldPoint tile = NoticeBoardTiles.of(p);
			assertNotNull(p + " has a board but no tile", tile);
			assertTrue(p + " board is far from its dock", tile.distanceTo2D(p.getNavigationLocation()) <= 30);
		}
	}
}
