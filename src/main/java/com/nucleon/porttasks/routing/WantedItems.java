package com.nucleon.porttasks.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The user's wanted items (SPEC-routing.md §2.3): one item per line, optionally "= value" in coins,
 * e.g. "Crystal shard = 2000". Tasks that can give a listed item are highlighted; a value, if given,
 * replaces the item's alch value in the value metrics. Names match drop names case-insensitively.
 */
public final class WantedItems
{
	/** Lower-case item name -> value override, or null if listed without a value. */
	private volatile Map<String, Integer> items = Collections.emptyMap();

	public void parse(String text)
	{
		Map<String, Integer> parsed = new LinkedHashMap<>();
		if (text != null)
		{
			for (String line : text.split("\\R"))
			{
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#"))
				{
					continue;
				}
				String name = trimmed;
				Integer value = null;
				int eq = trimmed.indexOf('=');
				if (eq >= 0)
				{
					name = trimmed.substring(0, eq).trim();
					try
					{
						value = Integer.parseInt(trimmed.substring(eq + 1).trim().replace(",", ""));
					}
					catch (NumberFormatException e)
					{
						value = null;
					}
				}
				if (!name.isEmpty())
				{
					parsed.put(name.toLowerCase(Locale.ROOT), value);
				}
			}
		}
		items = parsed;
	}

	/** The user's value for an item, or null if not listed with a value. */
	public Integer valueOf(String itemName)
	{
		return itemName == null ? null : items.get(itemName.toLowerCase(Locale.ROOT));
	}

	/** Wanted items that a bag of this size, received at this port, can contain. */
	public List<String> matches(CourierWikiData wiki, BagSize size, String destination)
	{
		if (items.isEmpty())
		{
			return Collections.emptyList();
		}
		List<String> found = new ArrayList<>();
		for (CourierWikiData.Drop d : wiki.sharedDrops(size))
		{
			addIfWanted(found, d.name);
		}
		for (CourierWikiData.Drop d : wiki.signatureDrops(size, destination))
		{
			addIfWanted(found, d.name);
		}
		return found;
	}

	private void addIfWanted(List<String> found, String name)
	{
		if (name != null && items.containsKey(name.toLowerCase(Locale.ROOT)) && !found.contains(name))
		{
			found.add(name);
		}
	}
}
