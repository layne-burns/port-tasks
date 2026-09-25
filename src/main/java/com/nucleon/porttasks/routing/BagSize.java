package com.nucleon.porttasks.routing;

/**
 * Port bag sizes. The size of the bag a courier task gives depends only on its base XP
 * (wiki Module:CourierTaskLine, 27 August 2026). Coin ranges are from the wiki's Port coin bag page.
 */
public enum BagSize
{
	TINY("Tiny", 400, 800, 1_200),
	SMALL("Small", 1_000, 1_613, 2_714),
	MEDIUM("Medium", 2_500, 2_486, 3_595),
	LARGE("Large", 6_000, 3_864, 5_350),
	HUGE("Huge", Integer.MAX_VALUE, 6_400, 9_600);

	/** Chance a courier task gives a coin bag; otherwise it gives a reward bag. */
	public static final double COIN_BAG_CHANCE = 0.8;

	private final String wikiName;
	/** XP below which a task gets this size (exclusive upper bound). */
	private final int xpBelow;
	private final int coinsMin;
	private final int coinsMax;

	BagSize(String wikiName, int xpBelow, int coinsMin, int coinsMax)
	{
		this.wikiName = wikiName;
		this.xpBelow = xpBelow;
		this.coinsMin = coinsMin;
		this.coinsMax = coinsMax;
	}

	public static BagSize forXp(int baseXp)
	{
		for (BagSize s : values())
		{
			if (baseXp < s.xpBelow)
			{
				return s;
			}
		}
		return HUGE;
	}

	public String wikiName()
	{
		return wikiName;
	}

	/** Expected coins in a coin bag of this size (midpoint of the wiki's range). */
	public double expectedCoins()
	{
		return (coinsMin + coinsMax) / 2.0;
	}
}
