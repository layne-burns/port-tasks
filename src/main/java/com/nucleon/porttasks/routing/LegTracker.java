package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import net.runelite.api.coords.WorldPoint;

/**
 * Measures the leg being sailed, from leaving one port's dock to docking at the next: the tiles the boat
 * covers (sum of the straight-line moves between game ticks, the same measure as Port Tasks' drawn paths)
 * and the ticks it takes.
 *
 * The start is event-driven (the boat's move mode leaving "docked"). While a leg is under way the plugin
 * feeds one boat position per game tick; there is no event for the boat moving, and one subtraction per
 * tick is all it costs. The leg is dropped if the boat jumps (a teleport), the player leaves the boat, or
 * they log out or hop. Client thread only.
 */
public final class LegTracker
{
	/** A finished leg. Immutable. */
	public static final class Leg
	{
		public final PortLocation from;
		public final PortLocation to;
		public final double tiles;
		public final int ticks;

		Leg(PortLocation from, PortLocation to, double tiles, int ticks)
		{
			this.from = from;
			this.to = to;
			this.tiles = tiles;
			this.ticks = ticks;
		}
	}

	/** More than this in one tick is a teleport, not sailing (boats cover a few tiles per tick). */
	static final double MAX_TILES_PER_TICK = 20;

	private PortLocation from;
	private int startTick;
	private double tiles;
	private WorldPoint last;

	public boolean active()
	{
		return from != null;
	}

	/** The boat left this port's dock. */
	public void start(PortLocation from, int tick)
	{
		this.from = from;
		startTick = tick;
		tiles = 0;
		last = null;
	}

	/** The boat's position this tick, or null if the player isn't on it (which drops the leg). */
	public void sample(WorldPoint boat)
	{
		if (!active())
		{
			return;
		}
		if (boat == null)
		{
			cancel();
			return;
		}
		if (last != null)
		{
			double d = Math.hypot(boat.getX() - last.getX(), boat.getY() - last.getY());
			if (d > MAX_TILES_PER_TICK || boat.getPlane() != last.getPlane())
			{
				cancel();
				return;
			}
			tiles += d;
		}
		last = boat;
	}

	/**
	 * The boat docked at this port (null for a mooring point or unknown dock). Returns the leg, or null if it
	 * can't be used (no port, or back where it started); either way tracking stops.
	 */
	public Leg finish(PortLocation to, int tick)
	{
		Leg leg = to == null || to == from ? null : new Leg(from, to, tiles, tick - startTick);
		cancel();
		return leg;
	}

	public void cancel()
	{
		from = null;
		last = null;
		tiles = 0;
	}

	/** The port the current leg started from, or null if none is under way. */
	public PortLocation from()
	{
		return from;
	}

	public double tiles()
	{
		return tiles;
	}

	public int ticks(int now)
	{
		return now - startTick;
	}
}
