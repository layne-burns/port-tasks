package com.nucleon.porttasks.routing;

/**
 * The Bounty AFK monster setting (SPEC-routing.md §2.5.3): Auto (whichever bounty monster is examined) or one
 * monster. Names are the wiki's, as in bounty_tasks.json.
 */
public enum AfkMonster
{
	AUTO("Auto (examined)", null),
	ALBATROSS("Albatross"),
	ARMOURED_KRAKEN("Armoured kraken"),
	BULL_SHARK("Bull shark"),
	BUTTERFLY_RAY("Butterfly ray"),
	EAGLE_RAY("Eagle ray"),
	FRIGATEBIRD("Frigatebird"),
	GREAT_WHITE_SHARK("Great white shark"),
	HAMMERHEAD_SHARK("Hammerhead shark"),
	MANTA_RAY("Manta ray", "Manta ray (monster)"),
	MOGRE("Mogre", "Mogre (sea)"),
	NARWHAL("Narwhal"),
	ORCA("Orca"),
	OSPREY("Osprey"),
	PYGMY_KRAKEN("Pygmy kraken"),
	SPINED_KRAKEN("Spined kraken"),
	STINGRAY("Stingray"),
	TERN("Tern"),
	TIGER_SHARK("Tiger shark"),
	VAMPYRE_KRAKEN("Vampyre kraken"),
	VEILED_KRAKEN("Veiled kraken");

	private final String label;
	/** The wiki's monster name, or null for Auto. */
	private final String monster;

	AfkMonster(String name)
	{
		this(name, name);
	}

	AfkMonster(String label, String monster)
	{
		this.label = label;
		this.monster = monster;
	}

	public String monster()
	{
		return monster;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
