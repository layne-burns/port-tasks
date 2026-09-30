package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BountySpawnsTest
{
	private static BountySpawns.Area area(String monster, String name, int x, int y)
	{
		return new BountySpawns.Area(monster, name, List.of(new WorldPoint(x, y, 0), new WorldPoint(x + 4, y, 0)));
	}

	@Test
	public void theGraphBeatsAStraightLineAcrossLand()
	{
		// Two ports: the boat is by Deepfin Point. Area A is straight-line close to the boat but only reached by
		// sailing round via Red Rock (the graph says so); area B is further in a straight line but open sea.
		PortLocation near = PortLocation.DEEPFIN_POINT;
		PortLocation far = PortLocation.RED_ROCK;
		WorldPoint boat = near.getNavigationLocation();
		BountySpawns.Area a = area("Shark", "behind land", far.getNavigationLocation().getX() + 5, far.getNavigationLocation().getY());
		BountySpawns.Area b = area("Ray", "open sea", boat.getX() + 300, boat.getY());
		BountySpawns spawns = new BountySpawns(Map.of("Shark", List.of(a), "Ray", List.of(b)));
		// The graph makes Red Rock 5000 tiles away.
		BountySpawns.Area best = spawns.nearest(List.of("Shark", "Ray"), Collections.emptyMap(), boat, Set.of(near, far),
			(p, q) -> p == q ? 0 : 5000);
		assertEquals("open sea", best.location);
	}

	@Test
	public void aSafespotWinsOverSpawnAreas()
	{
		PortLocation port = PortLocation.DEEPFIN_POINT;
		WorldPoint boat = port.getNavigationLocation();
		BountySpawns.Area close = area("Shark", "close", boat.getX() + 10, boat.getY());
		BountySpawns spawns = new BountySpawns(Map.of("Shark", List.of(close)));
		BountySpawns.Area spot = BountySpawns.safespot("Shark", "saved", boat.getX() + 900, boat.getY(), 1, 1);
		BountySpawns.Area best = spawns.nearest(List.of("Shark"), Map.of("Shark", List.of(spot)), boat, Set.of(port), (p, q) -> 0);
		assertNotNull(best.safespot);
		assertTrue(best.location.startsWith("Safespot"));
	}

	@Test
	public void parkLineRunsAlongTheBoatsHeading()
	{
		// Heading north (1024): five tiles in a column centred on the boat.
		BountySpawns.Area north = BountySpawns.parkLine("Shark", 100, 200, 1024);
		assertEquals(List.of(new WorldPoint(100, 198, 0), new WorldPoint(100, 199, 0), new WorldPoint(100, 200, 0),
			new WorldPoint(100, 201, 0), new WorldPoint(100, 202, 0)), north.line);
		// Heading east (1536): a row.
		BountySpawns.Area east = BountySpawns.parkLine("Shark", 100, 200, 1536);
		assertEquals(new WorldPoint(98, 200, 0), east.line.get(0));
		assertEquals(new WorldPoint(102, 200, 0), east.line.get(4));
		assertEquals(new WorldPoint(100, 200, 0), east.target);
	}

	@Test
	public void safespotRectangleIsCentredOnTheMapPin()
	{
		BountySpawns.Area s = BountySpawns.safespot("Orca", "x", 2268, 3747, 3, 9);
		assertEquals(new WorldPoint(2268, 3747, 0), s.target);
		assertEquals(2267, s.safespot[0]);
		assertEquals(3743, s.safespot[1]);
	}
}
