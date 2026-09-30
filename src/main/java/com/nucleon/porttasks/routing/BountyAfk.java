package com.nucleon.porttasks.routing;

/**
 * Bounty AFK (SPEC-routing.md §2.5.3): the blackout-while-planted mode. Pure state; the plugin feeds it game
 * events and it reports through a {@link Sink}: chat lines that Watchdog alerts match (show or dismiss the
 * blackout, flash the screen), and start/stop for AnkiScape's Bounty mode. Client thread only.
 *
 *   OFF     -the boat hits a held bounty's monster (auto), or examine any monster->   WAITING (armed on it)
 *   WAITING -3 corpses wait, or the oldest is ~20 s from despawning->   DOWN (blackout down, flash: loot)
 *   DOWN    -all looted, then the boat hits one->   WAITING  (blackout back)
 *   any     -examine it again / its bounty's parts are in / docked or off the boat
 *            / moved and no attack within the grace->   OFF (blackout gone, flash)
 *
 * The Anki session starts the first time the mode is armed with the boat parked ("planted and firing"),
 * survives moving (repositioning, banking), and stops when the mode goes off.
 */
public final class BountyAfk
{
	public enum State
	{
		OFF, WAITING, DOWN
	}

	/** Where the mode's effects go. */
	public interface Sink
	{
		/** A chat line for Watchdog ("[Bounty AFK] ..."). */
		void chat(String line);

		/** AnkiScape's Bounty mode: "start" or "stop". */
		void anki(String action, String monster);
	}

	public static final String PREFIX = "[Bounty AFK] ";

	private final Sink sink;
	private State state = State.OFF;
	private String monster;
	private boolean sessionStarted;
	/** Tick by which the boat, having moved, must attack again; -1 for none. */
	private int graceUntil = -1;
	/** Corpses of the armed monster waiting to be looted: key (NPC index) -> tick it appeared. */
	private final java.util.Map<Integer, Integer> corpses = new java.util.LinkedHashMap<>();
	// Loot alert: "down" once this many corpses wait, or the oldest is within warnTicks of despawning.
	private int lootCount = 3;
	private int lifeTicks = 300;
	private int warnTicks = 33;

	public BountyAfk(Sink sink)
	{
		this.sink = sink;
	}

	/**
	 * When the loot alert ("down") goes: once {@code count} corpses wait, or the oldest is within
	 * {@code warnTicks} of its {@code lifeTicks} despawn. So the player loots in batches without losing any.
	 */
	public void lootAlert(int count, int lifeTicks, int warnTicks)
	{
		this.lootCount = Math.max(1, count);
		this.lifeTicks = lifeTicks;
		this.warnTicks = warnTicks;
	}

	/** True while something needs checking each tick: a grace deadline, or corpses waiting for the loot alert. */
	public boolean needsTick()
	{
		return graceUntil >= 0 || state == State.WAITING && !corpses.isEmpty();
	}

	/** Corpses of the armed monster waiting to be looted. */
	public int corpsesWaiting()
	{
		return corpses.size();
	}

	public State state()
	{
		return state;
	}

	/** The monster the mode is armed on, or null when off. */
	public String monster()
	{
		return monster;
	}

	/** True while a grace deadline is pending (the plugin checks it each tick only then). */
	public boolean graceRunning()
	{
		return graceUntil >= 0;
	}

	/**
	 * The player examined a sea monster (the manual switch; any monster): arms on it, or, if already armed on
	 * it, turns off. {@code parked} if the boat is stopped at sea. Returns true if this turned the mode off.
	 */
	public boolean examine(String examined, boolean parked)
	{
		if (state != State.OFF && examined.equals(monster))
		{
			off("examined again");
			return true;
		}
		if (state != State.OFF)
		{
			off("switched to " + examined);
		}
		arm(examined, parked);
		return false;
	}

	/** Auto mode: the boat hit a monster of a held bounty; arms on it unless the mode is already on. */
	public void autoArm(String hit, boolean parked)
	{
		if (state == State.OFF)
		{
			arm(hit, parked);
		}
	}

	private void arm(String examined, boolean parked)
	{
		monster = examined;
		state = State.WAITING;
		graceUntil = -1;
		sink.chat(PREFIX + "on: " + monster);
		if (parked)
		{
			startSession();
		}
	}

	/** The boat came to a stop at sea. */
	public void parked()
	{
		if (state != State.OFF)
		{
			startSession();
		}
	}

	/** The boat started moving: it has {@code graceTicks} to attack the monster again. */
	public void moving(int tick, int graceTicks)
	{
		if (state != State.OFF && graceUntil < 0)
		{
			graceUntil = tick + graceTicks;
		}
	}

	/** The boat (the player or the crew) hit one of the armed monster. */
	public void attacked(String target)
	{
		if (state == State.OFF || !target.equals(monster))
		{
			return;
		}
		graceUntil = -1;
		// Back to fighting only once the batch has been looted; hits while corpses still wait change nothing.
		if (state == State.DOWN && corpses.isEmpty())
		{
			state = State.WAITING;
			sink.chat(PREFIX + monster + " fighting");
		}
	}

	/** A corpse of this monster appeared near the boat ({@code key}: its NPC index). */
	public void corpse(String of, int key, int tick)
	{
		if (state == State.OFF || !of.equals(monster))
		{
			return;
		}
		corpses.putIfAbsent(key, tick);
		checkLoot(tick);
	}

	/** A corpse was looted or despawned. */
	public void corpseGone(int key)
	{
		corpses.remove(key);
	}

	/** Each tick while {@link #needsTick}: the grace deadline and the loot alert's despawn warning. */
	public void tick(int tick)
	{
		if (graceUntil >= 0 && tick >= graceUntil)
		{
			off("no attack after moving");
			return;
		}
		checkLoot(tick);
	}

	/** "down" (loot now) once enough corpses wait, or the oldest is about to despawn. */
	private void checkLoot(int tick)
	{
		if (state != State.WAITING || corpses.isEmpty())
		{
			return;
		}
		int oldest = corpses.values().iterator().next();
		if (corpses.size() >= lootCount || tick >= oldest + lifeTicks - warnTicks)
		{
			state = State.DOWN;
			// Ends in "down", which the Watchdog alert matches; the count goes before it.
			sink.chat(PREFIX + corpses.size() + " to loot: " + monster + " down");
		}
	}

	/** Turns the mode off (docked, off the boat, parts in, ...), if on. */
	public void off(String reason)
	{
		if (state == State.OFF)
		{
			return;
		}
		String was = monster;
		state = State.OFF;
		graceUntil = -1;
		monster = null;
		corpses.clear();
		sink.chat(PREFIX + "off: " + reason);
		if (sessionStarted)
		{
			sessionStarted = false;
			sink.anki("stop", was);
		}
	}

	private void startSession()
	{
		if (!sessionStarted)
		{
			sessionStarted = true;
			sink.anki("start", monster);
		}
	}
}
