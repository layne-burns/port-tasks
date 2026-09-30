package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/**
 * Safespots the player saved at sea ("Save safespot here", SPEC-routing.md §2.5.2): per monster, the tiles the
 * boat was on. The wiki maps only a couple of bounty safespots, so the rest are recorded in play. Kept in the
 * profile. Client thread only.
 */
@Slf4j
public final class SavedSafespots
{
	private static final String KEY = "routingSafespots";
	//CHECKSTYLE:OFF
	private static final Type TYPE = new TypeToken<Map<String, List<int[]>>>() {}.getType();
	//CHECKSTYLE:ON

	private final ConfigManager configManager;
	private final String group;
	private final Gson gson;
	private final Map<String, List<int[]>> spots = new HashMap<>();

	public SavedSafespots(ConfigManager configManager, String group, Gson gson)
	{
		this.configManager = configManager;
		this.group = group;
		this.gson = gson;
		String json = configManager.getConfiguration(group, KEY);
		if (json != null)
		{
			try
			{
				Map<String, List<int[]>> m = gson.fromJson(json, TYPE);
				if (m != null)
				{
					spots.putAll(m);
				}
			}
			catch (RuntimeException e)
			{
				log.debug("saved safespots unreadable; starting empty", e);
			}
		}
	}

	/** Saves the boat's tile and heading ({@code orientation} -1 if unknown: marked as a single square). */
	public void add(String monster, WorldPoint tile, int orientation)
	{
		spots.computeIfAbsent(monster, k -> new ArrayList<>()).add(orientation < 0
			? new int[]{tile.getX(), tile.getY()} : new int[]{tile.getX(), tile.getY(), orientation});
		save();
	}

	/** Forgets the monster's saved safespots. */
	public void clear(String monster)
	{
		if (spots.remove(monster) != null)
		{
			save();
		}
	}

	public int count(String monster)
	{
		return spots.getOrDefault(monster, Collections.emptyList()).size();
	}

	/** The saved safespots as targets, by monster. */
	public Map<String, List<BountySpawns.Area>> areas()
	{
		Map<String, List<BountySpawns.Area>> out = new HashMap<>();
		spots.forEach((monster, tiles) ->
		{
			List<BountySpawns.Area> areas = new ArrayList<>();
			for (int[] t : tiles)
			{
				// Saved with the boat's heading: a "park here" line; saved before headings were kept: one square.
				areas.add(t.length >= 3 ? BountySpawns.parkLine(monster, t[0], t[1], t[2])
					: BountySpawns.safespot(monster, "saved", t[0], t[1], 1, 1));
			}
			out.put(monster, areas);
		});
		return out;
	}

	private void save()
	{
		configManager.setConfiguration(group, KEY, gson.toJson(spots, TYPE));
	}
}
