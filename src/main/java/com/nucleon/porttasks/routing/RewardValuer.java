package com.nucleon.porttasks.routing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.game.ItemManager;

/**
 * Expected value of a courier task's bag (SPEC-routing.md §6), for an ironman: items count at their
 * high-alchemy value, read from the game's item definitions. A value the user gave in their wanted-items list
 * wins over everything; sawmill coupons count at the sawmill fee they save.
 *
 * The wiki lists what each bag can contain but not how likely each item is, so every listed drop is
 * weighted equally. Must be called on the client thread (item definitions).
 */
public final class RewardValuer
{
	// Sawmill fee per plank (wiki Sawmill page, 2026-09-25); a coupon saves exactly this.
	private static final Map<Integer, Integer> COUPON_VALUES = Map.of(
		32083, 100,    // Sawmill coupon (wood plank)
		32085, 250,    // Sawmill coupon (oak plank)
		34068, 500,    // Sawmill coupon (teak plank)
		34070, 1_500,  // Sawmill coupon (mahogany plank)
		34072, 2_500,  // Sawmill coupon (camphor plank)
		34074, 5_000   // Sawmill coupon (ironwood plank)
	);

	private final CourierWikiData data;
	private final ItemManager itemManager;
	private final WantedItems wanted;
	private final Map<Integer, Integer> alchCache = new HashMap<>();

	public RewardValuer(CourierWikiData data, ItemManager itemManager, WantedItems wanted)
	{
		this.data = data;
		this.itemManager = itemManager;
		this.wanted = wanted;
	}

	/** Value of one unit of an item, in coins. */
	public int unitValue(Integer itemId)
	{
		if (itemId == null)
		{
			return 1; // coins
		}
		Integer coupon = COUPON_VALUES.get(itemId);
		if (coupon != null)
		{
			return coupon;
		}
		return alchCache.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).getHaPrice());
	}

	/** Expected value of a reward bag of this size received at this destination port. */
	public double expectedRewardBag(BagSize size, String destination)
	{
		List<CourierWikiData.Drop> shared = data.sharedDrops(size);
		List<CourierWikiData.Drop> signature = data.signatureDrops(size, destination);
		int n = shared.size() + signature.size();
		if (n == 0)
		{
			return 0;
		}
		double total = 0;
		for (CourierWikiData.Drop d : shared)
		{
			total += dropValue(d);
		}
		for (CourierWikiData.Drop d : signature)
		{
			total += dropValue(d);
		}
		return total / n;
	}

	/** Expected value of the one bag a courier task gives: coin bag 4/5, reward bag 1/5. */
	public double expectedTaskValue(int baseXp, String destination)
	{
		BagSize size = BagSize.forXp(baseXp);
		return BagSize.COIN_BAG_CHANCE * size.expectedCoins()
			+ (1 - BagSize.COIN_BAG_CHANCE) * expectedRewardBag(size, destination);
	}

	private double dropValue(CourierWikiData.Drop d)
	{
		Integer own = wanted.valueOf(d.name);
		return (own != null ? own : unitValue(d.id)) * (d.min + d.max) / 2.0;
	}
}
