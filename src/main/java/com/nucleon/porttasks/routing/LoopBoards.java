package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import com.nucleon.porttasks.enums.PortLocation;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/**
 * Loop mode's memory of notice boards (SPEC-routing.md §2.4.2). Boards reroll together, reportedly after every
 * 8 completed port tasks and at the daily reset (00:00 UTC), and otherwise keep their offers minus what was
 * taken, so a board seen once in a reset cycle is known until the next reset. Kept in the profile, so it
 * survives logouts and hops within the cycle. Each board's tile is also remembered (where the player stood
 * when it was opened), for guiding the player back to it. Client thread only.
 */
@Slf4j
public final class LoopBoards implements LoopStatus.Memory
{
	private static final String KEY = "routingLoopBoards";

	/** What is stored in the profile. */
	private static final class Stored
	{
		/** UTC date of the reset cycle the offers belong to. */
		String date;
		/** Port name -> offered courier task dbrows. */
		Map<String, Set<Integer>> offers;
		/** Port name -> {x, y, plane} where the player stood at its board. */
		Map<String, int[]> tiles;
		/** The player boarded the boat this cycle, ending the gather. */
		boolean gatherDone;
	}

	private final ConfigManager configManager;
	private final String group;
	private final Gson gson;
	private LocalDate date;
	private final Map<PortLocation, Set<Integer>> offers = new EnumMap<>(PortLocation.class);
	private final Map<PortLocation, WorldPoint> tiles = new EnumMap<>(PortLocation.class);
	private boolean gatherDone;

	public LoopBoards(ConfigManager configManager, String group, Gson gson)
	{
		this.configManager = configManager;
		this.group = group;
		this.gson = gson;
		load();
	}

	private void load()
	{
		String json = configManager.getConfiguration(group, KEY);
		if (json == null)
		{
			date = today();
			return;
		}
		try
		{
			Stored s = gson.fromJson(json, Stored.class);
			date = s.date == null ? today() : LocalDate.parse(s.date);
			gatherDone = s.gatherDone;
			if (s.offers != null)
			{
				s.offers.forEach((name, rows) -> put(offers, name, new LinkedHashSet<>(rows)));
			}
			if (s.tiles != null)
			{
				s.tiles.forEach((name, t) ->
				{
					if (t != null && t.length == 3)
					{
						put(tiles, name, new WorldPoint(t[0], t[1], t[2]));
					}
				});
			}
		}
		catch (RuntimeException e)
		{
			log.debug("[loop] stored boards unreadable; starting empty", e);
			date = today();
		}
		checkDay();
	}

	private static <V> void put(Map<PortLocation, V> map, String name, V value)
	{
		for (PortLocation p : PortLocation.values())
		{
			if (p.getName().equals(name))
			{
				map.put(p, value);
			}
		}
	}

	private void save()
	{
		Stored s = new Stored();
		s.date = date.toString();
		s.gatherDone = gatherDone;
		s.offers = new HashMap<>();
		offers.forEach((p, rows) -> s.offers.put(p.getName(), rows));
		s.tiles = new HashMap<>();
		tiles.forEach((p, t) -> s.tiles.put(p.getName(), new int[]{t.getX(), t.getY(), t.getPlane()}));
		configManager.setConfiguration(group, KEY, gson.toJson(s));
	}

	private static LocalDate today()
	{
		return LocalDate.now(ZoneOffset.UTC);
	}

	/** Forgets the offers if the UTC day has turned since they were seen. True if it did. */
	public boolean checkDay()
	{
		LocalDate now = today();
		if (now.equals(date))
		{
			return false;
		}
		reset("daily reset");
		return true;
	}

	/** The boards rerolled: forget every board's offers (the tiles stay). */
	public void reset(String why)
	{
		log.debug("[loop] boards reset ({}): forgetting {} boards", why, offers.size());
		offers.clear();
		gatherDone = false;
		date = today();
		save();
	}

	/**
	 * The player boarded the boat: the gather is over for this reset cycle, whether or not every loop board
	 * was seen, and the next gather starts after the reset.
	 */
	public void endGather()
	{
		checkDay();
		if (!gatherDone)
		{
			gatherDone = true;
			save();
			log.debug("[loop] boarded: gather over until the boards reset");
		}
	}

	@Override
	public boolean gatherDone()
	{
		return gatherDone;
	}

	/**
	 * A board was opened (or changed while open, e.g. after taking a task): remember its courier offers and
	 * where the player stood. Returns the offers that weren't there before; outside a reset these should never
	 * appear, which is how the 8-task rule gets checked in play.
	 */
	public Set<Integer> opened(PortLocation board, Collection<Integer> courierDbrows, WorldPoint where)
	{
		checkDay();
		Set<Integer> before = offers.get(board);
		Set<Integer> now = new LinkedHashSet<>(courierDbrows);
		if (now.equals(before) && (where == null || where.equals(tiles.get(board))))
		{
			return Collections.emptySet(); // rescanned, nothing new: don't rewrite the profile
		}
		Set<Integer> added = new LinkedHashSet<>(now);
		if (before != null)
		{
			added.removeAll(before);
		}
		else
		{
			added.clear();
		}
		offers.put(board, now);
		if (where != null)
		{
			tiles.put(board, where);
		}
		save();
		return added;
	}

	/** True if this board has been seen in the current reset cycle. */
	@Override
	public boolean seen(PortLocation board)
	{
		return offers.containsKey(board);
	}

	/** The board's courier offers as last seen this cycle, or empty. */
	@Override
	public Set<Integer> offers(PortLocation board)
	{
		Set<Integer> s = offers.get(board);
		return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
	}

	/** Where the player stood at this board, or null if never seen. */
	public WorldPoint tile(PortLocation board)
	{
		return tiles.get(board);
	}
}
