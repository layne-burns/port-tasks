package com.nucleon.porttasks.routing;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/**
 * Counts the port bags received this session, by type (coin / reward) and size, ignoring the port.
 * A bag counts when it appears in the inventory within a few ticks of a courier task being completed, in
 * either order (within a tick the bag can arrive before the "last crate delivered" varbit), so withdrawing
 * bags from the bank isn't mistaken for a reward. Each gain is counted at most once. Client thread only.
 */
public final class BagCounter
{
	private static final int MATCH_TICKS = 3;

	private final CourierWikiData wiki;
	/** Current inventory count of each bag item id. */
	private final Map<Integer, Integer> held = new HashMap<>();
	/** "coin Large" -> bags received this session. */
	private final Map<String, Integer> received = new HashMap<>();
	private int completedTick = Integer.MIN_VALUE;
	/** Bag gains not yet matched to a completion, and the tick they happened. */
	private final Map<Integer, Integer> pendingGains = new HashMap<>();
	private int pendingTick = Integer.MIN_VALUE;

	public BagCounter(CourierWikiData wiki)
	{
		this.wiki = wiki;
	}

	/** A courier task was just completed (its last crate delivered). */
	public void onTaskCompleted(int tick)
	{
		completedTick = tick;
		if (tick - pendingTick <= MATCH_TICKS)
		{
			count(pendingGains);
		}
		pendingGains.clear();
	}

	/** The inventory changed; counts bag increases that follow a completion. */
	public void onInventoryChanged(ItemContainer inventory, int tick)
	{
		Map<Integer, Integer> now = new HashMap<>();
		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0 && wiki.bagItem(item.getId()) != null)
			{
				now.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		Map<Integer, Integer> gains = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : now.entrySet())
		{
			int gained = e.getValue() - held.getOrDefault(e.getKey(), 0);
			if (gained > 0)
			{
				gains.put(e.getKey(), gained);
			}
		}
		held.clear();
		held.putAll(now);
		if (gains.isEmpty())
		{
			return;
		}
		if (tick - completedTick <= MATCH_TICKS)
		{
			count(gains);
			completedTick = Integer.MIN_VALUE;
		}
		else
		{
			// Maybe the completion is reported later this tick; hold on to it briefly.
			pendingGains.clear();
			pendingGains.putAll(gains);
			pendingTick = tick;
		}
	}

	private void count(Map<Integer, Integer> gains)
	{
		for (Map.Entry<Integer, Integer> e : gains.entrySet())
		{
			CourierWikiData.BagItem bag = wiki.bagItem(e.getKey());
			received.merge(bag.type + " " + bag.size, e.getValue(), Integer::sum);
		}
	}

	/** Bags of this type ("coin" or "reward") and size received this session. */
	public int received(String type, BagSize size)
	{
		return received.getOrDefault(type + " " + size.wikiName(), 0);
	}

	public int total(String type)
	{
		int n = 0;
		for (BagSize s : BagSize.values())
		{
			n += received(type, s);
		}
		return n;
	}
}
