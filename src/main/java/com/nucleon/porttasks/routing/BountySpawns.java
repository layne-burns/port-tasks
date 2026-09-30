package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import com.nucleon.porttasks.enums.PortLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Where bounty monsters spawn at sea, from the OSRS Wiki's monster pages (scripts/update_bounty_spawns.py), and
 * the choice of which area to sail to next (SPEC-routing.md §2.5.1). Immutable after loading.
 */
@Slf4j
public final class BountySpawns
{
	/** One sea area a monster spawns in. */
	public static final class Area
	{
		public final String monster;
		public final String location;
		public final List<WorldPoint> points;
		/** The spawn tile nearest the area's centre: the point to sail to (a real spawn, so at sea). */
		public final WorldPoint target;
		/** For a safespot: its tiles as a rectangle {x, y, width, height} (south-west corner), else null. */
		public final int[] safespot;

		Area(String monster, String location, List<WorldPoint> points)
		{
			this(monster, location, points, null);
		}

		Area(String monster, String location, List<WorldPoint> points, int[] safespot)
		{
			this.monster = monster;
			this.location = location;
			this.safespot = safespot;
			this.points = Collections.unmodifiableList(points);
			double cx = points.stream().mapToInt(WorldPoint::getX).average().orElse(0);
			double cy = points.stream().mapToInt(WorldPoint::getY).average().orElse(0);
			WorldPoint best = points.get(0);
			for (WorldPoint p : points)
			{
				if (Math.hypot(p.getX() - cx, p.getY() - cy) < Math.hypot(best.getX() - cx, best.getY() - cy))
				{
					best = p;
				}
			}
			target = best;
		}

		/** Straight-line tiles from a point to the area's nearest spawn. */
		double straight(WorldPoint from)
		{
			double best = Double.POSITIVE_INFINITY;
			for (WorldPoint p : points)
			{
				best = Math.min(best, Math.hypot(p.getX() - from.getX(), p.getY() - from.getY()));
			}
			return best;
		}
	}

	private static final class AreaRow
	{
		String location;
		List<int[]> points;
	}

	private static final class SafespotRow
	{
		int x;
		int y;
		int w;
		int h;
		String caption;
	}

	private static final class SpawnsFile
	{
		Map<String, List<AreaRow>> spawns;
		Map<String, List<SafespotRow>> safespots;
		Map<String, List<String>> safespotNotes;
	}

	private final Map<String, List<Area>> byMonster;
	private final Map<String, List<Area>> safespots;
	private final Map<String, List<String>> notes;

	BountySpawns(Map<String, List<Area>> byMonster)
	{
		this(byMonster, Collections.emptyMap(), Collections.emptyMap());
	}

	BountySpawns(Map<String, List<Area>> byMonster, Map<String, List<Area>> safespots, Map<String, List<String>> notes)
	{
		this.byMonster = byMonster;
		this.safespots = safespots;
		this.notes = notes;
	}

	/**
	 * A safespot as a target: the rectangle's middle tile to sail to. The wiki's map rectangles are given by their
	 * centre and size; a saved one is the single tile the boat was on.
	 */
	static Area safespot(String monster, String caption, int cx, int cy, int w, int h)
	{
		WorldPoint centre = new WorldPoint(cx, cy, 0);
		return new Area(monster, "Safespot" + (caption == null || caption.isEmpty() ? "" : ": " + caption),
			Collections.singletonList(centre), new int[]{cx - w / 2, cy - h / 2, Math.max(1, w), Math.max(1, h)});
	}

	/** The wiki's safespots for a monster (with a map); usually none. */
	public List<Area> safespots(String monster)
	{
		return safespots.getOrDefault(monster, Collections.emptyList());
	}

	/** The wiki's words about safespots for a monster, for those whose spot has no map; usually none. */
	public List<String> safespotNotes(String monster)
	{
		return notes.getOrDefault(monster, Collections.emptyList());
	}

	/** Loads the bundled data; on failure returns none and logs, so sea guidance degrades instead of breaking. */
	public static BountySpawns load(Gson gson)
	{
		try (InputStream in = BountySpawns.class.getResourceAsStream("/com/nucleon/porttasks/bounty_spawns.json"))
		{
			if (in == null)
			{
				throw new IOException("missing resource bounty_spawns.json");
			}
			SpawnsFile f;
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				f = gson.fromJson(reader, SpawnsFile.class);
			}
			Map<String, List<Area>> byMonster = new HashMap<>();
			f.spawns.forEach((monster, rows) ->
			{
				List<Area> areas = new ArrayList<>();
				for (AreaRow r : rows)
				{
					List<WorldPoint> points = new ArrayList<>();
					for (int[] xy : r.points)
					{
						points.add(new WorldPoint(xy[0], xy[1], 0));
					}
					if (!points.isEmpty())
					{
						areas.add(new Area(monster, r.location, points));
					}
				}
				byMonster.put(monster, Collections.unmodifiableList(areas));
			});
			Map<String, List<Area>> safe = new HashMap<>();
			if (f.safespots != null)
			{
				f.safespots.forEach((monster, rows) ->
				{
					List<Area> spots = new ArrayList<>();
					for (SafespotRow r : rows)
					{
						spots.add(safespot(monster, r.caption, r.x, r.y, r.w, r.h));
					}
					safe.put(monster, Collections.unmodifiableList(spots));
				});
			}
			return new BountySpawns(byMonster, safe, f.safespotNotes == null ? Collections.emptyMap() : f.safespotNotes);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load bounty spawn data", e);
			return new BountySpawns(Collections.emptyMap());
		}
	}

	public List<Area> areas(String monster)
	{
		return byMonster.getOrDefault(monster, Collections.emptyList());
	}

	/**
	 * The area, among these monsters' spawns, that is estimated quickest to sail to from {@code boat}. The sea
	 * has no pathfinder here, so the estimate goes through the port graph: from the port nearest the boat (a
	 * straight hop), along the graph to some port p, then a straight hop from p to the area:
	 *
	 *   est(A) = |boat - p0| + min over p of ( d(p0, p) + |p - A| )
	 *
	 * where p0 is the port nearest the boat and |x - A| is the straight distance to A's nearest spawn. A plain
	 * straight line would happily cross land (the Gulf of Kourend looks close to Tirannwn from the wrong side).
	 * A monster with a safespot (the wiki's, or one the player saved: {@code saved}) is only looked for at its
	 * safespots, not across its spawn areas. Null if none of the monsters has a known spawn.
	 */
	public Area nearest(Collection<String> monsters, Map<String, List<Area>> saved, WorldPoint boat, Collection<PortLocation> ports,
		ToDoubleBiFunction<PortLocation, PortLocation> d)
	{
		PortLocation p0 = nearestPort(boat, ports);
		if (p0 == null)
		{
			return null;
		}
		double toP0 = straight(boat, p0.getNavigationLocation());
		Area best = null;
		double bestCost = Double.POSITIVE_INFINITY;
		for (String m : monsters)
		{
			List<Area> spots = new ArrayList<>(safespots(m));
			spots.addAll(saved.getOrDefault(m, Collections.emptyList()));
			for (Area a : spots.isEmpty() ? areas(m) : spots)
			{
				double cost = toP0 + a.straight(p0.getNavigationLocation());
				for (PortLocation p : ports)
				{
					double viaP = d.applyAsDouble(p0, p);
					if (Double.isFinite(viaP))
					{
						cost = Math.min(cost, toP0 + viaP + a.straight(p.getNavigationLocation()));
					}
				}
				if (cost < bestCost)
				{
					bestCost = cost;
					best = a;
				}
			}
		}
		return best;
	}

	/** The port whose dock is nearest the point in a straight line, or null if none. */
	public static PortLocation nearestPort(WorldPoint from, Collection<PortLocation> ports)
	{
		PortLocation best = null;
		for (PortLocation p : ports)
		{
			if (p != PortLocation.EMPTY && (best == null
				|| straight(from, p.getNavigationLocation()) < straight(from, best.getNavigationLocation())))
			{
				best = p;
			}
		}
		return best;
	}

	private static double straight(WorldPoint a, WorldPoint b)
	{
		return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
	}
}
