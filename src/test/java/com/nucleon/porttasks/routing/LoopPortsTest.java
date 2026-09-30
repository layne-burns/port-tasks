package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.EnumSet;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LoopPortsTest
{
	@Test
	public void emptyIsNoLoop()
	{
		assertFalse(LoopPorts.parse("").active());
		assertFalse(LoopPorts.parse("  ,\n ").active());
		assertFalse(LoopPorts.parse(null).active());
	}

	@Test
	public void fullNamesPrefixesAndThe()
	{
		LoopPorts loop = LoopPorts.parse("Deepfin, lunar isle\nPort Roberts, Red Rock, Summer Shore");
		assertEquals(EnumSet.of(PortLocation.DEEPFIN_POINT, PortLocation.LUNAR_ISLE, PortLocation.PORT_ROBERTS,
			PortLocation.RED_ROCK, PortLocation.SUMMER_SHORE), loop.ports());
		assertTrue(loop.unknown().isEmpty());
	}

	@Test
	public void ambiguousAndUnknownNamesAreReported()
	{
		// "Port" starts several ports' names; "Atlantis" none.
		LoopPorts loop = LoopPorts.parse("Port, Atlantis, Lunar");
		assertEquals(EnumSet.of(PortLocation.LUNAR_ISLE), loop.ports());
		assertEquals(List.of("Port", "Atlantis"), loop.unknown());
		assertNull(LoopPorts.match("Port"));
	}

	@Test
	public void holdsNeedsBothEnds()
	{
		LoopPorts loop = LoopPorts.parse("Deepfin, Red Rock");
		assertTrue(loop.holds(PortLocation.DEEPFIN_POINT, PortLocation.RED_ROCK));
		assertFalse(loop.holds(PortLocation.DEEPFIN_POINT, PortLocation.LUNAR_ISLE));
		assertFalse(LoopPorts.NONE.holds(PortLocation.DEEPFIN_POINT, PortLocation.RED_ROCK));
	}
}
