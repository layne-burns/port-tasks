package com.nucleon.porttasks.routing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class BoatLocatorTest
{
	@Test
	public void boatPortVarbitsLocateTheBoat()
	{
		assertTrue(BoatLocator.locatesBoat(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED));
		assertTrue(BoatLocator.locatesBoat(VarbitID.SAILING_BOAT_1_PORT));
		assertTrue(BoatLocator.locatesBoat(VarbitID.SAILING_BOAT_5_PORT));
	}

	@Test
	public void otherSailingVarbitsDoNot()
	{
		// These change every tick at sea (move mode) or on each dock visit; neither moves the plan's start.
		assertFalse(BoatLocator.locatesBoat(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE));
		assertFalse(BoatLocator.locatesBoat(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT));
		assertFalse(BoatLocator.locatesBoat(VarbitID.SAILING_BOARDED_BOAT_LAST_DOCK));
		assertFalse(BoatLocator.locatesBoat(VarbitID.SAILING_BOARDED_BOAT_LAST_MOORING_POINT));
	}
}
