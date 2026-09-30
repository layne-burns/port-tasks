package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Where each port's notice board stands, on land: loop mode's gather target for a board the player hasn't
 * opened yet (after that, the tile they stood on is used). From the OSRS Wiki's *Notice board* map pins
 * (29 September 2026); each pin was matched to the nearest port dock, all within 26 tiles and at least 68
 * tiles from the next nearest. The port's own navigation tile can't stand in: it is at sea, and a land path
 * to it fails ("path can't be found").
 */
public final class NoticeBoardTiles
{
	private static final Map<PortLocation, WorldPoint> TILES;

	static
	{
		Map<PortLocation, WorldPoint> m = new EnumMap<>(PortLocation.class);
		m.put(PortLocation.ALDARIN, new WorldPoint(1438, 2969, 0));
		m.put(PortLocation.LANDS_END, new WorldPoint(1502, 3407, 0));
		m.put(PortLocation.CIVITAS_ILLA_FORTIS, new WorldPoint(1782, 3142, 0));
		m.put(PortLocation.PORT_PISCARILIUS, new WorldPoint(1839, 3691, 0));
		m.put(PortLocation.PORT_ROBERTS, new WorldPoint(1872, 3303, 0));
		m.put(PortLocation.DEEPFIN_POINT, new WorldPoint(1931, 2761, 0));
		m.put(PortLocation.LUNAR_ISLE, new WorldPoint(2139, 3884, 0));
		m.put(PortLocation.PORT_TYRAS, new WorldPoint(2146, 3123, 0));
		m.put(PortLocation.PRIFDDINAS, new WorldPoint(2163, 3326, 0));
		m.put(PortLocation.CORSAIR_COVE, new WorldPoint(2579, 2853, 0));
		m.put(PortLocation.ETCETERIA, new WorldPoint(2617, 3849, 0));
		m.put(PortLocation.RELLEKKA, new WorldPoint(2629, 3685, 0));
		m.put(PortLocation.VOID_KNIGHTS_OUTPOST, new WorldPoint(2659, 2672, 0));
		m.put(PortLocation.ARDOUGNE, new WorldPoint(2676, 3276, 0));
		m.put(PortLocation.PORT_KHAZARD, new WorldPoint(2678, 3162, 0));
		m.put(PortLocation.BRIMHAVEN, new WorldPoint(2764, 3227, 0));
		m.put(PortLocation.CATHERBY, new WorldPoint(2803, 3418, 0));
		m.put(PortLocation.RED_ROCK, new WorldPoint(2806, 2511, 0));
		m.put(PortLocation.MUSA_POINT, new WorldPoint(2940, 3144, 0));
		m.put(PortLocation.PORT_SARIM, new WorldPoint(3030, 3197, 0));
		m.put(PortLocation.PANDEMONIUM, new WorldPoint(3058, 2985, 0));
		m.put(PortLocation.RUINS_OF_UNKAH, new WorldPoint(3145, 2828, 0));
		m.put(PortLocation.SUMMER_SHORE, new WorldPoint(3183, 2368, 0));
		TILES = Collections.unmodifiableMap(m);
	}

	private NoticeBoardTiles()
	{
	}

	/** The board's tile, or null if the port has no notice board (or it isn't known). */
	public static WorldPoint of(PortLocation port)
	{
		return TILES.get(port);
	}
}
